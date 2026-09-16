// SPDX-License-Identifier: GPL-3.0-or-later
// Protocol flow adapted from libadb-android 3.1.1 PairingConnectionCtx (Muntashir Al-Islam).
// Cryptography remains in libadb; this adapter changes transport ownership and cleanup only.
package io.github.muntashirakon.adb;

import android.annotation.SuppressLint;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;

/** Single-use pairing client. close() aborts I/O; only start()'s worker destroys native PAKE state. */
public final class CancellablePairingConnection implements Closeable {
    private static final int PEER_INFO_SIZE = 8192;
    private static final int MAX_PAYLOAD = 2 * PEER_INFO_SIZE;
    private final Object lifecycle = new Object();
    private final CancellableSocket transport = new CancellableSocket();
    private final String host;
    private final int port;
    private final KeyPair keys;
    private final String deviceName;
    private final byte[] password;
    private boolean started;
    private boolean closed;

    public CancellablePairingConnection(String host, int port, byte[] password,
            PrivateKey privateKey, Certificate certificate, String deviceName) {
        this.host = host;
        this.port = port;
        this.password = password.clone();
        this.keys = new KeyPair(privateKey, certificate);
        this.deviceName = deviceName;
    }

    public void start(long timeoutMillis) throws Exception {
        final byte[] code;
        synchronized (lifecycle) {
            if (started || closed) throw new IOException("Pairing client is not ready");
            started = true;
            code = password.clone();
            Arrays.fill(password, (byte) 0);
        }
        PairingAuthCtx auth = null;
        byte[] exported = null;
        byte[] combined = null;
        try {
            CancellableSocket.Deadline deadline = new CancellableSocket.Deadline(timeoutMillis);
            transport.connect(host, port, deadline);
            SSLSocket socket = transport.startTls(LocalAdbTls.create(keys.getPrivateKey(),
                    (X509Certificate) keys.getCertificate(), LocalAdbTls.Identity.PAIRING), host, port, deadline);
            DataInputStream input = new DataInputStream(socket.getInputStream());
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            exported = exportKeyingMaterial(socket);
            combined = new byte[code.length + exported.length];
            System.arraycopy(code, 0, combined, 0, code.length);
            System.arraycopy(exported, 0, combined, code.length, exported.length);
            transport.checkOpen();
            auth = PairingAuthCtx.createAlice(combined);
            if (auth == null) throw new IOException("Unable to initialize pairing authentication");

            writePacket(output, 0, auth.getMsg(), deadline);
            if (!auth.initCipher(readPacket(input, 0, deadline))) {
                throw new IOException("Pairing authentication failed");
            }
            byte[] publicKey = AndroidPubkey.encodeWithName((RSAPublicKey) keys.getPublicKey(), deviceName);
            if (publicKey.length >= PEER_INFO_SIZE) throw new IOException("Pairing public key is too large");
            byte[] peerInfo = new byte[PEER_INFO_SIZE];
            // Type 0 is ADB_RSA_PUB_KEY; the remaining bytes contain the null-terminated key/name.
            System.arraycopy(publicKey, 0, peerInfo, 1, publicKey.length);
            byte[] encrypted = auth.encrypt(peerInfo);
            if (encrypted == null) throw new IOException("Unable to encrypt pairing peer info");
            writePacket(output, 1, encrypted, deadline);
            byte[] decrypted = auth.decrypt(readPacket(input, 1, deadline));
            PairingPeerInfo.requireDeviceGuid(decrypted);
            transport.checkOpen();
            deadline.remainingMillis();
        } finally {
            // Never race native state destruction against a crypto call on another thread.
            try {
                if (auth != null) auth.destroy();
            } finally {
                Arrays.fill(code, (byte) 0);
                if (combined != null) Arrays.fill(combined, (byte) 0);
                if (exported != null) Arrays.fill(exported, (byte) 0);
                close();
                transport.dispose();
            }
        }
    }

    private void writePacket(DataOutputStream output, int type, byte[] payload,
            CancellableSocket.Deadline deadline) throws IOException {
        transport.checkOpen();
        deadline.remainingMillis();
        if (payload.length == 0 || payload.length > MAX_PAYLOAD) throw new IOException("Invalid pairing payload");
        output.writeByte(1);
        output.writeByte(type);
        output.writeInt(payload.length);
        output.write(payload);
        output.flush();
    }

    private byte[] readPacket(DataInputStream input, int expectedType,
            CancellableSocket.Deadline deadline) throws IOException {
        transport.prepareRead(deadline);
        int version = input.readUnsignedByte();
        int type = input.readUnsignedByte();
        int length = input.readInt();
        if (version != 1 || type != expectedType || length <= 0 || length > MAX_PAYLOAD) {
            throw new IOException("Invalid pairing packet header");
        }
        byte[] payload = new byte[length];
        transport.prepareRead(deadline);
        input.readFully(payload);
        transport.checkOpen();
        return payload;
    }

    @SuppressLint("PrivateApi") // Same Conscrypt exporter used by the pinned libadb pairing protocol.
    private static byte[] exportKeyingMaterial(SSLSocket socket) throws SSLException {
        try {
            Class<?> provider = Class.forName(org.conscrypt.Conscrypt.isConscrypt(socket)
                    ? "org.conscrypt.Conscrypt" : "com.android.org.conscrypt.Conscrypt");
            Method export = provider.getMethod("exportKeyingMaterial", SSLSocket.class,
                    String.class, byte[].class, int.class);
            byte[] result = (byte[]) export.invoke(null, socket, "adb-label\u0000", null, 64);
            if (result == null || result.length != 64) throw new SSLException("Invalid TLS exporter result");
            return result;
        } catch (ReflectiveOperationException error) {
            throw new SSLException("Unable to export pairing key material", error);
        }
    }

    @Override public void close() {
        synchronized (lifecycle) {
            closed = true;
            Arrays.fill(password, (byte) 0);
        }
        transport.close();
    }
}
