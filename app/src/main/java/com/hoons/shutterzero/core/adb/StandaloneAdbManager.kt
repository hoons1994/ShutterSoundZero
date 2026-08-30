package com.hoons.shutterzero.core.adb

import android.content.Context
import android.util.Log
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.android.AdbMdns
import io.github.muntashirakon.adb.android.AndroidUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.security.PrivateKey
import java.security.cert.Certificate

/**
 * 셔터 제로 자체 무선 디버깅(On-Device Wireless ADB) 매니저
 * PC나 외부 앱 없이 앱 단독으로 로컬 adbd와 TLS 페어링 및 셸 명령어 실행
 */
class StandaloneAdbManager(private val context: Context) : AbsAdbConnectionManager() {
    companion object {
        private const val TAG = "StandaloneAdbManager"
        private const val DEVICE_NAME = "ShutterZero"

        @Volatile
        private var INSTANCE: StandaloneAdbManager? = null

        fun getInstance(context: Context): StandaloneAdbManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: StandaloneAdbManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val keyPairAndCert by lazy {
        AdbKeyHelper.getOrCreateKeyPairAndCertificate(context)
    }

    override fun getPrivateKey(): PrivateKey = keyPairAndCert.first

    override fun getCertificate(): Certificate = keyPairAndCert.second

    override fun getDeviceName(): String = DEVICE_NAME

    /**
     * mDNS를 활용하여 활성화된 무선 디버깅 포트를 탐색
     */
    fun startMdnsDiscovery(
        serviceType: String,
        onDiscovered: (InetAddress, Int) -> Unit
    ): AdbMdns {
        val mdns = AdbMdns(context, serviceType) { address, port ->
            if (address != null) {
                Log.i(TAG, "mDNS Discovered: $address:$port for $serviceType")
                onDiscovered(address, port)
            }
        }
        mdns.start()
        return mdns
    }

    /**
     * 6자리 페어링 코드로 로컬 기기와 페어링 수행
     */
    suspend fun pairLocal(port: Int, pairingCode: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val host = AndroidUtils.getHostIpAddress(context)
            Log.i(TAG, "Attempting pairing with $host:$port using code $pairingCode")
            val success = pair(host, port, pairingCode)
            if (success) {
                Log.i(TAG, "Pairing successful!")
                Result.success(Unit)
            } else {
                Result.failure(Exception("페어링에 실패했습니다. 페어링 코드를 다시 확인해 주세요."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pairing error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 무선 디버깅 포트로 연결하여 셔터음 무음화 명령어 실행
     */
    suspend fun applyCameraMuteViaAdb(connectPort: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val host = AndroidUtils.getHostIpAddress(context)
            Log.i(TAG, "Connecting to ADB daemon at $host:$connectPort")

            val isConnected = connect(host, connectPort)
            if (!isConnected) {
                return@withContext Result.failure(Exception("ADB 연결에 실패했습니다. 포트를 확인해 주세요."))
            }

            // CSC 무음화 명령 실행: settings put system csc_pref_camera_forced_shuttersound_key 0
            val command = "settings put system csc_pref_camera_forced_shuttersound_key 0\n"
            openStream("shell:$command").use { stream ->
                try {
                    stream.openOutputStream().use { out ->
                        out.write(command.toByteArray(Charsets.UTF_8))
                        out.flush()
                    }
                } catch (_: Exception) {}
                Thread.sleep(300)
            }

            disconnect()
            Log.i(TAG, "CSC Mute command executed successfully via standalone ADB!")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply CSC mute via ADB: ${e.message}", e)
            try { disconnect() } catch (_: Exception) {}
            Result.failure(e)
        }
    }
}
