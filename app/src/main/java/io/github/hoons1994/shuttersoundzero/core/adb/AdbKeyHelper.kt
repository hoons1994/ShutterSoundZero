package io.github.hoons1994.shuttersoundzero.core.adb

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.file.Files
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import java.util.Date
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 자체 무선 디버깅(On-Device ADB) 페어링 및 TLS 연결에 필요한
 * 2048비트 RSA 키페어와 X.509 인증서를 생성하고 기기 로컬에 영구 보관하는 헬퍼.
 *
 * ADB identity는 다른 기기로 복원되면 안 되므로 Android 자동 백업 대상이 아닌
 * noBackupFilesDir에 저장한다. RSA 개인키는 Android Keystore의 비추출 AES 키로
 * AES-GCM 암호화해 디스크에 평문 PKCS#8이 남지 않도록 한다.
 */
object AdbKeyHelper {
    private const val TAG = "AdbKeyHelper"
    private const val ENCRYPTED_PRIV_KEY_FILE = "adb_private_key.enc"
    private const val LEGACY_PRIV_KEY_FILE = "adb_private_key.der"
    private const val CERT_FILE = "adb_cert.der"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val WRAPPING_KEY_ALIAS = "shuttersoundzero_adb_identity_wrap_v1"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val ENCRYPTED_KEY_MAGIC = 0x53535A31 // "SSZ1"
    private const val MAX_GCM_IV_BYTES = 32

    /**
     * 소프트웨어 업데이트 뒤 기존 연동 흔적은 있는데 권한이 사라진 예외 상황에서만
     * 로컬 ADB identity 파일을 폐기한다. Android Keystore wrapping key 자체는 재사용해
     * 불필요한 키스토어 churn 없이 다음 페어링에서 새 RSA identity를 만들게 한다.
     */
    @Synchronized
    fun resetIdentity(context: Context) {
        val files = listOf(
            File(context.noBackupFilesDir, ENCRYPTED_PRIV_KEY_FILE),
            File(context.noBackupFilesDir, "$ENCRYPTED_PRIV_KEY_FILE.tmp"),
            File(context.noBackupFilesDir, LEGACY_PRIV_KEY_FILE),
            File(context.noBackupFilesDir, CERT_FILE),
            File(context.filesDir, LEGACY_PRIV_KEY_FILE),
            File(context.filesDir, CERT_FILE)
        )

        val failed = files.filter { file -> file.exists() && !file.delete() }
        if (failed.isNotEmpty()) {
            throw IOException("Unable to clear persisted ADB identity files")
        }

        Log.i(TAG, "Cleared persisted ADB identity for fresh pairing")
    }

    @Synchronized
    fun getOrCreateKeyPairAndCertificate(context: Context): Pair<PrivateKey, Certificate> {
        val privFile = File(context.noBackupFilesDir, ENCRYPTED_PRIV_KEY_FILE)
        val certFile = File(context.noBackupFilesDir, CERT_FILE)

        // An unavailable Keystore or an incorrect clock must not destroy an existing pairing.
        if (!Files.notExists(privFile.toPath())) {
            val identity = loadIdentity(privFile, certFile)
                ?: throw IOException("Existing ADB identity could not be loaded")
            deleteLegacyIdentityFiles(context)
            return identity
        }

        migrateLegacyIdentityIfNeeded(context, privFile, certFile)

        loadIdentity(privFile, certFile)?.let { return it }

        // loadIdentity returns null only when neither identity file exists.

        // 2048비트 RSA 키페어 생성
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048)
        val keyPair: KeyPair = keyGen.generateKeyPair()

        // 자체 서명 X.509 인증서 생성 (25년 유효)
        val now = System.currentTimeMillis()
        val startDate = Date(now - 24 * 60 * 60 * 1000L)
        val endDate = Date(now + 25L * 365 * 24 * 60 * 60 * 1000L)
        val subject = X500Name("CN=ShutterSoundZero")
        val subPubKeyInfo = SubjectPublicKeyInfo.getInstance(keyPair.public.encoded)

        val certBuilder = X509v3CertificateBuilder(
            subject,
            BigInteger.valueOf(now),
            startDate,
            endDate,
            subject,
            subPubKeyInfo
        )

        val signer = JcaContentSignerBuilder("SHA256WithRSAEncryption").build(keyPair.private)
        val certHolder = certBuilder.build(signer)
        val cert = JcaX509CertificateConverter().getCertificate(certHolder)

