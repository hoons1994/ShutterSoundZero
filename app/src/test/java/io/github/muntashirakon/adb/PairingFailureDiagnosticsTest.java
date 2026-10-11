package io.github.muntashirakon.adb;

import static org.junit.Assert.*;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class PairingFailureDiagnosticsTest {
    @Test public void refusedConnectionKeepsPhaseCauseAndSafeCategory() throws Exception {
        TestTlsKeys keys = new TestTlsKeys();
        int port;
        try (ServerSocket unused = new ServerSocket(0)) { port = unused.getLocalPort(); }
        List<PairingFailureException.Phase> phases = new ArrayList<>();
        List<PairingFailureException> failures = new ArrayList<>();
        try (CancellablePairingConnection client = new CancellablePairingConnection("127.0.0.1", port,
                "123456".getBytes(java.nio.charset.StandardCharsets.UTF_8), keys.keys.getPrivate(), keys.certificate, "test")) {
            PairingFailureException failure = assertThrows(PairingFailureException.class,
                    () -> client.start(3000, (phase, duration, error) -> {
                        assertTrue(duration >= 0);
                        phases.add(phase); failures.add(error);
                    }));
            assertEquals(PairingFailureException.Phase.TCP_CONNECT, failure.getPhase());
            assertEquals(PairingFailureException.Reason.CONNECTION_FAILED, failure.getReason());
            assertEquals(List.of(PairingFailureException.Phase.TCP_CONNECT), phases);
            assertSame(failure, failures.get(0));
            assertNotNull(failure.getCause());
            assertFalse(failure.getMessage().contains("127.0.0.1"));
        }
    }

    @Test public void tlsFailureFollowsSuccessfulTcpWithoutAttemptingCodeAuthentication() throws Exception {
        TestTlsKeys keys = new TestTlsKeys();
        List<PairingFailureException.Phase> phases = new ArrayList<>();
        List<PairingFailureException> failures = new ArrayList<>();
        AtomicReference<Throwable> peerError = new AtomicReference<>();
        try (ServerSocket server = new ServerSocket(0)) {
            server.setSoTimeout(3000);
            Thread peer = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    socket.getOutputStream().write(new byte[]{0, 0, 0, 0, 0});
                } catch (Throwable error) { peerError.set(error); }
            });
            peer.start();
            try (CancellablePairingConnection client = new CancellablePairingConnection("127.0.0.1", server.getLocalPort(),
                    new byte[6], keys.keys.getPrivate(), keys.certificate, "test")) {
                PairingFailureException failure = assertThrows(PairingFailureException.class,
                        () -> client.start(3000, (phase, duration, error) -> { phases.add(phase); failures.add(error); }));
                assertEquals(PairingFailureException.Phase.TLS_HANDSHAKE, failure.getPhase());
                assertEquals(List.of(PairingFailureException.Phase.TCP_CONNECT,
                        PairingFailureException.Phase.TLS_HANDSHAKE), phases);
                assertNull(failures.get(0));
                assertSame(failure, failures.get(1));
            } finally { peer.join(4000); }
            assertFalse(peer.isAlive());
            assertNull(peerError.get());
        }
    }

    @Test public void unavailableDiagnosticsCannotReplaceTransportFailure() throws Exception {
        TestTlsKeys keys = new TestTlsKeys();
        int port;
        try (ServerSocket unused = new ServerSocket(0)) { port = unused.getLocalPort(); }
        try (CancellablePairingConnection client = new CancellablePairingConnection("127.0.0.1", port,
                new byte[6], keys.keys.getPrivate(), keys.certificate, "test")) {
            PairingFailureException failure = assertThrows(PairingFailureException.class,
                    () -> client.start(3000, (phase, duration, error) -> { throw new IllegalStateException("logger unavailable"); }));
            assertEquals(PairingFailureException.Reason.CONNECTION_FAILED, failure.getReason());
        }
    }

    @Test public void timeoutAndIdentityFailuresHaveDistinctCategories() {
        assertEquals(PairingFailureException.Reason.TIMEOUT, new PairingFailureException(
                PairingFailureException.Phase.CODE_AUTHENTICATION, new SocketTimeoutException()).getReason());
        assertEquals(PairingFailureException.Reason.IDENTITY_UNAVAILABLE, new PairingFailureException(
                PairingFailureException.Phase.IDENTITY, new IOException("private details")).getReason());
    }
}
