package io.github.hoons1994.shuttersoundzero.core.adb

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.DataInputStream
import java.security.PrivateKey
import java.security.cert.Certificate
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.assertArrayEquals
import java.io.IOException
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdbKeyHelperInstrumentedTest {

    private lateinit var context: Context
    private lateinit var identityFiles: List<File>

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        identityFiles = listOf(
            File(context.noBackupFilesDir, "adb_identity.enc"),
            File(context.noBackupFilesDir, "adb_identity.enc.tmp"),
            File(context.noBackupFilesDir, "adb_private_key.enc"),
            File(context.noBackupFilesDir, "adb_private_key.enc.tmp"),
            File(context.noBackupFilesDir, "adb_private_key.der"),
            File(context.noBackupFilesDir, "adb_cert.der"),
            File(context.filesDir, "adb_private_key.der"),
            File(context.filesDir, "adb_cert.der")
        )
        identityFiles.forEach { file ->
            file.parentFile?.mkdirs()
            file.writeText("stale-test-data")
        }
    }

    @After
    fun tearDown() {
        identityFiles.forEach { it.delete() }
    }

    @Test
    fun resetIdentity_removesCurrentAndLegacyIdentityFiles() {
        AdbKeyHelper.resetIdentity(context)

        identityFiles.forEach { file ->
            assertFalse("Expected ${file.name} to be removed", file.exists())
        }
    }

    @Test fun failedExistingIdentityLoadPreservesCurrentAndLegacyFiles() {
        assertThrows(IOException::class.java) { AdbKeyHelper.getOrCreateKeyPairAndCertificate(context) }
        identityFiles.forEach { file -> assertEquals("stale-test-data", file.readText()) }
    }

    @Test fun firstIdentityCreationRetriesAfterAnInterruptedStagingWrite() {
        clearFiles()
        stagingFile.writeText("partial bundle from an interrupted first attempt")
        val identity = AdbKeyHelper.getOrCreateKeyPairAndCertificate(context)
        assertTrue(bundleFile.exists())
        assertFalse(stagingFile.exists())
        assertFalse(encryptedKeyFile.exists())
        assertFalse(currentCertificateFile.exists())
        assertArrayEquals(identity.second.encoded, AdbKeyHelper.getOrCreateKeyPairAndCertificate(context).second.encoded)
    }

    @Test fun bothPlaintextLocationsMigrateTheSameIdentityAndRemoveSourcesOnlyAfterCommit() {
        val original = newIdentity()
        for (directory in listOf(context.noBackupFilesDir, context.filesDir)) {
            clearFiles()
            val legacyKey = File(directory, "adb_private_key.der").apply { writeBytes(original.first.encoded) }
            val legacyCertificate = File(directory, "adb_cert.der").apply { writeBytes(original.second.encoded) }
            stagingFile.writeText("interrupted migration staging")

            val migrated = AdbKeyHelper.getOrCreateKeyPairAndCertificate(context)
            assertArrayEquals(original.second.encoded, migrated.second.encoded)
            assertTrue(bundleFile.exists())
            assertFalse(legacyKey.exists())
            assertFalse(legacyCertificate.exists())
            assertArrayEquals(original.second.encoded, AdbKeyHelper.getOrCreateKeyPairAndCertificate(context).second.encoded)
        }
    }

    @Test fun completeOldEncryptedPairMigratesWithoutChangingItsIdentity() {
        val original = newIdentity()
        val payload = encryptedPayload()
        clearFiles()
        encryptedKeyFile.writeBytes(payload)
        currentCertificateFile.writeBytes(original.second.encoded)

        val migrated = AdbKeyHelper.getOrCreateKeyPairAndCertificate(context)
        assertArrayEquals(original.second.encoded, migrated.second.encoded)
        assertTrue(bundleFile.exists())
        assertFalse(encryptedKeyFile.exists())
        assertFalse(currentCertificateFile.exists())
    }

    @Test fun interruptedOldMigrationRecoversFromMatchingPlaintextOriginal() {
        val original = newIdentity()
        val payload = encryptedPayload()
        clearFiles()
        encryptedKeyFile.writeBytes(payload)
        val legacyKey = File(context.filesDir, "adb_private_key.der").apply { writeBytes(original.first.encoded) }
        val legacyCertificate = File(context.filesDir, "adb_cert.der").apply { writeBytes(original.second.encoded) }

        val recovered = AdbKeyHelper.getOrCreateKeyPairAndCertificate(context)
        assertArrayEquals(original.second.encoded, recovered.second.encoded)
        assertTrue(bundleFile.exists())
        assertFalse(encryptedKeyFile.exists())
        assertFalse(legacyKey.exists())
        assertFalse(legacyCertificate.exists())
    }

    @Test fun failedInitialWriteLeavesNoIncompleteCommittedIdentityAndCanRetry() {
        clearFiles()
        assertTrue(stagingFile.mkdir()) // Deterministic staging I/O failure before the atomic commit.
        assertThrows(IOException::class.java) { AdbKeyHelper.getOrCreateKeyPairAndCertificate(context) }
        assertFalse(bundleFile.exists())
        assertFalse(encryptedKeyFile.exists())
        assertFalse(currentCertificateFile.exists())

        stagingFile.delete()
        val retried = AdbKeyHelper.getOrCreateKeyPairAndCertificate(context)
        assertTrue(AdbIdentityValidator.isValid(retried.first, retried.second))
    }

    @Test fun failedMigrationWritePreservesOriginalFilesAndCanRetry() {
        val original = newIdentity()
        clearFiles()
        val keyBytes = original.first.encoded
        val certificateBytes = original.second.encoded
        val legacyKey = File(context.filesDir, "adb_private_key.der").apply { writeBytes(keyBytes) }
        val legacyCertificate = File(context.filesDir, "adb_cert.der").apply { writeBytes(certificateBytes) }
        assertTrue(stagingFile.mkdir())

        assertThrows(IOException::class.java) { AdbKeyHelper.getOrCreateKeyPairAndCertificate(context) }
        assertFalse(bundleFile.exists())
        assertArrayEquals(keyBytes, legacyKey.readBytes())
        assertArrayEquals(certificateBytes, legacyCertificate.readBytes())
        stagingFile.delete()
        assertArrayEquals(certificateBytes, AdbKeyHelper.getOrCreateKeyPairAndCertificate(context).second.encoded)
    }

    @Test fun malformedCompleteOldEncryptedPairCannotFallBackToValidPlaintext() {
        val original = newIdentity()
        val payload = encryptedPayload()
        clearFiles()
        encryptedKeyFile.writeBytes(payload)
        currentCertificateFile.writeText("invalid completed certificate")
        val legacyKey = File(context.filesDir, "adb_private_key.der").apply { writeBytes(original.first.encoded) }
        val legacyCertificate = File(context.filesDir, "adb_cert.der").apply { writeBytes(original.second.encoded) }

        assertThrows(Exception::class.java) { AdbKeyHelper.getOrCreateKeyPairAndCertificate(context) }
        assertFalse(bundleFile.exists())
        assertArrayEquals(payload, encryptedKeyFile.readBytes())
        assertEquals("invalid completed certificate", currentCertificateFile.readText())
        assertArrayEquals(original.first.encoded, legacyKey.readBytes())
        assertArrayEquals(original.second.encoded, legacyCertificate.readBytes())
    }

    private val bundleFile get() = File(context.noBackupFilesDir, "adb_identity.enc")
    private val stagingFile get() = File(context.noBackupFilesDir, "adb_identity.enc.tmp")
    private val encryptedKeyFile get() = File(context.noBackupFilesDir, "adb_private_key.enc")
    private val currentCertificateFile get() = File(context.noBackupFilesDir, "adb_cert.der")

    private fun clearFiles() = identityFiles.forEach { file -> file.delete() }

    private fun newIdentity(): Pair<PrivateKey, Certificate> {
        clearFiles()
        return AdbKeyHelper.getOrCreateKeyPairAndCertificate(context)
    }

    private fun encryptedPayload(): ByteArray = DataInputStream(bundleFile.inputStream()).use { input ->
        input.readInt() // Bundle magic.
        val keyLength = input.readInt()
        input.readInt() // Certificate length.
        ByteArray(keyLength).also(input::readFully)
    }
}
