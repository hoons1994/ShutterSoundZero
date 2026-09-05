package com.charmingcolor.shuttersoundzero.core.adb

import android.content.Context
import android.util.Log
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date

/**
 * 자체 무선 디버깅(On-Device ADB) 페어링 및 TLS 연결에 필요한
 * 2048비트 RSA 키페어와 X.509 인증서를 생성하고 기기 로컬에 영구 보관하는 헬퍼.
 *
 * ADB identity는 다른 기기로 복원되면 안 되므로 Android 자동 백업 대상이 아닌
 * noBackupFilesDir에 저장한다.
 */
object AdbKeyHelper {
    private const val TAG = "AdbKeyHelper"
    private const val PRIV_KEY_FILE = "adb_private_key.der"
    private const val CERT_FILE = "adb_cert.der"

    @Synchronized
    fun getOrCreateKeyPairAndCertificate(context: Context): Pair<PrivateKey, Certificate> {
        val privFile = File(context.noBackupFilesDir, PRIV_KEY_FILE)
        val certFile = File(context.noBackupFilesDir, CERT_FILE)

        migrateLegacyIdentityIfNeeded(context, privFile, certFile)

        loadIdentity(privFile, certFile)?.let { return it }

        // 손상되거나 한쪽만 남은 identity는 새로 생성한다.
        privFile.delete()
        certFile.delete()

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
            privFile.writeBytes(keyPair.private.encoded)
            certFile.writeBytes(cert.encoded)

            loadIdentity(privFile, certFile)
                ?: throw IOException("Persisted ADB identity could not be validated")
        } catch (e: Exception) {
            privFile.delete()
            certFile.delete()
            Log.e(TAG, "Failed to persist a valid ADB identity: ${e.message}", e)
            throw IOException("Failed to persist a valid ADB identity", e)
        }
    }

    private fun loadIdentity(privFile: File, certFile: File): Pair<PrivateKey, Certificate>? {
        if (!privFile.exists() || !certFile.exists()) return null

        return try {
            val keySpec = PKCS8EncodedKeySpec(privFile.readBytes())
            val privateKey = KeyFactory.getInstance("RSA").generatePrivate(keySpec)
            val cert = certFile.inputStream().use {
                CertificateFactory.getInstance("X.509").generateCertificate(it)
            }

            if (!AdbIdentityValidator.isValid(privateKey, cert)) {
                Log.w(TAG, "Persisted ADB identity failed cryptographic validation")
                return null
            }

            Pair(privateKey, cert)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load existing ADB identity: ${e.message}")
            null
        }
    }

    /**
     * 1.2.0 이하에서 filesDir에 저장하던 identity를 noBackupFilesDir로 1회 이전한다.
     * 이전이 검증된 뒤에만 legacy 파일을 삭제하여 기존 페어링을 최대한 보존한다.
     */
    private fun migrateLegacyIdentityIfNeeded(
        context: Context,
        targetPrivFile: File,
        targetCertFile: File
    ) {
        val legacyPrivFile = File(context.filesDir, PRIV_KEY_FILE)
        val legacyCertFile = File(context.filesDir, CERT_FILE)

        if (loadIdentity(targetPrivFile, targetCertFile) != null) {
            legacyPrivFile.delete()
            legacyCertFile.delete()
            return
        }

        // 불완전한 새 위치 파일은 제거한 뒤 legacy identity 이전을 다시 시도한다.
        targetPrivFile.delete()
        targetCertFile.delete()

        if (!legacyPrivFile.exists() || !legacyCertFile.exists()) return

        try {
            legacyPrivFile.copyTo(targetPrivFile, overwrite = false)
            legacyCertFile.copyTo(targetCertFile, overwrite = false)

            if (loadIdentity(targetPrivFile, targetCertFile) == null) {
                throw IOException("Migrated ADB identity could not be validated")
            }

            legacyPrivFile.delete()
            legacyCertFile.delete()
            Log.i(TAG, "Migrated ADB identity to no-backup storage")
        } catch (e: Exception) {
            targetPrivFile.delete()
            targetCertFile.delete()
            Log.w(TAG, "Unable to migrate legacy ADB identity: ${e.message}")
        }
    }
}
