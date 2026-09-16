package io.github.muntashirakon.adb;

import java.math.BigInteger;
import java.net.Socket;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Date;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/** Generates disposable test credentials; no bundled secret or trust-all test server. */
final class DeviceTlsKeys {
    final java.security.KeyPair keys;
    final X509Certificate certificate;
    DeviceTlsKeys() throws Exception { this(2048, -60_000, 86_400_000, false); }
    DeviceTlsKeys(int bits, long start, long end, boolean wrongSigner) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(bits);
        keys = generator.generateKeyPair();
        X500Name name = new X500Name("CN=adb");
        long now = System.currentTimeMillis();
        certificate = new JcaX509CertificateConverter().getCertificate(
                new JcaX509v3CertificateBuilder(name, new BigInteger(120, new SecureRandom()),
                        new Date(now + start), new Date(now + end), name, keys.getPublic())
                        .build(new JcaContentSignerBuilder("SHA256withRSA").build(
                                wrongSigner ? generator.generateKeyPair().getPrivate() : keys.getPrivate())));
    }
    LocalAdbTls client(LocalAdbTls.Identity kind) throws Exception {
        return LocalAdbTls.create(keys.getPrivate(), certificate, kind);
    }
    SSLContext serverContext(X509Certificate expectedClient) throws Exception {
        KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        store.load(null, null);
        char[] password = new char[0];
        store.setKeyEntry("server", keys.getPrivate(), password, new java.security.cert.Certificate[]{certificate});
        KeyManagerFactory manager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        manager.init(store, password);
        SSLContext context = SSLContext.getInstance("TLSv1.3");
        context.init(manager.getKeyManagers(), new javax.net.ssl.TrustManager[]{new PinnedClient(expectedClient)}, null);
        return context;
    }
    private static final class PinnedClient extends X509ExtendedTrustManager {
        private final X509Certificate expected;
        PinnedClient(X509Certificate expected) { this.expected = expected; }
        private void check(X509Certificate[] chain) throws CertificateException {
            if (chain == null || chain.length != 1 || !Arrays.equals(expected.getEncoded(), chain[0].getEncoded())) {
                throw new CertificateException("Unexpected test TLS client");
            }
            chain[0].checkValidity();
        }
        @Override public void checkClientTrusted(X509Certificate[] chain, String type) throws CertificateException { check(chain); }
        @Override public void checkClientTrusted(X509Certificate[] chain, String type, Socket socket) throws CertificateException { check(chain); }
        @Override public void checkClientTrusted(X509Certificate[] chain, String type, SSLEngine engine) throws CertificateException { check(chain); }
        @Override public void checkServerTrusted(X509Certificate[] chain, String type) throws CertificateException { throw new CertificateException("server only"); }
        @Override public void checkServerTrusted(X509Certificate[] chain, String type, Socket socket) throws CertificateException { throw new CertificateException("server only"); }
        @Override public void checkServerTrusted(X509Certificate[] chain, String type, SSLEngine engine) throws CertificateException { throw new CertificateException("server only"); }
        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[]{expected}; }
    }
}
