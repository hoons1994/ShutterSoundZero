package io.github.hoons1994.shuttersoundzero.core.adb

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.PrivateKey
import java.security.cert.Certificate

/** A single commit point for the encrypted private key and its certificate. */
internal class AdbIdentityBundleStore(
    private val file: File,
    private val syncDirectory: (File) -> Unit = {},
    private val commit: (File, File) -> Unit = { staged, target ->
        // A failed/unsupported atomic move must never fall back to copy/delete.
        Files.move(staged.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }
) {
    private val directory = requireNotNull(file.parentFile) { "ADB identity bundle requires a parent directory" }
    private val stagingFile = File(directory, "${file.name}.tmp")

    fun load(
        decodePrivateKey: (ByteArray) -> ByteArray,
        nowMillis: Long = System.currentTimeMillis()
    ): Pair<PrivateKey, Certificate>? {
        if (Files.notExists(file.toPath())) return null // An interrupted staging write is not committed.
        val identity = DataInputStream(file.inputStream()).use { input ->
            if (input.readInt() != MAGIC) throw IOException("Unrecognized ADB identity bundle")
            val keyLength = input.readInt()
            val certificateLength = input.readInt()
            requireLengths(keyLength, certificateLength)
            val key = ByteArray(keyLength).also(input::readFully)
            val certificate = ByteArray(certificateLength).also(input::readFully)
            if (input.read() != -1) throw IOException("Unexpected trailing ADB identity data")
            AdbIdentityLoader.loadEncoded(key, certificate, decodePrivateKey, nowMillis)
        }
        // Also completes a previous rename interrupted before its directory was synced.
        syncDirectory(directory)
        stagingFile.delete()
        return identity
    }

    fun write(encryptedPrivateKey: ByteArray, certificate: ByteArray) {
        requireLengths(encryptedPrivateKey.size, certificate.size)
        if (!Files.notExists(file.toPath())) throw IOException("An existing ADB identity bundle must be preserved")
        try {
            FileOutputStream(stagingFile).use { stream ->
                val output = DataOutputStream(stream)
                output.writeInt(MAGIC)
                output.writeInt(encryptedPrivateKey.size)
                output.writeInt(certificate.size)
                output.write(encryptedPrivateKey)
                output.write(certificate)
                output.flush()
                stream.fd.sync()
            }
            commit(stagingFile, file)
            syncDirectory(directory)
        } finally {
            // A committed bundle is never rolled back after an uncertain I/O/Keystore failure.
            stagingFile.delete()
        }
    }

    private fun requireLengths(keyLength: Int, certificateLength: Int) {
        if (keyLength !in 1..MAX_COMPONENT_BYTES || certificateLength !in 1..MAX_COMPONENT_BYTES) {
            throw IOException("Invalid ADB identity bundle size")
        }
    }

    companion object {
        private const val MAGIC = 0x53535A32 // "SSZ2"; the encrypted key keeps its SSZ1 AES-GCM format.
        private const val MAX_COMPONENT_BYTES = 64 * 1024
    }
}
