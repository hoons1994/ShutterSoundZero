// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.adb;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLParameters;

/**
 * Owns the raw socket BEFORE connect/TLS; cancellation never waits for a worker-held monitor.
 * This adapter is exclusively for on-device ADB, not arbitrary TLS endpoints.
 */
public final class CancellableSocket implements Closeable {
    private final Socket raw;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    // Only the worker touches TLS state. The cancelling thread closes the raw transport only.
    private SSLSocket handshakingTls;
    private SSLSocket tls;

    public CancellableSocket() { this(new Socket()); }
    CancellableSocket(Socket socket) { raw = socket; }

    public void connect(String host, int port, Deadline deadline) throws IOException {
        checkOpen();
        if (port < 1 || port > 65535) throw new IOException("Invalid local ADB port");
        // Reject names before resolution: no DNS, rebinding or network-interface-change race.
        InetAddress address = loopbackAddress(host);
        raw.connect(new InetSocketAddress(address, port), deadline.remainingMillis());
        checkOpen();
        raw.setTcpNoDelay(true);
        prepareRead(deadline);
    }

    public SSLSocket startTls(LocalAdbTls context, String host, int port, Deadline deadline)
            throws IOException {
        checkOpen();
        requireConnectedEndpoint(host, port);
        // TLS is provisional until PAKE or the Binder shell-identity proof completes.
        // This policy is deliberately distinct from HTTPS endpoint identification.
        SSLSocket socket = (SSLSocket) context.context().getSocketFactory()
                .createSocket(raw, host, port, true);
        handshakingTls = socket;
        socket.setUseClientMode(true);
        socket.setEnabledProtocols(new String[]{"TLSv1.3"});
        SSLParameters parameters = socket.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm(context.endpointAlgorithm());
        socket.setSSLParameters(parameters);
        prepareRead(deadline);
        checkOpen();
        socket.startHandshake();
        checkOpen();
        // Publish only the socket that completed the explicit endpoint-identification policy.
        // Keeping an earlier alias in tls lets static and human review miss that invariant.
        tls = socket;
        handshakingTls = null;
        return socket;
    }

    private static InetAddress loopbackAddress(String host) throws IOException {
        if ("127.0.0.1".equals(host)) return InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        if ("::1".equals(host)) {
            byte[] address = new byte[16];
            address[15] = 1;
            return InetAddress.getByAddress(address);
        }
        throw new IOException("Only literal loopback ADB endpoints are permitted");
    }

    private void requireConnectedEndpoint(String host, int port) throws IOException {
        InetAddress expected = loopbackAddress(host);
        if (!raw.isConnected() || !expected.equals(raw.getInetAddress()) || raw.getPort() != port) {
            throw new IOException("TLS endpoint does not match the connected local ADB socket");
        }
    }

    public Socket activeSocket() throws IOException {
        checkOpen();
        return tls == null ? raw : tls;
    }

    public void prepareRead(Deadline deadline) throws IOException {
        checkOpen();
        int remaining = deadline.remainingMillis();
        raw.setSoTimeout(remaining);
        if (handshakingTls != null) handshakingTls.setSoTimeout(remaining);
        if (tls != null) tls.setSoTimeout(remaining);
    }

    public void checkOpen() throws IOException {
        if (cancelled.get() || raw.isClosed() || Thread.currentThread().isInterrupted()) {
            throw new IOException("ADB transport was cancelled");
        }
    }

    public boolean isClosed() { return cancelled.get() || raw.isClosed(); }

    /** Safe from any thread, including before connect, during TLS, and during a blocking read/write. */
    @Override public void close() {
        cancelled.set(true);
        try { raw.close(); } catch (IOException ignored) { }
    }

    /** Worker-only TLS cleanup, after raw close has unblocked I/O. Never destroys shared credentials. */
    public void dispose() {
        close();
        if (handshakingTls != null) {
            try { handshakingTls.close(); } catch (IOException ignored) { }
            handshakingTls = null;
        }
        if (tls != null) {
            try { tls.close(); } catch (IOException ignored) { }
            tls = null;
        }
    }

    public static final class Deadline {
        private final long expires;
        public Deadline(long timeoutMillis) {
            if (timeoutMillis <= 0 || timeoutMillis > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid operation timeout");
            }
            expires = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        }
        public int remainingMillis() throws SocketTimeoutException {
            long remaining = expires - System.nanoTime();
            if (remaining <= 0) throw new SocketTimeoutException("ADB operation deadline exceeded");
            return (int) Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remaining));
        }
    }
}
