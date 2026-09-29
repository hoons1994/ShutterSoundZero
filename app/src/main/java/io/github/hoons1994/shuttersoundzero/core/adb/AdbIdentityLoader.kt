package io.github.hoons1994.shuttersoundzero.core.adb

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec

/** A failed read or validation never authorizes replacing an existing pairing identity. */
internal object AdbIdentityLoader {
    fun load(
        privateKeyFile: File,
        certificateFile: File,
        decodePrivateKey: (ByteArray) -> ByteArray,
        nowMillis: Long = System.currentTimeMillis()
    ): Pair<PrivateKey, Certificate>? {
        val privateKeyMissing = Files.notExists(privateKeyFile.toPath())
        val certificateMissing = Files.notExists(certificateFile.toPath())
        if (privateKeyMissing && certificateMissing) return null
        if (privateKeyMissing || certificateMissing) {
            throw IOException("Incomplete ADB identity; existing files have been preserved")
        }

        val privateKeyBytes = decodePrivateKey(privateKeyFile.readBytes())
        val privateKey = try {
            KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
        } finally {
            privateKeyBytes.fill(0)
        }
        val certificate = certificateFile.inputStream().use {
            CertificateFactory.getInstance("X.509").generateCertificate(it)
        }
        if (!AdbIdentityValidator.isValid(privateKey, certificate, nowMillis)) {
            throw IOException("ADB identity validation failed; check device time before resetting pairing")
        }
        return privateKey to certificate
    }
}
