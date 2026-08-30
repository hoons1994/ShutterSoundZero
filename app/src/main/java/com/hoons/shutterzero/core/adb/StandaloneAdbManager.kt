package com.hoons.shutterzero.core.adb

import android.content.Context
import android.util.Log
import com.hoons.shutterzero.core.CscMuteManager
import com.hoons.shutterzero.data.PreferencesRepository
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

    init {
        try {
            java.security.Security.insertProviderAt(org.conscrypt.Conscrypt.newProvider(), 1)
            Log.i(TAG, "Conscrypt security provider registered at position 1")
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to register Conscrypt provider: ${e.message}")
        }
    }

    private val keyPairAndCert by lazy {
        AdbKeyHelper.getOrCreateKeyPairAndCertificate(context)
    }

    override fun getPrivateKey(): PrivateKey = keyPairAndCert.first

    override fun getCertificate(): Certificate = keyPairAndCert.second

    override fun getDeviceName(): String = DEVICE_NAME

    @Volatile
    var lastDiscoveredPairingPort: Int? = null

    @Volatile
    var lastDiscoveredConnectPort: Int? = null

    private var multicastLock: android.net.wifi.WifiManager.MulticastLock? = null

    /**
     * mDNS를 활용하여 활성화된 무선 디버깅 포트를 탐색
     */
    fun startMdnsDiscovery(
        serviceType: String,
        onDiscovered: (InetAddress, Int) -> Unit
    ): AdbMdns {
        acquireMulticastLock()
        val mdns = AdbMdns(context, serviceType) { address, port ->
            if (address != null) {
                Log.i(TAG, "mDNS Discovered: $address:$port for $serviceType")
                if (serviceType == AdbMdns.SERVICE_TYPE_TLS_PAIRING) {
                    lastDiscoveredPairingPort = port
                } else if (serviceType == AdbMdns.SERVICE_TYPE_TLS_CONNECT) {
                    lastDiscoveredConnectPort = port
                }
                onDiscovered(address, port)
            }
        }
        mdns.start()
        return mdns
    }

    private fun acquireMulticastLock() {
        try {
            if (multicastLock == null || !multicastLock!!.isHeld) {
                val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                multicastLock = wifi?.createMulticastLock("ShutterZeroMdns")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
                Log.d(TAG, "Acquired WifiManager MulticastLock for mDNS discovery")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire MulticastLock: ${e.message}")
        }
    }

    fun releaseMulticastLock() {
        try {
            multicastLock?.let {
                if (it.isHeld) it.release()
            }
            multicastLock = null
            Log.d(TAG, "Released WifiManager MulticastLock")
        } catch (_: Exception) {}
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
     * 무선 디버깅 포트로 연결하여 권한 부여 및 셔터음 무음화 명령어 실행
     */
    suspend fun applyCameraMuteViaAdb(connectPort: Int? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            acquireMulticastLock()
            val host = AndroidUtils.getHostIpAddress(context)
            val prefs = PreferencesRepository.getInstance(context)
            Log.i(TAG, "Connecting to ADB daemon at $host...")

            var connected = false

            // 1. 지정된 connectPort가 있다면 직접 연결 시도
            if (connectPort != null && connectPort > 0) {
                try {
                    Log.i(TAG, "Attempting direct connect to port $connectPort")
                    connected = connect(host, connectPort)
                } catch (e: Exception) {
                    Log.w(TAG, "Direct connect to $connectPort failed: ${e.message}")
                }
            }

            // 2. 이미 캐시된 connectPort가 있다면 시도
            val cachedPort = lastDiscoveredConnectPort ?: if (prefs.lastConnectPort > 0) prefs.lastConnectPort else null
            if (!connected && cachedPort != null && cachedPort > 0) {
                try {
                    Log.i(TAG, "Attempting connect to cached port $cachedPort")
                    connected = connect(host, cachedPort)
                } catch (e: Exception) {
                    Log.w(TAG, "Connect to cached port $cachedPort failed: ${e.message}")
                }
            }

            // 3. connectTls (adb-tls-connect mDNS 자동 탐색 및 연결)
            if (!connected) {
                try {
                    Log.i(TAG, "Attempting connectTls with 7s timeout...")
                    connected = connectTls(context, 7000)
                } catch (e: Exception) {
                    Log.w(TAG, "connectTls failed: ${e.message}")
                }
            }

            // 4. connectTcp (adb mDNS 자동 탐색 및 연결)
            if (!connected) {
                try {
                    Log.i(TAG, "Attempting connectTcp with 4s timeout...")
                    connected = connectTcp(context, 4000)
                } catch (e: Exception) {
                    Log.w(TAG, "connectTcp failed: ${e.message}")
                }
            }

            if (!connected && !isConnected) {
                return@withContext Result.failure(Exception("ADB 연결 실패: 무선 디버깅이 활성화되어 있는지 확인해 주세요."))
            }

            saveConnectedPort()
            Log.i(TAG, "ADB session established! Granting WRITE_SECURE_SETTINGS & enabling camera mute...")

            // 1) WRITE_SECURE_SETTINGS 권한 부여 (영구 권한)
            executeShellCommand("pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS")

            // 2) CSC 셔터음 키 무음화 설정 (0) - 권한 부여 시 켜짐 상태 적용
            executeShellCommand("settings put system csc_pref_camera_forced_shuttersound_key 0")

            Log.i(TAG, "WRITE_SECURE_SETTINGS granted & camera mute enabled successfully via standalone ADB!")

            // 3) 설정 저장
            prefs.shouldMuteOnBoot = true

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to grant permission via ADB: ${e.message}", e)
            try { disconnect() } catch (_: Exception) {}
            Result.failure(e)
        }
    }

    /**
     * 무선 디버깅 셸을 통해 CSC 셔터음 키(0 또는 1)를 직접 변경
     */
    suspend fun setCameraMute(enableMute: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            acquireMulticastLock()
            val targetVal = if (enableMute) "0" else "1"
            val host = AndroidUtils.getHostIpAddress(context)
            val prefs = PreferencesRepository.getInstance(context)

            // 1. 이미 연결되어 있는 세션이 있다면 즉시 재사용
            if (isConnected) {
                try {
                    executeShellCommand("settings put system csc_pref_camera_forced_shuttersound_key $targetVal")
                    prefs.shouldMuteOnBoot = enableMute
                    Log.i(TAG, "Reused active ADB session to set CSC key to $targetVal")
                    return@withContext Result.success(Unit)
                } catch (e: Exception) {
                    Log.w(TAG, "Active session failed, will reconnect: ${e.message}")
                    try { disconnect() } catch (_: Exception) {}
                }
            }

            var connected = false
            val savedPort = lastDiscoveredConnectPort ?: if (prefs.lastConnectPort > 0) prefs.lastConnectPort else null

            // 2. 저장된 포트로 초고속 직접 연결 시도
            if (savedPort != null && savedPort > 0) {
                try {
                    Log.i(TAG, "Attempting fast reconnect to saved port $savedPort")
                    connected = connect(host, savedPort)
                    if (connected) saveConnectedPort()
                } catch (e: Exception) {
                    Log.w(TAG, "Fast connect to saved port $savedPort failed: ${e.message}")
                }
            }

            // 3. mDNS 자동 탐색 및 연결 (TLS)
            if (!connected) {
                try {
                    Log.i(TAG, "Attempting connectTls with 4s timeout...")
                    connected = connectTls(context, 4000)
                    if (connected) saveConnectedPort()
                } catch (e: Exception) {
                    Log.w(TAG, "connectTls failed: ${e.message}")
                }
            }

            // 4. mDNS 자동 탐색 및 연결 (TCP)
            if (!connected) {
                try {
                    Log.i(TAG, "Attempting connectTcp with 2.5s timeout...")
                    connected = connectTcp(context, 2500)
                    if (connected) saveConnectedPort()
                } catch (e: Exception) {
                    Log.w(TAG, "connectTcp failed: ${e.message}")
                }
            }

            if (!connected && !isConnected) {
                return@withContext Result.failure(Exception("무선 디버깅에 연결할 수 없습니다."))
            }

            executeShellCommand("settings put system csc_pref_camera_forced_shuttersound_key $targetVal")
            prefs.shouldMuteOnBoot = enableMute
            Log.i(TAG, "Successfully set csc_pref_camera_forced_shuttersound_key to $targetVal via ADB")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set camera mute via ADB: ${e.message}", e)
            try { disconnect() } catch (_: Exception) {}
            Result.failure(e)
        }
    }

    private fun saveConnectedPort() {
        try {
            val adbConn = adbConnection ?: return
            val portField = adbConn.javaClass.getDeclaredField("mPort").apply { isAccessible = true }
            val port = portField.getInt(adbConn)
            if (port > 0) {
                lastDiscoveredConnectPort = port
                PreferencesRepository.getInstance(context).lastConnectPort = port
                Log.i(TAG, "Saved active ADB connect port: $port")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not extract connect port: ${e.message}")
        }
    }

    private fun executeShellCommand(cmd: String) {
        try {
            openStream("shell:$cmd\n").use { stream ->
                val buffer = ByteArray(1024)
                try {
                    while (stream.read(buffer, 0, buffer.size) > 0) {}
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.w(TAG, "Shell command '$cmd' execution note: ${e.message}")
        }
    }
}
