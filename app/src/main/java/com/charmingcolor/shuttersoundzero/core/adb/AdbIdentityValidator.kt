package com.charmingcolor.shuttersoundzero.core.adb

import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature

/**
 * Verifies that a persisted ADB private key actually belongs to the certificate public key.
 * Parsing both files successfully is not enough: a stale or mixed pair would otherwise fail
 * later during TLS authentication in a much less obvious way.
 */
internal object AdbIdentityValidator {
    private val CHALLENGE = "ShutterSoundZero ADB identity validation".toByteArray(Charsets.UTF_8)

    fun keysMatch(privateKey: PrivateKey, publicKey: PublicKey): Boolean {
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
}
