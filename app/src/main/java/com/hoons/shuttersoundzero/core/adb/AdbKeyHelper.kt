package com.hoons.shuttersoundzero.core.adb

import android.content.Context
import android.util.Log
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
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
 * 2048비트 RSA 키페어와 X.509 인증서를 생성하고 영구 보관하는 헬퍼
 */
object AdbKeyHelper {
    private const val TAG = "AdbKeyHelper"
    private const val PRIV_KEY_FILE = "adb_private_key.der"
    private const val CERT_FILE = "adb_cert.der"

    @Synchronized
    fun getOrCreateKeyPairAndCertificate(context: Context): Pair<PrivateKey, Certificate> {
        val privFile = File(context.filesDir, PRIV_KEY_FILE)
        val certFile = File(context.filesDir, CERT_FILE)

        if (privFile.exists() && certFile.exists()) {
            try {
                val privBytes = privFile.readBytes()
                val keySpec = PKCS8EncodedKeySpec(privBytes)
                val keyFactory = KeyFactory.getInstance("RSA")
                val privateKey = keyFactory.generatePrivate(keySpec)

                val certFactory = CertificateFactory.getInstance("X.509")
                val cert = certFile.inputStream().use { certFactory.generateCertificate(it) }
                return Pair(privateKey, cert)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load existing keys, regenerating: ${e.message}")
            }
        }

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

        // 내부 스토리지에 안전하게 영구 저장
        try {
            privFile.writeBytes(keyPair.private.encoded)
            certFile.writeBytes(cert.encoded)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist keys: ${e.message}")
        }

        return Pair(keyPair.private, cert)
    }
}
