package com.charmingcolor.shuttersoundzero.core.adb

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log
import com.charmingcolor.shuttersoundzero.data.PreferencesRepository
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.android.AdbMdns
import io.github.muntashirakon.adb.android.AndroidUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.security.PrivateKey
import java.security.cert.Certificate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * 셔터음 제로 자체 무선 디버깅(On-Device Wireless ADB) 매니저
 * PC나 외부 앱 없이 앱 단독으로 로컬 adbd와 TLS 페어링 및 셸 명령어 실행
 */
class StandaloneAdbManager(context: Context) : AbsAdbConnectionManager() {
    private val context = context.applicationContext

    companion object {
        private const val TAG = "StandaloneAdbManager"
        private const val DEVICE_NAME = "ShutterSoundZero"
        private const val SHELL_COMMAND_TIMEOUT_MS = 5_000L
        private const val SHELL_COMMAND_POLL_INTERVAL_MS = 20L
        private const val MAX_SHELL_OUTPUT_BYTES = 64 * 1024

        private val shellCommandSequence = AtomicLong()

        @SuppressLint("StaticFieldLeak") // The manager stores only applicationContext.
        @Volatile
        private var INSTANCE: StandaloneAdbManager? = null

        fun getInstance(context: Context): StandaloneAdbManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: StandaloneAdbManager(context).also { INSTANCE = it }
            }
        }
    }

    private val isDebuggable: Boolean
        get() = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private inline fun logSensitive(message: () -> String) {
        if (isDebuggable) Log.d(TAG, message())
    }

    private fun logFailure(summary: String, error: Throwable) {
        if (isDebuggable) {
            Log.w(TAG, "$summary: ${error.message}", error)
        } else {
            Log.w(TAG, "$summary (${error.javaClass.simpleName})")
        }
    }

    init {
        try {
            java.security.Security.insertProviderAt(org.conscrypt.Conscrypt.newProvider(), 1)
            Log.i(TAG, "Conscrypt security provider registered at position 1")
        } catch (e: Throwable) {
            logFailure("Failed to register Conscrypt provider", e)
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
    private var pairingMdns: AdbMdns? = null
    private var pairingConnectMdns: AdbMdns? = null

    @Volatile
    private var isPairingDiscoveryActive = false

    /** 사용자가 명시적으로 시작한 페어링 세션의 mDNS 탐색을 시작한다. */
    @Synchronized
    fun startPairingDiscovery(
        onPairingPortDiscovered: (Int) -> Unit,
        onConnectPortDiscovered: (Int) -> Unit
    ) {
        stopPairingDiscovery()
        isPairingDiscoveryActive = true

        try {
            pairingMdns = startMdnsDiscovery(AdbMdns.SERVICE_TYPE_TLS_PAIRING) { _, port ->
                if (isPairingDiscoveryActive) onPairingPortDiscovered(port)
            }
            pairingConnectMdns = startMdnsDiscovery(AdbMdns.SERVICE_TYPE_TLS_CONNECT) { _, port ->
                if (isPairingDiscoveryActive) onConnectPortDiscovered(port)
            }
        } catch (e: Exception) {
            stopPairingDiscovery()
            throw e
        }
    }

    /** 페어링 취소·완료 시 관련 탐색과 멀티캐스트 잠금을 즉시 해제한다. */
    @Synchronized
    fun stopPairingDiscovery() {
        isPairingDiscoveryActive = false
        try {
            pairingMdns?.stop()
            pairingConnectMdns?.stop()
        } catch (e: Exception) {
            logFailure("Failed to stop pairing discovery", e)
        }
        pairingMdns = null
        pairingConnectMdns = null
        releaseMulticastLock()
    }

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
                if (LocalAdbEndpointPolicy.isLocalDeviceAddress(address) && port in 1..65535) {
                    Log.i(TAG, "Local mDNS service discovered")
                    logSensitive { "Local mDNS endpoint: $address:$port for $serviceType" }
                    if (serviceType == AdbMdns.SERVICE_TYPE_TLS_PAIRING) {
                        lastDiscoveredPairingPort = port
                    } else if (serviceType == AdbMdns.SERVICE_TYPE_TLS_CONNECT) {
                        lastDiscoveredConnectPort = port
                    }
                    onDiscovered(address, port)
                } else {
                    Log.w(TAG, "Ignoring invalid or non-local mDNS service")
                    logSensitive { "Rejected mDNS endpoint: $address:$port for $serviceType" }
                }
            }
        }
        mdns.start()
        return mdns
    }

    private fun acquireMulticastLock() {
        try {
            if (multicastLock == null || !multicastLock!!.isHeld) {
                val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                multicastLock = wifi?.createMulticastLock("ShutterSoundZeroMdns")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
                Log.d(TAG, "Acquired WifiManager MulticastLock for mDNS discovery")
            }
        } catch (e: Exception) {
            logFailure("Failed to acquire MulticastLock", e)
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
        if (port !in 1..65535) {
            return@withContext Result.failure(IOException("유효한 페어링 포트를 찾지 못했습니다."))
        }

        acquireMulticastLock()
        try {
            val host = AndroidUtils.getHostIpAddress(context).ifBlank { "127.0.0.1" }
            Log.i(TAG, "Attempting local ADB pairing")
            logSensitive { "Pairing endpoint: $host:$port" }
            val success = pair(host, port, pairingCode)
            if (success) {
                Log.i(TAG, "Pairing successful")
                Result.success(Unit)
            } else {
                Result.failure(IOException("페어링에 실패했습니다. 페어링 코드를 다시 확인해 주세요."))
            }
        } catch (e: Exception) {
            logFailure("Pairing failed", e)
            Result.failure(IOException("페어링 중 오류가 발생했습니다. 무선 디버깅 상태와 코드를 확인해 주세요."))
        } finally {
            releaseMulticastLock()
        }
    }

    /**
     * 무선 디버깅 포트로 연결하여 권한 부여 및 셔터음 무음화 명령어 실행
     */
    suspend fun applyCameraMuteViaAdb(connectPort: Int? = null): Result<Unit> = withContext(Dispatchers.IO) {
        acquireMulticastLock()
        try {
            val host = AndroidUtils.getHostIpAddress(context).ifBlank { "127.0.0.1" }
            val prefs = PreferencesRepository.getInstance(context)
            Log.i(TAG, "Connecting to local ADB daemon")
            logSensitive { "Local ADB host: $host" }

            var connected = false

            // 1. 지정된 connectPort가 있다면 직접 연결 시도
            val requestedPort = connectPort?.takeIf { it in 1..65535 }
            if (requestedPort != null) {
                try {
                    Log.i(TAG, "Attempting direct ADB connect")
                    logSensitive { "Direct connect port: $requestedPort" }
                    connected = connect(host, requestedPort)
                } catch (e: Exception) {
                    logFailure("Direct ADB connect failed", e)
                }
            }

            // 2. 이미 캐시된 connectPort가 있다면 시도
            val cachedPort = (lastDiscoveredConnectPort ?: prefs.lastConnectPort.takeIf { it > 0 })
                ?.takeIf { it in 1..65535 }
            if (!connected && cachedPort != null) {
                try {
                    Log.i(TAG, "Attempting cached ADB connect")
                    logSensitive { "Cached connect port: $cachedPort" }
                    connected = connect(host, cachedPort)
                } catch (e: Exception) {
                    logFailure("Cached ADB connect failed", e)
                }
            }

            // 3. 현재 기기가 게시한 adb-tls-connect 서비스만 탐색하여 연결
            if (!connected) {
                try {
                    Log.i(TAG, "Attempting local-only TLS discovery with 7s timeout")
                    connected = connectLocalTls(7000)
                } catch (e: Exception) {
                    logFailure("Local-only TLS discovery failed", e)
                }
            }

            if (!connected && !isConnected) {
                return@withContext Result.failure(IOException("ADB 연결 실패: 무선 디버깅이 활성화되어 있는지 확인해 주세요."))
            }

            saveConnectedPort()
            Log.i(TAG, "ADB session established; applying permission and camera setting")

            // 1) WRITE_SECURE_SETTINGS 권한 부여 (영구 권한)
            executeShellCommand("pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS")

            // 2) CSC 셔터음 키 무음화 설정 (0) - 권한 부여 시 켜짐 상태 적용
            executeShellCommand("settings put system csc_pref_camera_forced_shuttersound_key 0")

            Log.i(TAG, "ADB permission and camera setting applied successfully")

            // 3) 설정 저장
            prefs.shouldMuteOnBoot = true
            prefs.isPermissionRevokedByUser = false

            Result.success(Unit)
        } catch (e: Exception) {
            logFailure("Failed to apply permission via ADB", e)
            try { disconnect() } catch (_: Exception) {}
            Result.failure(IOException("ADB 권한 적용 중 오류가 발생했습니다. 무선 디버깅 상태를 확인해 주세요."))
        } finally {
            releaseMulticastLock()
        }
    }

    /**
     * 권한 연동 해제 (WRITE_SECURE_SETTINGS 회수 및 셔터음 소리 기본값 복원)
     */
    suspend fun revokePermissionViaAdb(): Result<Unit> = withContext(Dispatchers.IO) {
        acquireMulticastLock()
        try {
            val host = AndroidUtils.getHostIpAddress(context).ifBlank { "127.0.0.1" }
            val prefs = PreferencesRepository.getInstance(context)
            val savedPort = (lastDiscoveredConnectPort ?: prefs.lastConnectPort.takeIf { it > 0 })
                ?.takeIf { it in 1..65535 }

            var connected = isConnected
            if (!connected && savedPort != null) {
                try {
                    connected = connect(host, savedPort)
                } catch (e: Exception) {
                    logFailure("Saved-port reconnect for permission revoke failed", e)
                }
            }

            if (!connected) {
                try {
                    connected = connectLocalTls(4000)
                    if (connected) saveConnectedPort()
                } catch (e: Exception) {
                    logFailure("Local-only TLS reconnect for permission revoke failed", e)
                }
            }

            if (!connected) {
                return@withContext Result.failure(
                    IOException("ADB 연결 실패: 무선 디버깅을 켠 뒤 다시 시도해 주세요.")
                )
            }

            // 1) 셔터음 키 1로 복원 (소리 남)
            executeShellCommand("settings put system csc_pref_camera_forced_shuttersound_key 1")
            // 2) WRITE_SECURE_SETTINGS 권한 회수
            executeShellCommand("pm revoke ${context.packageName} android.permission.WRITE_SECURE_SETTINGS")
            try {
                disconnect()
            } catch (e: Exception) {
                logFailure("ADB disconnect after permission revoke failed", e)
            }

            prefs.shouldMuteOnBoot = false
            prefs.lastConnectPort = -1
            prefs.isPermissionRevokedByUser = true
            Result.success(Unit)
        } catch (e: Exception) {
            logFailure("Failed to revoke permission via ADB", e)
            Result.failure(IOException("권한 연동 해제 중 오류가 발생했습니다. 무선 디버깅 상태를 확인해 주세요."))
        } finally {
            releaseMulticastLock()
        }
    }

    /**
     * 무선 디버깅 셸을 통해 CSC 셔터음 키(0 또는 1)를 직접 변경
     */
    suspend fun setCameraMute(enableMute: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        acquireMulticastLock()
        try {
            val targetVal = if (enableMute) "0" else "1"
            val host = AndroidUtils.getHostIpAddress(context).ifBlank { "127.0.0.1" }
            val prefs = PreferencesRepository.getInstance(context)

            // 1. 이미 연결되어 있는 세션이 있다면 즉시 재사용
            if (isConnected) {
                try {
                    executeShellCommand("settings put system csc_pref_camera_forced_shuttersound_key $targetVal")
                    prefs.shouldMuteOnBoot = enableMute
                    Log.i(TAG, "Reused active ADB session for camera setting")
                    return@withContext Result.success(Unit)
                } catch (e: Exception) {
                    logFailure("Active ADB session failed; reconnecting", e)
                    try { disconnect() } catch (_: Exception) {}
                }
            }

            var connected = false
            val savedPort = (lastDiscoveredConnectPort ?: prefs.lastConnectPort.takeIf { it > 0 })
                ?.takeIf { it in 1..65535 }

            // 2. 저장된 포트로 초고속 직접 연결 시도
            if (savedPort != null) {
                try {
                    Log.i(TAG, "Attempting fast ADB reconnect")
                    logSensitive { "Saved reconnect port: $savedPort" }
                    connected = connect(host, savedPort)
                    if (connected) saveConnectedPort()
                } catch (e: Exception) {
                    logFailure("Fast ADB reconnect failed", e)
                }
            }

            // 3. 현재 기기의 mDNS 서비스만 탐색하여 TLS 연결
            if (!connected) {
                try {
                    Log.i(TAG, "Attempting local-only TLS discovery with 4s timeout")
                    connected = connectLocalTls(4000)
                    if (connected) saveConnectedPort()
                } catch (e: Exception) {
                    logFailure("Local-only TLS discovery failed", e)
                }
            }

            if (!connected && !isConnected) {
                return@withContext Result.failure(IOException("무선 디버깅에 연결할 수 없습니다."))
            }

            executeShellCommand("settings put system csc_pref_camera_forced_shuttersound_key $targetVal")
            prefs.shouldMuteOnBoot = enableMute
            Log.i(TAG, "Camera setting updated successfully via ADB")
            Result.success(Unit)
        } catch (e: Exception) {
            logFailure("Failed to update camera setting via ADB", e)
            try { disconnect() } catch (_: Exception) {}
            Result.failure(IOException("셔터음 설정 변경 중 오류가 발생했습니다. 무선 디버깅 상태를 확인해 주세요."))
        } finally {
            releaseMulticastLock()
        }
    }

    private fun saveConnectedPort() {
        try {
            val adbConn = adbConnection ?: return
            val portField = adbConn.javaClass.getDeclaredField("mPort").apply { isAccessible = true }
            val port = portField.getInt(adbConn)
            if (port in 1..65535) {
                lastDiscoveredConnectPort = port
                PreferencesRepository.getInstance(context).lastConnectPort = port
                Log.i(TAG, "Saved active ADB connect endpoint")
                logSensitive { "Saved active ADB connect port: $port" }
            }
        } catch (e: Exception) {
            logFailure("Could not extract connect port", e)
        }
    }

    private fun connectLocalTls(timeoutMs: Long): Boolean {
        val endpoint = AtomicReference<Pair<InetAddress, Int>?>(null)
        val discovered = CountDownLatch(1)
        val mdns = startMdnsDiscovery(AdbMdns.SERVICE_TYPE_TLS_CONNECT) { address, port ->
            if (endpoint.compareAndSet(null, address to port)) {
                discovered.countDown()
            }
        }

        return try {
            if (!discovered.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "Timed out waiting for this device's TLS ADB service")
                false
            } else {
                val (address, port) = endpoint.get() ?: return false
                if (port !in 1..65535) return false
                Log.i(TAG, "Connecting to discovered local TLS ADB service")
                logSensitive { "Discovered TLS endpoint: $address:$port" }
                val hostAddress = address.hostAddress ?: return false
                connect(hostAddress, port)
            }
        } finally {
            mdns.stop()
        }
    }

    private fun executeShellCommand(cmd: String): String {
        val marker = "__SSZ_EXIT_${SystemClock.elapsedRealtimeNanos()}_${shellCommandSequence.incrementAndGet()}__"
        val wrappedCommand = "$cmd; printf '\n$marker:%d\n' \$?"
        val output = ByteArrayOutputStream()
        var commandResult: AdbShellCommandResult? = null
        val deadline = SystemClock.elapsedRealtime() + SHELL_COMMAND_TIMEOUT_MS

        openStream("shell:$wrappedCommand").use { stream ->
            val buffer = ByteArray(1024)

            while (commandResult == null) {
                if (SystemClock.elapsedRealtime() >= deadline) {
                    throw IOException("ADB 명령 응답 시간이 초과되었습니다.")
                }

                val availableBytes = stream.available()
                if (availableBytes <= 0) {
                    if (stream.isClosed) break
                    try {
                        Thread.sleep(SHELL_COMMAND_POLL_INTERVAL_MS)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw IOException("ADB 명령 대기가 중단되었습니다.", e)
                    }
                    continue
                }

                val readBytes = stream.read(buffer, 0, minOf(buffer.size, availableBytes))
                if (readBytes <= 0) {
                    if (stream.isClosed) break
                    continue
                }

                if (output.size() + readBytes > MAX_SHELL_OUTPUT_BYTES) {
                    throw IOException("ADB 명령 출력이 허용 크기를 초과했습니다.")
                }
                output.write(buffer, 0, readBytes)
                commandResult = AdbShellCommandResultParser.parseOrNull(
                    output.toString(Charsets.UTF_8.name()),
                    marker
                )
            }
        }

        val result = commandResult
            ?: throw IOException("ADB 명령의 완료 상태를 확인할 수 없습니다.")
        if (result.exitCode != 0) {
            if (isDebuggable) {
                val errorOutput = result.output
                    .lineSequence()
                    .joinToString(" ")
                    .trim()
                    .take(512)
                    .ifBlank { "출력 없음" }
                Log.w(TAG, "ADB shell command failed with exit code ${result.exitCode}: $errorOutput")
            } else {
                Log.w(TAG, "ADB shell command failed with exit code ${result.exitCode}")
            }
            throw IOException("ADB 명령이 종료 코드 ${result.exitCode}로 실패했습니다.")
        }

        return result.output
    }
}
