package io.github.hoons1994.shuttersoundzero.core.adb

import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.nio.file.AtomicMoveNotSupportedException
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

class AdbIdentityBundleStoreTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val now = 1_800_000_000_000L
    private val bundle get() = File(temporaryFolder.root, "adb_identity.enc")
    private val staging get() = File(temporaryFolder.root, "adb_identity.enc.tmp")
    private val decode: (ByteArray) -> ByteArray = { it.clone() }

    @Test fun interruptedFirstWriteCanRetryWithoutLoadingPartialStagingData() {
        staging.writeBytes(byteArrayOf(0x53, 0x53, 0x5a))
        val store = AdbIdentityBundleStore(bundle)
        assertNull(store.load(decode, now))

        val (key, certificate) = createIdentity()
        store.write(key, certificate)
        assertArrayEquals(certificate, store.load(decode, now)!!.second.encoded)
        assertFalse(staging.exists())
    }

    @Test fun interruptedStagingDoesNotReplaceAnAlreadyCommittedIdentity() {
        val (key, certificate) = createIdentity()
        val store = AdbIdentityBundleStore(bundle)
        store.write(key, certificate)
        val committed = bundle.readBytes()
        staging.writeText("unfinished next identity")

        assertArrayEquals(certificate, store.load(decode, now)!!.second.encoded)
        assertArrayEquals(committed, bundle.readBytes())
        assertFalse(staging.exists())
        assertThrows(IOException::class.java) { store.write(key, certificate) }
        assertArrayEquals(committed, bundle.readBytes())
    }

    @Test fun unsupportedAtomicCommitPreservesMigrationSourcesAndAllowsRetry() {
        val (key, certificate) = createIdentity()
        val legacyKey = temporaryFolder.newFile("legacy-key").apply { writeBytes(key) }
        val legacyCertificate = temporaryFolder.newFile("legacy-certificate").apply { writeBytes(certificate) }
        var commits = 0
        val failingStore = AdbIdentityBundleStore(bundle, commit = { source, target ->
            commits++
            throw AtomicMoveNotSupportedException(source.path, target.path, "test filesystem")
        })
        assertThrows(AtomicMoveNotSupportedException::class.java) { failingStore.write(key, certificate) }
        assertEquals(1, commits)
        assertFalse(bundle.exists())
        assertFalse(staging.exists())
        assertArrayEquals(key, legacyKey.readBytes())
        assertArrayEquals(certificate, legacyCertificate.readBytes())

        val store = AdbIdentityBundleStore(bundle)
        store.write(key, certificate)
        assertArrayEquals(certificate, store.load(decode, now)!!.second.encoded)
    }

    @Test fun failureAfterAtomicCommitNeverDeletesTheCommittedBundle() {
        val (key, certificate) = createIdentity()
        val failingStore = AdbIdentityBundleStore(bundle, syncDirectory = { throw IOException("directory sync failed") })
        assertThrows(IOException::class.java) { failingStore.write(key, certificate) }
        assertTrue(bundle.exists())
        val committed = bundle.readBytes()

        assertThrows(IOException::class.java) { failingStore.load(decode, now) }
        assertArrayEquals(committed, bundle.readBytes())
        assertArrayEquals(certificate, AdbIdentityBundleStore(bundle).load(decode, now)!!.second.encoded)
    }

    @Test fun unavailableKeystoreOrIncorrectClockPreservesCommittedIdentityForRetry() {
        val (key, certificate) = createIdentity()
        val store = AdbIdentityBundleStore(bundle)
        store.write(key, certificate)
        val committed = bundle.readBytes()
        assertThrows(IOException::class.java) {
            store.load({ throw IOException("Keystore unavailable") }, now)
        }
        assertThrows(IOException::class.java) { store.load(decode, now + 120_000) }
        assertArrayEquals(committed, bundle.readBytes())
        assertArrayEquals(certificate, store.load(decode, now)!!.second.encoded)
    }

    @Test fun malformedCommittedBundleFailsWithoutReadingStagingAsFallback() {
        bundle.writeText("invalid completed identity")
        staging.writeText("staged bytes must not become authoritative")
        assertThrows(IOException::class.java) { AdbIdentityBundleStore(bundle).load(decode, now) }
        assertEquals("invalid completed identity", bundle.readText())
        assertEquals("staged bytes must not become authoritative", staging.readText())
    }

    private fun createIdentity(): Pair<ByteArray, ByteArray> {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val subject = X500Name("CN=ShutterSoundZero-Bundle-Test")
        val builder = X509v3CertificateBuilder(
            subject, BigInteger.ONE, Date(now - 60_000), Date(now + 60_000), subject,
            SubjectPublicKeyInfo.getInstance(keyPair.public.encoded)
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        return keyPair.private.encoded to JcaX509CertificateConverter().getCertificate(builder.build(signer)).encoded
    }
}
