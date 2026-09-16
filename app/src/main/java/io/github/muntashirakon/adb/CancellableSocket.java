// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.adb;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

/** Owns the raw socket BEFORE connect/TLS; cancellation never waits for a worker-held monitor. */
public final class CancellableSocket implements Closeable {
    private final Socket raw;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    // Only the worker touches TLS state. The cancelling thread closes the raw transport only.
    private SSLSocket tls;

    public CancellableSocket() { this(new Socket()); }
    CancellableSocket(Socket socket) { raw = socket; }

    public void connect(String host, int port, Deadline deadline) throws IOException {
        checkOpen();
        raw.connect(new InetSocketAddress(host, port), deadline.remainingMillis());
        checkOpen();
        raw.setTcpNoDelay(true);
        prepareRead(deadline);
    }

    public SSLSocket startTls(SSLContext context, String host, int port, Deadline deadline)
            throws IOException {
        checkOpen();
        tls = (SSLSocket) context.getSocketFactory().createSocket(raw, host, port, true);
        prepareRead(deadline);
        checkOpen();
        tls.startHandshake();
        checkOpen();
        return tls;
    }

    public Socket activeSocket() throws IOException {
        checkOpen();
        return tls == null ? raw : tls;
    }

    public void prepareRead(Deadline deadline) throws IOException {
        checkOpen();
        int remaining = deadline.remainingMillis();
        raw.setSoTimeout(remaining);
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
