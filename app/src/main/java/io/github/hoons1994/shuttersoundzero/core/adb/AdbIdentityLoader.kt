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

        return loadEncoded(privateKeyFile.readBytes(), certificateFile.readBytes(), decodePrivateKey, nowMillis)
    }

    /** Only a missing target certificate permits recovery of an interrupted old migration. */
    fun loadEncryptedForMigration(
        privateKeyFile: File,
        certificateFile: File,
        legacyIdentities: List<Pair<File, File>>,
        decodePrivateKey: (ByteArray) -> ByteArray,
        nowMillis: Long = System.currentTimeMillis()
    ): Pair<PrivateKey, Certificate>? {
        if (Files.notExists(privateKeyFile.toPath()) || !Files.notExists(certificateFile.toPath())) {
            // A complete old pair is authoritative, including clock/Keystore/validation failures.
            return load(privateKeyFile, certificateFile, decodePrivateKey, nowMillis)
        }

        for ((legacyKey, legacyCertificate) in legacyIdentities) {
            if (Files.notExists(legacyKey.toPath()) || Files.notExists(legacyCertificate.toPath())) continue
            try {
                // Require an intact original and prove that the encrypted copy has the SAME key.
                load(legacyKey, legacyCertificate, { it }, nowMillis) ?: continue
                return load(privateKeyFile, legacyCertificate, decodePrivateKey, nowMillis)
            } catch (_: Exception) {
                // Neither an unrelated original nor unavailable crypto authorizes replacement.
            }
        }
        throw IOException("Incomplete ADB identity; no matching legacy identity could restore its certificate")
    }

    fun loadEncoded(
        encodedPrivateKey: ByteArray,
        encodedCertificate: ByteArray,
        decodePrivateKey: (ByteArray) -> ByteArray,
        nowMillis: Long = System.currentTimeMillis()
    ): Pair<PrivateKey, Certificate> {
        val privateKeyBytes = decodePrivateKey(encodedPrivateKey)
        val privateKey = try {
            KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
        } finally {
            privateKeyBytes.fill(0)
        }
        val certificate = encodedCertificate.inputStream().use {
            CertificateFactory.getInstance("X.509").generateCertificate(it)
        }
        if (!AdbIdentityValidator.isValid(privateKey, certificate, nowMillis)) {
            throw IOException("ADB identity validation failed; check device time before resetting pairing")
        }
        return privateKey to certificate
    }
}
