package com.charmingcolor.shuttersoundzero.core.adb

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.Date

class AdbIdentityValidatorTest {
    private val now = 1_800_000_000_000L

    @Test
    fun `valid self signed RSA identity is accepted`() {
        val keyPair = generateRsaKeyPair(2048)
        val certificate = generateCertificate(
            subjectKeyPair = keyPair,
            signingKeyPair = keyPair,
            notBefore = now - 60_000,
            notAfter = now + 60_000
        )

        assertTrue(AdbIdentityValidator.isValid(keyPair.private, certificate, now))
    }

    @Test
    fun `different RSA private key is rejected`() {
        val certificateKeyPair = generateRsaKeyPair(2048)
        val differentPrivateKeyPair = generateRsaKeyPair(2048)
        val certificate = generateCertificate(
            subjectKeyPair = certificateKeyPair,
            signingKeyPair = certificateKeyPair,
            notBefore = now - 60_000,
            notAfter = now + 60_000
        )

        assertFalse(AdbIdentityValidator.isValid(differentPrivateKeyPair.private, certificate, now))
    }

    @Test
    fun `weak RSA identity is rejected`() {
        val keyPair = generateRsaKeyPair(1024)
        val certificate = generateCertificate(
            subjectKeyPair = keyPair,
            signingKeyPair = keyPair,
            notBefore = now - 60_000,
            notAfter = now + 60_000
        )

        assertFalse(AdbIdentityValidator.isValid(keyPair.private, certificate, now))
    }

    @Test
    fun `expired certificate is rejected`() {
        val keyPair = generateRsaKeyPair(2048)
        val certificate = generateCertificate(
            subjectKeyPair = keyPair,
            signingKeyPair = keyPair,
            notBefore = now - 120_000,
            notAfter = now - 60_000
        )

        assertFalse(AdbIdentityValidator.isValid(keyPair.private, certificate, now))
    }

    @Test
    fun `not yet valid certificate is rejected`() {
        val keyPair = generateRsaKeyPair(2048)
        val certificate = generateCertificate(
            subjectKeyPair = keyPair,
            signingKeyPair = keyPair,
            notBefore = now + 60_000,
            notAfter = now + 120_000
        )

        assertFalse(AdbIdentityValidator.isValid(keyPair.private, certificate, now))
    }

    @Test
    fun `certificate not self signed by its public key is rejected`() {
        val subjectKeyPair = generateRsaKeyPair(2048)
        val signerKeyPair = generateRsaKeyPair(2048)
        val certificate = generateCertificate(
            subjectKeyPair = subjectKeyPair,
            signingKeyPair = signerKeyPair,
            notBefore = now - 60_000,
            notAfter = now + 60_000
        )

        assertFalse(AdbIdentityValidator.isValid(subjectKeyPair.private, certificate, now))
    }

    private fun generateRsaKeyPair(bits: Int): KeyPair = KeyPairGenerator.getInstance("RSA").apply {
        initialize(bits)
    }.generateKeyPair()

    private fun generateCertificate(
        subjectKeyPair: KeyPair,
        signingKeyPair: KeyPair,
        notBefore: Long,
        notAfter: Long
    ) = X500Name("CN=ShutterSoundZero-Test").let { subject ->
        val builder = X509v3CertificateBuilder(
            subject,
            BigInteger.valueOf(1L),
            Date(notBefore),
            Date(notAfter),
            subject,
            SubjectPublicKeyInfo.getInstance(subjectKeyPair.public.encoded)
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(signingKeyPair.private)
        JcaX509CertificateConverter().getCertificate(builder.build(signer))
    }
}
