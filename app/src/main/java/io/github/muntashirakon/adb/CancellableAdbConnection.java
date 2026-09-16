// SPDX-License-Identifier: GPL-3.0-or-later
// Connection flow adapted from libadb-android 3.1.1 AdbConnection (Muntashir Al-Islam).
// Reuses libadb's packet codec, RSA authentication and TLS context; does not fork cryptography.
package io.github.muntashirakon.adb;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPublicKey;

/**
 * App-specific, sequential ADB session. The app serializes all commands, so a permanent reader
 * thread and multiplexed stream queues are unnecessary. Every I/O runs inside an owned worker.
 * The instance exists before TCP connect and can be cancelled during connect, TLS, OPEN or a read.
 */
public final class CancellableAdbConnection implements Closeable {
    @FunctionalInterface public interface PayloadHandler {
        /** Return true when the command's completion marker has been received. */
        boolean onPayload(byte[] data) throws IOException;
    }
    private final Object lifecycle = new Object();
    private final CancellableSocket transport = new CancellableSocket();
    private final String host;
    private final int port;
    private final int api;
    private final KeyPair keys;
    private final String deviceName;
    private InputStream input;
    private OutputStream output;
    private int protocolVersion;
    private int maxData;
    private int nextStreamId;
    private volatile boolean connected;
    private boolean busy;
    private boolean closed;
    private boolean disposed;

    public CancellableAdbConnection(String host, int port, PrivateKey privateKey,
            Certificate certificate, String deviceName, int api) {
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid ADB port");
        this.host = host;
        this.port = port;
        this.keys = new KeyPair(privateKey, certificate);
        this.deviceName = deviceName;
        this.api = api;
        protocolVersion = AdbProtocol.getProtocolVersion(api);
        maxData = AdbProtocol.getMaxData(api);
    }

    public boolean isConnected() { return connected && !transport.isClosed(); }

    public void connect(long timeoutMillis) throws Exception {
        begin();
        boolean success = false;
        try {
            if (connected) throw new IOException("Already connected");
            CancellableSocket.Deadline deadline = new CancellableSocket.Deadline(timeoutMillis);
            transport.connect(host, port, deadline);
            updateStreams();
            write(AdbProtocol.generateConnect(api), deadline);
            boolean tls = false;
            boolean signed = false;
            while (true) {
                AdbProtocol.Message message = read(deadline);
                switch (message.command) {
                    case AdbProtocol.A_STLS:
                        if (tls) throw new IOException("Repeated TLS upgrade");
                        write(AdbProtocol.generateStls(), deadline);
                        transport.startTls(SslUtils.getSslContext(keys), host, port, deadline);
                        updateStreams();
                        tls = true;
                        break;
                    case AdbProtocol.A_AUTH:
                        if (tls || message.arg0 != AdbProtocol.ADB_AUTH_TOKEN || message.payload == null) {
                            throw new IOException("Unexpected ADB authentication packet");
                        }
                        if (signed) {
                            write(AdbProtocol.generateAuth(AdbProtocol.ADB_AUTH_RSAPUBLICKEY,
                                    AndroidPubkey.encodeWithName((RSAPublicKey) keys.getPublicKey(), deviceName)), deadline);
                        } else {
                            write(AdbProtocol.generateAuth(AdbProtocol.ADB_AUTH_SIGNATURE,
                                    AndroidPubkey.adbAuthSign(keys.getPrivateKey(), message.payload)), deadline);
                            signed = true;
                        }
                        break;
                    case AdbProtocol.A_CNXN:
                        if (message.arg0 < AdbProtocol.A_VERSION_MIN || message.arg1 <= 0) {
                            throw new IOException("Invalid ADB connection parameters");
                        }
                        protocolVersion = Math.min(message.arg0, AdbProtocol.getProtocolVersion(api));
                        maxData = Math.min(message.arg1, AdbProtocol.getMaxData(api));
                        transport.checkOpen();
                        deadline.remainingMillis();
                        connected = true;
                        success = true;
                        return;
                    default:
                        throw new IOException("Unexpected packet before ADB connection");
                }
            }
        } finally {
            if (!success) cancel();
            end();
        }
    }

