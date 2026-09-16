package io.github.muntashirakon.adb;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import org.junit.Test;
import static org.junit.Assert.*;

public class LocalAdbTlsTest {
    @Test public void invalidExpiredWeakAndForgedCertificatesAreRejected() throws Exception {
        X509Certificate valid = new TestTlsKeys().certificate;
        LocalAdbTls.EndpointTrustManager.validateCertificate(new X509Certificate[]{valid}, "RSA");
        assertThrows(CertificateException.class, () -> LocalAdbTls.EndpointTrustManager.validateCertificate(null, "RSA"));
        assertThrows(CertificateException.class, () -> LocalAdbTls.EndpointTrustManager.validateCertificate(new X509Certificate[]{valid, valid}, "RSA"));
        for (TestTlsKeys fixture : new TestTlsKeys[]{
                new TestTlsKeys(2048, -120_000, -60_000, false),
                new TestTlsKeys(2048, 60_000, 120_000, false),
                new TestTlsKeys(1024, -60_000, 120_000, false),
                new TestTlsKeys(2048, -60_000, 120_000, true)}) {
            assertThrows(CertificateException.class, () -> LocalAdbTls.EndpointTrustManager.validateCertificate(
                    new X509Certificate[]{fixture.certificate}, "RSA"));
        }
    }
    @Test public void socketlessFallbacksDoNotTrustCertificates() throws Exception {
        LocalAdbTls.EndpointTrustManager trust = new LocalAdbTls.EndpointTrustManager(LocalAdbTls.Identity.COMMAND);
        X509Certificate[] chain = {new TestTlsKeys().certificate};
        assertThrows(CertificateException.class, () -> trust.checkServerTrusted(chain, "RSA"));
        assertThrows(CertificateException.class, () -> trust.checkServerTrusted(chain, "RSA", (Socket) null));
        assertThrows(CertificateException.class, () -> trust.checkClientTrusted(chain, "RSA"));
    }
    @Test public void explicitPoliciesCompleteRealTls13HandshakesWithoutPretendingToUseHttps() throws Exception {
        for (LocalAdbTls.Identity identity : LocalAdbTls.Identity.values()) {
            TestTlsKeys clientKeys = new TestTlsKeys();
            TestTlsKeys serverKeys = new TestTlsKeys();
            AtomicReference<Throwable> error = new AtomicReference<>();
            try (SSLServerSocket server = (SSLServerSocket) serverKeys.serverContext(clientKeys.certificate)
                    .getServerSocketFactory().createServerSocket(0);
                    CancellableSocket client = new CancellableSocket()) {
                server.setSoTimeout(3000);
                server.setNeedClientAuth(true);
                Thread peer = new Thread(() -> {
                    try (SSLSocket socket = (SSLSocket) server.accept()) {
                        socket.setSoTimeout(3000);
                        assertEquals(7, socket.getInputStream().read());
                        socket.getOutputStream().write(9);
                    } catch (Throwable failure) { error.set(failure); }
                });
                peer.start();
                try {
                    CancellableSocket.Deadline deadline = new CancellableSocket.Deadline(3000);
                    client.connect("127.0.0.1", server.getLocalPort(), deadline);
                    SSLSocket socket = client.startTls(clientKeys.client(identity), "127.0.0.1", server.getLocalPort(), deadline);
                    assertEquals(identity.algorithm, socket.getSSLParameters().getEndpointIdentificationAlgorithm());
                    assertEquals("TLSv1.3", socket.getSession().getProtocol());
                    socket.getOutputStream().write(7);
                    assertEquals(9, socket.getInputStream().read());
                } finally { client.dispose(); peer.join(4000); }
                assertFalse(peer.isAlive());
                assertNull(error.get());
            }
        }
    }
    @Test public void wrongPolicyCannotUseAnotherModesTrustManager() throws Exception {
        TestTlsKeys keys = new TestTlsKeys();
        try (ServerSocket server = new ServerSocket(0);
                Socket raw = new Socket("127.0.0.1", server.getLocalPort());
                Socket peer = server.accept()) {
            SSLSocket socket = (SSLSocket) keys.client(LocalAdbTls.Identity.COMMAND).context()
                    .getSocketFactory().createSocket(raw, "127.0.0.1", server.getLocalPort(), true);
            try {
                keys.client(LocalAdbTls.Identity.PAIRING).configure(socket);
                assertThrows(CertificateException.class, () -> new LocalAdbTls.EndpointTrustManager(LocalAdbTls.Identity.COMMAND)
                        .checkServerTrusted(new X509Certificate[]{keys.certificate}, "RSA", socket));
            } finally { raw.close(); socket.close(); }
        }
    }
    @Test public void authenticatedPeerInfoRequiresDeviceTypeAndGuid() throws Exception {
        byte[] info = new byte[8192];
        info[0] = 1; info[1] = 'a';
        PairingPeerInfo.requireDeviceGuid(info);
        info[0] = 0;
        assertThrows(IOException.class, () -> PairingPeerInfo.requireDeviceGuid(info));
        info[0] = 1; info[1] = 0;
        assertThrows(IOException.class, () -> PairingPeerInfo.requireDeviceGuid(info));
        assertThrows(IOException.class, () -> PairingPeerInfo.requireDeviceGuid(new byte[1]));
        java.util.Arrays.fill(info, (byte) 'a'); info[0] = 1;
        assertThrows(IOException.class, () -> PairingPeerInfo.requireDeviceGuid(info));
    }
}
