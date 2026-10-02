package io.github.hoons1994.shuttersoundzero.core.adb

import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.util.Date
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AdbIdentityLoaderTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val now = 1_800_000_000_000L

    @Test fun emptyStoreAllowsFirstIdentityCreation() {
        assertNull(AdbIdentityLoader.load(file("key"), file("certificate"), { it }, now))
    }

    @Test fun incompleteIdentityFailsWithoutDiscardingRemainingFile() {
        val privateKey = temporaryFolder.newFile("key").apply { writeText("existing identity") }
        val original = privateKey.readBytes()
        assertThrows(IOException::class.java) {
            AdbIdentityLoader.load(privateKey, file("certificate"), { it }, now)
        }
        assertArrayEquals(original, privateKey.readBytes())
    }

    @Test fun temporarilyUnavailableKeystorePreservesBothFilesForRetry() {
        val (privateKey, certificate) = createIdentity()
        val originalKey = privateKey.readBytes()
        val originalCertificate = certificate.readBytes()
        assertThrows(IOException::class.java) {
            AdbIdentityLoader.load(privateKey, certificate, { throw IOException("temporarily unavailable") }, now)
        }
        assertArrayEquals(originalKey, privateKey.readBytes())
        assertArrayEquals(originalCertificate, certificate.readBytes())
        assertNotNull(AdbIdentityLoader.load(privateKey, certificate, { it }, now))
    }

    @Test fun incorrectClockPreservesIdentityAndCorrectingClockRestoresIt() {
        val (privateKey, certificate) = createIdentity()
        val originalKey = privateKey.readBytes()
        val originalCertificate = certificate.readBytes()
        for (incorrectTime in listOf(now - 120_000, now + 120_000)) {
            assertThrows(IOException::class.java) {
                AdbIdentityLoader.load(privateKey, certificate, { it }, incorrectTime)
            }
        }
        assertArrayEquals(originalKey, privateKey.readBytes())
        assertArrayEquals(originalCertificate, certificate.readBytes())
        assertNotNull(AdbIdentityLoader.load(privateKey, certificate, { it }, now))
    }

    @Test fun malformedCertificateFailsClosedWithoutReplacingIdentity() {
        val (privateKey, certificate) = createIdentity()
        val originalKey = privateKey.readBytes()
        certificate.writeText("invalid certificate")
        assertThrows(Exception::class.java) {
            AdbIdentityLoader.load(privateKey, certificate, { it }, now)
        }
        assertArrayEquals(originalKey, privateKey.readBytes())
        assertEquals("invalid certificate", certificate.readText())
    }

    @Test fun decodedPrivateKeyBufferIsClearedAfterUse() {
        val (privateKey, certificate) = createIdentity()
        var decoded: ByteArray? = null
        assertNotNull(AdbIdentityLoader.load(privateKey, certificate, { bytes -> bytes.also { decoded = it } }, now))
        assertTrue(decoded!!.all { it == 0.toByte() })
    }

    @Test fun decodedPrivateKeyBufferIsClearedEvenWhenParsingFails() {
        val (privateKey, certificate) = createIdentity()
        val decoded = byteArrayOf(1, 2, 3)
        assertThrows(Exception::class.java) {
            AdbIdentityLoader.load(privateKey, certificate, { decoded }, now)
        }
        assertTrue(decoded.all { it == 0.toByte() })
    }

    @Test fun interruptedOldMigrationRestoresOnlyTheMatchingOriginalCertificate() {
        val (encryptedKey, certificate) = createIdentity()
        val legacyKey = temporaryFolder.newFile("legacy-key").apply { writeBytes(encryptedKey.readBytes()) }
        val legacyCertificate = temporaryFolder.newFile("legacy-certificate").apply { writeBytes(certificate.readBytes()) }
        val originalKey = encryptedKey.readBytes()
        val originalCertificate = legacyCertificate.readBytes()
        assertTrue(certificate.delete())

        val recovered = AdbIdentityLoader.loadEncryptedForMigration(
            encryptedKey, certificate, listOf(legacyKey to legacyCertificate), { it }, now
        )!!
        assertArrayEquals(originalCertificate, recovered.second.encoded)
        assertArrayEquals(originalKey, encryptedKey.readBytes())
        assertArrayEquals(originalKey, legacyKey.readBytes())
        assertArrayEquals(originalCertificate, legacyCertificate.readBytes())
        assertFalse(certificate.exists()) // Persistence happens only when the complete bundle commits.
    }

    @Test fun incompleteEncryptedCopyCannotBorrowAnUnrelatedLegacyCertificate() {
        val (encryptedKey, certificate) = createIdentity("encrypted-")
        val (legacyKey, legacyCertificate) = createIdentity("legacy-")
        assertTrue(certificate.delete())
        val original = encryptedKey.readBytes()
        assertThrows(IOException::class.java) {
            AdbIdentityLoader.loadEncryptedForMigration(
                encryptedKey, certificate, listOf(legacyKey to legacyCertificate), { it }, now
            )
        }
        assertArrayEquals(original, encryptedKey.readBytes())
        assertTrue(legacyKey.exists())
        assertTrue(legacyCertificate.exists())
    }

    @Test fun completeEncryptedPairValidationFailureDoesNotFallBackToLegacy() {
        val (encryptedKey, certificate) = createIdentity()
        val legacyKey = temporaryFolder.newFile("legacy-key").apply { writeBytes(encryptedKey.readBytes()) }
        val legacyCertificate = temporaryFolder.newFile("legacy-certificate").apply { writeBytes(certificate.readBytes()) }
        certificate.writeText("damaged current certificate")
        assertThrows(Exception::class.java) {
            AdbIdentityLoader.loadEncryptedForMigration(
                encryptedKey, certificate, listOf(legacyKey to legacyCertificate), { it }, now
            )
        }
        assertEquals("damaged current certificate", certificate.readText())
        assertTrue(legacyKey.exists())
        assertTrue(legacyCertificate.exists())
    }

    @Test fun completeEncryptedPairClockOrKeystoreFailureDoesNotFallBackToLegacy() {
        val (encryptedKey, certificate) = createIdentity()
        val legacyKey = temporaryFolder.newFile("legacy-key").apply { writeBytes(encryptedKey.readBytes()) }
        val legacyCertificate = temporaryFolder.newFile("legacy-certificate").apply { writeBytes(certificate.readBytes()) }
        val originalKey = encryptedKey.readBytes()
        val originalCertificate = certificate.readBytes()
        var decodes = 0
        assertThrows(IOException::class.java) {
            AdbIdentityLoader.loadEncryptedForMigration(
                encryptedKey, certificate, listOf(legacyKey to legacyCertificate), {
                    decodes++
                    throw IOException("Keystore unavailable")
                }, now
            )
        }
        assertEquals(1, decodes)
        assertThrows(IOException::class.java) {
            AdbIdentityLoader.loadEncryptedForMigration(
                encryptedKey, certificate, listOf(legacyKey to legacyCertificate), { it }, now + 120_000
            )
        }
        assertArrayEquals(originalKey, encryptedKey.readBytes())
        assertArrayEquals(originalCertificate, certificate.readBytes())
    }

    private fun file(name: String) = File(temporaryFolder.root, name)

    private fun createIdentity(prefix: String = ""): Pair<File, File> {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val subject = X500Name("CN=ShutterSoundZero-Test")
        val builder = X509v3CertificateBuilder(
            subject, BigInteger.ONE, Date(now - 60_000), Date(now + 60_000), subject,
            SubjectPublicKeyInfo.getInstance(keyPair.public.encoded)
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val certificate = JcaX509CertificateConverter().getCertificate(builder.build(signer))
        return file("${prefix}key").apply { writeBytes(keyPair.private.encoded) } to
            file("${prefix}certificate").apply { writeBytes(certificate.encoded) }
    }
}
