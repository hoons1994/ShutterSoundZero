package com.charmingcolor.shuttersoundzero.core.adb

import java.security.Key
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.security.interfaces.RSAKey
import java.util.Date

/**
 * Validates the persisted ADB identity before it is reused for TLS authentication.
 *
 * A parseable key/certificate pair is not sufficient. The identity must also use an
 * adequately sized RSA key, be within its X.509 validity window, be self-signed by the
 * persisted public key, and prove that the private key belongs to that certificate.
 */
internal object AdbIdentityValidator {
    private const val MIN_RSA_KEY_BITS = 2048
    private val CHALLENGE = "ShutterSoundZero ADB identity validation".toByteArray(Charsets.UTF_8)

    fun isValid(
        privateKey: PrivateKey,
        certificate: Certificate,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val x509 = certificate as? X509Certificate ?: return false
        val publicKey = x509.publicKey

        if (!isStrongRsaKey(privateKey) || !isStrongRsaKey(publicKey)) return false
        if (!keysMatch(privateKey, publicKey)) return false

        return try {
            x509.checkValidity(Date(nowMillis))
            x509.verify(publicKey)
            true
        } catch (_: Exception) {
            false
        }
    }

    internal fun keysMatch(privateKey: PrivateKey, publicKey: PublicKey): Boolean {
        return try {
            val signer = Signature.getInstance("SHA256withRSA")
            signer.initSign(privateKey)
            signer.update(CHALLENGE)
            val signature = signer.sign()

            val verifier = Signature.getInstance("SHA256withRSA")
            verifier.initVerify(publicKey)
            verifier.update(CHALLENGE)
            verifier.verify(signature)
        } catch (_: Exception) {
            false
        }
    }

    private fun isStrongRsaKey(key: Key): Boolean {
        if (!key.algorithm.equals("RSA", ignoreCase = true)) return false
        val rsaKey = key as? RSAKey ?: return false
        return rsaKey.modulus.bitLength() >= MIN_RSA_KEY_BITS
    }
}
