// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.adb;

import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Locale;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509ExtendedTrustManager;

/**
 * Explicit endpoint-identification policies for LOCAL ADB, not HTTPS/Web PKI.
 *
 * TLS admits only structurally valid, self-signed RSA certificates on literal loopback sockets.
 * This is PROVISIONAL, NOT proof of server identity. Identity is completed by:
 * - PAIRING: TLS-exporter-bound SPAKE2 plus authenticated device peer-info;
 * - COMMAND: a fresh ContentProvider challenge acknowledged with Binder UID shell/root.
 * CancellableAdbConnection cannot expose general shell operations before that second proof.
 *
 * The custom SSLParameters identifiers are implemented by the extended trust manager below.
 * They are not aliases for HTTPS, no-op flags, certificate pins, or trust-on-first-use enrollment.
 */
public final class LocalAdbTls {
    public enum Identity {
        PAIRING("SSZ-ADB-LOCAL-PAKE-v1"), COMMAND("SSZ-ADB-LOCAL-SHELL-UID-v1");
        final String algorithm;
        Identity(String algorithm) { this.algorithm = algorithm; }
    }

    private final SSLContext context;
    private final Identity identity;

    public static LocalAdbTls create(PrivateKey key, X509Certificate certificate, Identity identity)
            throws GeneralSecurityException {
        if (key == null || certificate == null || identity == null) {
            throw new GeneralSecurityException("Missing local ADB TLS identity");
        }
        SSLContext context = SSLContext.getInstance("TLSv1.3");
        context.init(new KeyManager[]{new ClientKeyManager(key, certificate)},
                new TrustManager[]{new EndpointTrustManager(identity)}, new SecureRandom());
        return new LocalAdbTls(context, identity);
    }

    private LocalAdbTls(SSLContext context, Identity identity) {
        this.context = context;
        this.identity = identity;
    }

    SSLContext context() { return context; }
    String endpointAlgorithm() { return identity.algorithm; }

    void configure(SSLSocket socket) {
        socket.setUseClientMode(true);
        socket.setEnabledProtocols(new String[]{"TLSv1.3"});
        SSLParameters parameters = socket.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm(identity.algorithm);
        socket.setSSLParameters(parameters);
    }

    static final class EndpointTrustManager extends X509ExtendedTrustManager {
        private final Identity identity;
        EndpointTrustManager(Identity identity) { this.identity = identity; }

        @Override public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException {
            if (!(socket instanceof SSLSocket) || !socket.isConnected()
                    || socket.getInetAddress() == null || !socket.getInetAddress().isLoopbackAddress()
                    || socket.getPort() < 1
                    || !identity.algorithm.equals(((SSLSocket) socket).getSSLParameters()
                            .getEndpointIdentificationAlgorithm())) {
                throw new CertificateException("Wrong local ADB endpoint-identification policy");
            }
            validateCertificate(chain, authType);
        }

        static void validateCertificate(X509Certificate[] chain, String authType)
                throws CertificateException {
            if (chain == null || chain.length != 1 || chain[0] == null
                    || authType == null || authType.isEmpty()) {
                throw new CertificateException("Expected a single local ADB certificate");
            }
            X509Certificate leaf = chain[0];
            leaf.checkValidity();
            if (!(leaf.getPublicKey() instanceof RSAPublicKey)
                    || ((RSAPublicKey) leaf.getPublicKey()).getModulus().bitLength() < 2048
                    || !leaf.getSubjectX500Principal().equals(leaf.getIssuerX500Principal())) {
                throw new CertificateException("Invalid local ADB certificate key or issuer");
            }
            String signature = leaf.getSigAlgName().toUpperCase(Locale.ROOT).replace("-", "");
            if (!signature.equals("SHA256WITHRSA") && !signature.equals("SHA384WITHRSA")
                    && !signature.equals("SHA512WITHRSA")) {
                throw new CertificateException("Unsupported local ADB certificate signature");
            }
            try {
                leaf.verify(leaf.getPublicKey());
            } catch (GeneralSecurityException error) {
                throw new CertificateException("Invalid local ADB certificate signature", error);
            }
        }

        @Override public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException { throw noSocket(); }
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException { throw noSocket(); }
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException { throw noSocket(); }
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException { throw noSocket(); }
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException { throw noSocket(); }
        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        private static CertificateException noSocket() {
            return new CertificateException("Only an explicitly identified local ADB client socket is allowed");
        }
    }

    private static final class ClientKeyManager extends X509ExtendedKeyManager {
        private static final String ALIAS = "local-adb-client";
        private final PrivateKey key;
        private final X509Certificate certificate;
        ClientKeyManager(PrivateKey key, X509Certificate certificate) {
            this.key = key;
            this.certificate = certificate;
        }
        @Override public String[] getClientAliases(String keyType, Principal[] issuers) {
            return "RSA".equals(keyType) ? new String[]{ALIAS} : null;
        }
        @Override public String chooseClientAlias(String[] keyTypes, Principal[] issuers, Socket socket) {
            if (keyTypes != null) for (String type : keyTypes) if ("RSA".equals(type)) return ALIAS;
            return null;
        }
        @Override public X509Certificate[] getCertificateChain(String alias) {
            return ALIAS.equals(alias) ? new X509Certificate[]{certificate} : null;
        }
        @Override public PrivateKey getPrivateKey(String alias) { return ALIAS.equals(alias) ? key : null; }
        @Override public String[] getServerAliases(String keyType, Principal[] issuers) { return null; }
        @Override public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) { return null; }
    }
}