        return try {
            val encodedKey = keyPair.private.encoded
            try {
                writeEncryptedPrivateKey(privFile, encodedKey)
            } finally {
                encodedKey.fill(0)
            }
            certFile.writeBytes(cert.encoded)

            loadIdentity(privFile, certFile)
                ?: throw IOException("Persisted ADB identity could not be validated")
        } catch (e: Exception) {
            privFile.delete()
            certFile.delete()
            Log.e(TAG, "Failed to persist a valid encrypted ADB identity: ${e.message}", e)
            throw IOException("Failed to persist a valid encrypted ADB identity", e)
        }
    }

    private fun loadIdentity(privFile: File, certFile: File): Pair<PrivateKey, Certificate>? {
        return try {
            AdbIdentityLoader.load(privFile, certFile, ::decryptPrivateKey)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load existing encrypted ADB identity: ${e.javaClass.simpleName}")
            throw IOException("Unable to load ADB identity; existing pairing files have been preserved", e)
        }
    }

    private fun loadLegacyIdentity(privFile: File, certFile: File): Pair<PrivateKey, Certificate>? {
        return AdbIdentityLoader.load(privFile, certFile, { it })
    }

    private fun writeEncryptedPrivateKey(target: File, privateKeyBytes: ByteArray) {
        val wrappingKey = getOrCreateWrappingKey()
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey)
        val encrypted = cipher.doFinal(privateKeyBytes)
        val iv = cipher.iv ?: throw IOException("AES-GCM IV was not generated")

        if (iv.isEmpty() || iv.size > MAX_GCM_IV_BYTES) {
            throw IOException("Unexpected AES-GCM IV length")
        }

        val payload = ByteBuffer.allocate(Int.SIZE_BYTES * 2 + iv.size + encrypted.size)
            .putInt(ENCRYPTED_KEY_MAGIC)
            .putInt(iv.size)
            .put(iv)
            .put(encrypted)
            .array()

        val tempFile = File(target.parentFile, "${target.name}.tmp")
        try {
            tempFile.writeBytes(payload)
            if (target.exists() && !target.delete()) {
                throw IOException("Could not replace encrypted ADB private key")
            }
            if (!tempFile.renameTo(target)) {
                throw IOException("Could not finalize encrypted ADB private key")
            }
        } finally {
            tempFile.delete()
        }
    }

    private fun decryptPrivateKey(payload: ByteArray): ByteArray {
        if (payload.size <= Int.SIZE_BYTES * 2) {
            throw IOException("Encrypted ADB private key payload is too short")
        }

        val buffer = ByteBuffer.wrap(payload)
        if (buffer.int != ENCRYPTED_KEY_MAGIC) {
            throw IOException("Encrypted ADB private key format is not recognized")
        }

        val ivLength = buffer.int
        if (ivLength <= 0 || ivLength > MAX_GCM_IV_BYTES || buffer.remaining() <= ivLength) {
            throw IOException("Encrypted ADB private key IV is invalid")
        }

        val iv = ByteArray(ivLength).also { buffer.get(it) }
        val encrypted = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getWrappingKey() ?: throw IOException("ADB identity wrapping key is unavailable"),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        )
        return cipher.doFinal(encrypted)
    }

    private fun getOrCreateWrappingKey(): SecretKey {
        getWrappingKey()?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                WRAPPING_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private fun getWrappingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        return keyStore.getKey(WRAPPING_KEY_ALIAS, null) as? SecretKey
    }

    /**
     * 이전 버전의 평문 PKCS#8 개인키를 암호화 저장 형식으로 1회 이전한다.
     *
     * - 현재 noBackupFilesDir의 평문 identity를 먼저 이전한다.
     * - 1.2.0 이하에서 filesDir에 저장하던 identity도 이어서 지원한다.
     * - 암호화된 identity의 검증이 끝난 뒤에만 원본 평문 파일을 삭제한다.
     */
    private fun migrateLegacyIdentityIfNeeded(
        context: Context,
        targetPrivFile: File,
        targetCertFile: File
    ) {
        val candidates = listOf(
            File(context.noBackupFilesDir, LEGACY_PRIV_KEY_FILE) to targetCertFile,
            File(context.filesDir, LEGACY_PRIV_KEY_FILE) to File(context.filesDir, CERT_FILE)
        )

        for ((legacyPrivFile, legacyCertFile) in candidates) {
            if (Files.notExists(legacyPrivFile.toPath())) continue
            val legacyIdentity = loadLegacyIdentity(legacyPrivFile, legacyCertFile) ?: continue
            val previousCertificate = targetCertFile.takeIf { it.exists() }?.readBytes()

            try {
                val encodedKey = legacyIdentity.first.encoded
                try {
                    writeEncryptedPrivateKey(targetPrivFile, encodedKey)
                } finally {
                    encodedKey.fill(0)
                }
                if (legacyCertFile.absolutePath != targetCertFile.absolutePath) {
                    legacyCertFile.copyTo(targetCertFile, overwrite = true)
                }

                if (loadIdentity(targetPrivFile, targetCertFile) == null) {
                    throw IOException("Migrated encrypted ADB identity could not be validated")
                }

                legacyPrivFile.delete()
                if (legacyCertFile.absolutePath != targetCertFile.absolutePath) {
                    legacyCertFile.delete()
                }
                deleteLegacyIdentityFiles(context)
                Log.i(TAG, "Migrated ADB identity to Android Keystore encrypted storage")
                return
            } catch (e: Exception) {
                // Only the encrypted copy created by this migration is disposable.
                targetPrivFile.delete()
                if (legacyCertFile.absolutePath != targetCertFile.absolutePath) {
                    try {
                        if (previousCertificate != null) targetCertFile.writeBytes(previousCertificate)
                        else targetCertFile.delete()
                    } catch (restoreError: Exception) {
                        e.addSuppressed(restoreError)
                    }
                }
                Log.w(TAG, "Unable to migrate legacy ADB identity: ${e.javaClass.simpleName}")
                throw IOException("Unable to migrate ADB identity; legacy pairing files have been preserved", e)
            }
        }
    }

    private fun deleteLegacyIdentityFiles(context: Context) {
        File(context.noBackupFilesDir, LEGACY_PRIV_KEY_FILE).delete()
        File(context.filesDir, LEGACY_PRIV_KEY_FILE).delete()
        File(context.filesDir, CERT_FILE).delete()
    }
}
