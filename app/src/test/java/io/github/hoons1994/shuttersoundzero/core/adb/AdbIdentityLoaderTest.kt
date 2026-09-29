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

    private fun file(name: String) = File(temporaryFolder.root, name)

    private fun createIdentity(): Pair<File, File> {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val subject = X500Name("CN=ShutterSoundZero-Test")
        val builder = X509v3CertificateBuilder(
            subject, BigInteger.ONE, Date(now - 60_000), Date(now + 60_000), subject,
            SubjectPublicKeyInfo.getInstance(keyPair.public.encoded)
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val certificate = JcaX509CertificateConverter().getCertificate(builder.build(signer))
        return file("key").apply { writeBytes(keyPair.private.encoded) } to
            file("certificate").apply { writeBytes(certificate.encoded) }
    }
}