    /** Reads one shell stream; late packets from a previously closed stream cannot satisfy this one. */
    public void readShell(String command, long timeoutMillis, PayloadHandler handler) throws IOException {
        begin();
        boolean success = false;
        try {
            if (!isConnected()) throw new IOException("Not connected to ADB");
            if (nextStreamId == Integer.MAX_VALUE) throw new IOException("ADB stream IDs exhausted");
            int localId = ++nextStreamId;
            int remoteId = 0;
            CancellableSocket.Deadline deadline = new CancellableSocket.Deadline(timeoutMillis);
            byte[] destination = ("shell:" + command + "\u0000").getBytes(StandardCharsets.UTF_8);
            if (destination.length > maxData) throw new IOException("ADB command is too large");
            write(AdbProtocol.generateMessage(AdbProtocol.A_OPEN, localId, 0, destination), deadline);
            while (true) {
                AdbProtocol.Message message = read(deadline);
                if (message.arg1 != localId) {
                    // A prior command may still have a CLSE in flight when the next command opens.
                    if (message.command == AdbProtocol.A_WRTE && message.arg1 > 0) {
                        write(AdbProtocol.generateClose(message.arg1, message.arg0), deadline);
                    }
                    continue;
                }
                if (message.command == AdbProtocol.A_OKAY) {
                    if (message.arg0 <= 0 || (remoteId != 0 && remoteId != message.arg0)) {
                        throw new IOException("Invalid ADB stream acknowledgement");
                    }
                    remoteId = message.arg0;
                } else if (message.command == AdbProtocol.A_WRTE) {
                    if (remoteId == 0 || message.arg0 != remoteId) throw new IOException("Invalid ADB stream ID");
                    boolean complete = handler.onPayload(message.payload == null ? new byte[0] : message.payload);
                    write(AdbProtocol.generateReady(localId, remoteId), deadline);
                    if (complete) {
                        write(AdbProtocol.generateClose(localId, remoteId), deadline);
                        success = true;
                        return;
                    }
                } else if (message.command == AdbProtocol.A_CLSE) {
                    if (remoteId != 0 && message.arg0 != remoteId) throw new IOException("Invalid ADB close ID");
                    if (message.arg0 != 0) write(AdbProtocol.generateClose(localId, message.arg0), deadline);
                    // Caller requires a parsed exit marker; EOF alone does not mean command success.
                    success = true;
                    return;
                } else {
                    throw new IOException("Unexpected ADB shell packet");
                }
            }
        } finally {
            if (!success) cancel();
            end();
        }
    }

    private void updateStreams() throws IOException {
        input = transport.activeSocket().getInputStream();
        output = transport.activeSocket().getOutputStream();
    }
    private AdbProtocol.Message read(CancellableSocket.Deadline deadline) throws IOException {
        transport.prepareRead(deadline);
        AdbProtocol.Message message = AdbProtocol.Message.parse(input, protocolVersion, maxData);
        transport.checkOpen();
        deadline.remainingMillis();
        return message;
    }
    private void write(byte[] packet, CancellableSocket.Deadline deadline) throws IOException {
        transport.checkOpen();
        deadline.remainingMillis();
        output.write(packet);
        output.flush();
    }
    private void begin() throws IOException {
        synchronized (lifecycle) {
            if (closed || busy) throw new IOException("ADB session is closed or already in use");
            busy = true;
        }
    }
    private void end() {
        boolean dispose;
        synchronized (lifecycle) {
            busy = false;
            dispose = claimDisposal();
        }
        if (dispose) transport.dispose();
    }
    private boolean claimDisposal() {
        if (closed && !busy && !disposed) {
            disposed = true;
            return true;
        }
        return false;
    }
    /** Nonblocking cancellation hook: do not close a TLS wrapper or destroy credentials here. */
    public void cancel() {
        synchronized (lifecycle) { closed = true; }
        connected = false;
        transport.close();
    }
    @Override public void close() {
        cancel();
        boolean dispose;
        synchronized (lifecycle) { dispose = claimDisposal(); }
        if (dispose) transport.dispose();
    }
}
