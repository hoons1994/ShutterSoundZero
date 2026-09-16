package io.github.muntashirakon.adb;

import org.junit.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Loopback protocol tests. These do not claim to exercise Android Conscrypt or a real adbd. */
public class CancellableAdbConnectionTest {
    private static final int API = 30;
    private static final int VERSION = AdbProtocol.getProtocolVersion(API);
    private static final int MAX_DATA = AdbProtocol.getMaxData(API);
    @FunctionalInterface interface PeerScript { void run(Socket socket) throws Exception; }
    private static class Peer implements AutoCloseable {
        final ServerSocket server = new ServerSocket(0);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final Thread worker;
        Peer(PeerScript script) throws IOException {
            server.setSoTimeout(3000);
            worker = new Thread(() -> {
                try (Socket socket = server.accept()) { socket.setSoTimeout(3000); script.run(socket); }
                catch (Throwable error) { failure.set(error); }
            });
            worker.start();
        }
        @Override public void close() throws Exception {
            worker.join(4000);
            server.close();
            assertFalse("Peer did not finish", worker.isAlive());
            if (failure.get() != null) throw new AssertionError("Peer failed", failure.get());
        }
    }
    private static CancellableAdbConnection connection(int port) throws Exception {
        java.security.KeyPair keys = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        Certificate certificate = new Certificate("test") {
            public byte[] getEncoded() { return new byte[0]; }
            public void verify(PublicKey key) { }
            public void verify(PublicKey key, String provider) { }
            public PublicKey getPublicKey() { return keys.getPublic(); }
            public String toString() { return "Loopback test certificate"; }
        };
        return new CancellableAdbConnection("127.0.0.1", port, keys.getPrivate(), certificate, "test", API);
    }
    private static AdbProtocol.Message read(Socket socket) throws IOException {
        return AdbProtocol.Message.parse(socket.getInputStream(), VERSION, MAX_DATA);
    }
    private static void send(Socket socket, byte[] packet) throws IOException {
        socket.getOutputStream().write(packet); socket.getOutputStream().flush();
    }
    private static void handshake(Socket socket) throws IOException {
        assertEquals(AdbProtocol.A_CNXN, read(socket).command);
        send(socket, AdbProtocol.generateMessage(AdbProtocol.A_CNXN, VERSION, MAX_DATA, new byte[0]));
    }

    @Test public void connectTimeoutClosesFailedSocketOnEveryAttempt() throws Exception {
        for (int i = 0; i < 3; i++) {
            try (Peer peer = new Peer(socket -> {
                assertEquals(AdbProtocol.A_CNXN, read(socket).command);
                assertEquals(-1, socket.getInputStream().read());
            }); CancellableAdbConnection client = connection(peer.server.getLocalPort())) {
                assertThrows(SocketTimeoutException.class, () -> client.connect(150));
                assertFalse(client.isConnected());
            }
        }
    }

    @Test public void cancelClosesAnInProgressHandshake() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        try (Peer peer = new Peer(socket -> {
            assertEquals(AdbProtocol.A_CNXN, read(socket).command);
            received.countDown();
            assertEquals(-1, socket.getInputStream().read());
        }); CancellableAdbConnection client = connection(peer.server.getLocalPort())) {
            AtomicReference<Throwable> error = new AtomicReference<>();
            Thread worker = new Thread(() -> { try { client.connect(5000); } catch (Throwable e) { error.set(e); } });
            worker.start();
            try {
                assertTrue(received.await(2, TimeUnit.SECONDS));
                client.cancel(); worker.join(2000);
                assertFalse(worker.isAlive());
                assertTrue(error.get() instanceof IOException);
                assertFalse(client.isConnected());
            } finally { client.cancel(); worker.join(2000); }
        }
    }

    @Test public void sequentialShellCommandsIgnoreLateCloseFromPreviousStream() throws Exception {
        try (Peer peer = new Peer(socket -> {
            handshake(socket);
            int previousLocal = 0;
            for (int remote = 101; remote <= 102; remote++) {
                AdbProtocol.Message open = read(socket);
                assertEquals(AdbProtocol.A_OPEN, open.command);
                assertTrue(new String(open.payload, StandardCharsets.UTF_8).startsWith("shell:echo"));
                int local = open.arg0;
                if (previousLocal != 0) send(socket, AdbProtocol.generateClose(remote - 1, previousLocal));
                send(socket, AdbProtocol.generateReady(remote, local));
                send(socket, AdbProtocol.generateMessage(AdbProtocol.A_WRTE, remote, local, ("result" + remote).getBytes(StandardCharsets.UTF_8)));
                assertEquals(AdbProtocol.A_OKAY, read(socket).command);
                assertEquals(AdbProtocol.A_CLSE, read(socket).command);
                previousLocal = local;
            }
        }); CancellableAdbConnection client = connection(peer.server.getLocalPort())) {
            client.connect(2000);
            for (int remote = 101; remote <= 102; remote++) {
                AtomicReference<String> result = new AtomicReference<>();
                client.readShell("echo 한글", 2000, data -> { result.set(new String(data, StandardCharsets.UTF_8)); return true; });
                assertEquals("result" + remote, result.get());
            }
        }
    }

    @Test public void shellOpenTimeoutClosesConnection() throws Exception {
        try (Peer peer = new Peer(socket -> {
            handshake(socket); assertEquals(AdbProtocol.A_OPEN, read(socket).command);
            assertEquals(-1, socket.getInputStream().read());
        }); CancellableAdbConnection client = connection(peer.server.getLocalPort())) {
            client.connect(2000);
            assertThrows(SocketTimeoutException.class, () -> client.readShell("echo test", 100, data -> true));
            assertFalse(client.isConnected());
        }
    }

    @Test public void oversizedPacketIsRejectedBeforeAllocatingItsPayload() throws Exception {
        try (Peer peer = new Peer(socket -> {
            handshake(socket);
            AdbProtocol.Message open = read(socket);
            byte[] header = AdbProtocol.generateMessage(AdbProtocol.A_WRTE, 1, open.arg0, null);
            java.nio.ByteBuffer.wrap(header).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(12, Integer.MAX_VALUE);
            send(socket, header);
            assertEquals(-1, socket.getInputStream().read());
        }); CancellableAdbConnection client = connection(peer.server.getLocalPort())) {
            client.connect(2000);
            assertThrows(IOException.class, () -> client.readShell("echo test", 1000, data -> true));
            assertFalse(client.isConnected());
        }
    }

    @Test public void payloadHandlerFailureClosesSession() throws Exception {
        try (Peer peer = new Peer(socket -> {
            handshake(socket);
            AdbProtocol.Message open = read(socket);
            send(socket, AdbProtocol.generateReady(9, open.arg0));
            send(socket, AdbProtocol.generateMessage(AdbProtocol.A_WRTE, 9, open.arg0, new byte[]{1}));
            assertEquals(-1, socket.getInputStream().read());
        }); CancellableAdbConnection client = connection(peer.server.getLocalPort())) {
            client.connect(2000);
            IOException expected = new IOException("output limit");
            assertSame(expected, assertThrows(IOException.class, () -> client.readShell("echo", 1000, data -> { throw expected; })));
            assertFalse(client.isConnected());
        }
    }
}
