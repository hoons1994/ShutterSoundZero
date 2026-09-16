package io.github.muntashirakon.adb;

import org.junit.Test;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class CancellableSocketTest {
    @Test public void closeBeforeConnectRejectsUseAndIsIdempotent() {
        CancellableSocket transport = new CancellableSocket();
        transport.close(); transport.close(); transport.dispose();
        assertTrue(transport.isClosed());
        assertThrows(IOException.class, () -> transport.connect("127.0.0.1", 1, new CancellableSocket.Deadline(1000)));
    }

    @Test public void cancellationClosesSocketDuringConnectNotJustAfterIt() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        Socket raw = new Socket() {
            @Override public void connect(SocketAddress endpoint, int timeout) throws IOException {
                assertTrue(timeout > 0 && timeout <= 1000);
                entered.countDown();
                try { if (!closed.await(2, TimeUnit.SECONDS)) throw new IOException("not closed"); }
                catch (InterruptedException e) { throw new IOException(e); }
                throw new IOException("closed during connect");
            }
            @Override public void close() { closed.countDown(); }
        };
        CancellableSocket transport = new CancellableSocket(raw);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try { transport.connect("127.0.0.1", 1, new CancellableSocket.Deadline(1000)); }
            catch (Throwable e) { failure.set(e); }
        });
        worker.start();
        try { assertTrue(entered.await(2, TimeUnit.SECONDS)); transport.close(); worker.join(2000); }
        finally { transport.close(); worker.join(2000); }
        assertFalse(worker.isAlive());
        assertTrue(failure.get() instanceof IOException);
    }

    @Test public void rawReadUnblocksWhenCancelled() throws Exception { cancelBlockedIo(false); }
    @Test public void tlsHandshakeUnblocksWhenRawSocketIsCancelled() throws Exception { cancelBlockedIo(true); }

    private void cancelBlockedIo(boolean tls) throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            server.setSoTimeout(2000);
            CancellableSocket transport = new CancellableSocket();
            transport.connect("127.0.0.1", server.getLocalPort(), new CancellableSocket.Deadline(5000));
            try (Socket peer = server.accept()) {
                peer.setSoTimeout(2000);
                CountDownLatch entered = new CountDownLatch(1);
                AtomicReference<Throwable> failure = new AtomicReference<>();
                LocalAdbTls context = new TestTlsKeys().client(LocalAdbTls.Identity.COMMAND);
                Thread worker = new Thread(() -> {
                    try {
                        entered.countDown();
                        if (tls) transport.startTls(context, "127.0.0.1", server.getLocalPort(), new CancellableSocket.Deadline(5000));
                        else transport.activeSocket().getInputStream().read();
                        failure.set(new AssertionError("Expected cancellation"));
                    } catch (Throwable error) { failure.set(error); }
                    finally { transport.dispose(); }
                });
                worker.start();
                try {
                    assertTrue(entered.await(2, TimeUnit.SECONDS));
                    // Receiving ClientHello proves the TLS worker is in the handshake, not merely queued.
                    if (tls) assertTrue(peer.getInputStream().read() >= 0);
                    transport.close();
                    worker.join(2000);
                    assertFalse(worker.isAlive());
                    assertTrue(failure.get() instanceof IOException);
                } finally { transport.close(); worker.join(2000); }
            }
        }
    }

    @Test public void nonLoopbackAddressesAndDnsNamesAreRejectedBeforeConnect() {
        Socket raw = new Socket() {
            @Override public void connect(SocketAddress endpoint, int timeout) {
                fail("Disallowed endpoint reached Socket.connect");
            }
        };
        try (CancellableSocket transport = new CancellableSocket(raw)) {
            for (String host : new String[]{"192.0.2.1", "192.168.1.1", "example.com",
                    "localhost", "127.0.0.1.example.com", "0.0.0.0", "::", "", null}) {
                assertThrows(IOException.class, () -> transport.connect(host, 12345,
                        new CancellableSocket.Deadline(1000)));
            }
        }
    }

    @Test public void invalidPortsAreRejectedBeforeConnect() {
        Socket raw = new Socket() {
            @Override public void connect(SocketAddress endpoint, int timeout) {
                fail("Invalid port reached Socket.connect");
            }
        };
        try (CancellableSocket transport = new CancellableSocket(raw)) {
            for (int port : new int[]{-1, 0, 65536}) {
                assertThrows(IOException.class, () -> transport.connect("127.0.0.1", port,
                        new CancellableSocket.Deadline(1000)));
            }
        }
    }

    @Test public void tlsCannotRelabelTheConnectedEndpoint() throws Exception {
        try (ServerSocket server = new ServerSocket(0); CancellableSocket transport = new CancellableSocket()) {
            server.setSoTimeout(2000);
            transport.connect("127.0.0.1", server.getLocalPort(), new CancellableSocket.Deadline(1000));
            try (Socket peer = server.accept()) {
                LocalAdbTls context = new TestTlsKeys().client(LocalAdbTls.Identity.COMMAND);
                int wrongPort = server.getLocalPort() == 65535 ? 65534 : server.getLocalPort() + 1;
                assertThrows(IOException.class, () -> transport.startTls(context, "127.0.0.1", wrongPort,
                        new CancellableSocket.Deadline(1000)));
                assertThrows(IOException.class, () -> transport.startTls(context, "example.com", server.getLocalPort(),
                        new CancellableSocket.Deadline(1000)));
            }
        }
    }

    @Test public void readDeadlineIsFinite() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CancellableSocket transport = new CancellableSocket();
            try {
                transport.connect("127.0.0.1", server.getLocalPort(), new CancellableSocket.Deadline(1000));
                try (Socket peer = server.accept()) {
                    transport.prepareRead(new CancellableSocket.Deadline(50));
                    assertThrows(SocketTimeoutException.class, () -> transport.activeSocket().getInputStream().read());
                }
            } finally { transport.dispose(); }
        }
    }
}
