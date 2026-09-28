package io.github.hoons1994.shuttersoundzero.core.adb

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.SystemClock
import android.util.Log
import io.github.hoons1994.shuttersoundzero.core.CscMuteManager
import io.github.hoons1994.shuttersoundzero.core.CscStateVerifier
import io.github.hoons1994.shuttersoundzero.data.PreferencesRepository
import io.github.hoons1994.shuttersoundzero.diagnostics.DiagnosticLogger
import io.github.muntashirakon.adb.AdbShellIdentity
import io.github.muntashirakon.adb.CancellableAdbConnection
import io.github.muntashirakon.adb.CancellablePairingConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** On-device ADB manager. All command sessions are owned, serialized and closed on every exit. */
class StandaloneAdbManager(context: Context) {
    private val context = context.applicationContext

    companion object {
        private const val TAG = "StandaloneAdbManager"
        private const val DEVICE_NAME = "ShutterSoundZero"
        private const val LOCAL_ADB_HOST = "127.0.0.1"
        private const val SERVICE_TYPE_TLS_PAIRING = "adb-tls-pairing"
        private const val SERVICE_TYPE_TLS_CONNECT = "adb-tls-connect"
        private const val ADB_CONNECTION_TIMEOUT_MS = 8_000L
        private const val PAIRING_OPERATION_TIMEOUT_MS = 12_000L
        private const val SHELL_COMMAND_TOTAL_TIMEOUT_MS = 6_000L
        private const val SHELL_COMMAND_TIMEOUT_MS = 5_000L
        private const val MAX_SHELL_OUTPUT_BYTES = 64 * 1024
        private val shellCommandSequence = AtomicLong()

        @SuppressLint("StaticFieldLeak") // Stores only applicationContext.
        @Volatile private var INSTANCE: StandaloneAdbManager? = null
        fun getInstance(context: Context): StandaloneAdbManager = INSTANCE ?: synchronized(this) {
            INSTANCE ?: StandaloneAdbManager(context).also { INSTANCE = it }
        }
    }

    private val isDebuggable: Boolean
        get() = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    private inline fun logSensitive(message: () -> String) {
        if (isDebuggable) Log.d(TAG, message())
    }
    private fun logFailure(summary: String, error: Throwable) {
        if (isDebuggable) Log.w(TAG, "$summary: ${error.message}", error)
        else Log.w(TAG, "$summary (${error.javaClass.simpleName})")
    }

    init {
        try {
            java.security.Security.insertProviderAt(org.conscrypt.Conscrypt.newProvider(), 1)
        } catch (error: Throwable) {
            logFailure("Failed to register Conscrypt provider", error)
        }
    }

    private val keyPairAndCert by lazy { AdbKeyHelper.getOrCreateKeyPairAndCertificate(context) }
    private var connection: CancellableAdbConnection? = null // Accessed only inside operationRunner.
    private val operationRunner = AdbOperationRunner(
        enter = CameraMuteOperationGate::enter,
        exit = CameraMuteOperationGate::exit,
        acquire = {
            LocalNetworkAccess.requireGranted(this.context)
            multicastLeaseManager.acquire()
        },
        cleanup = ::disconnect,
        onCleanupFailure = { logFailure("ADB resource cleanup failed", it) }
    )

    private val pairingDiscoveryPorts = PairingDiscoveryPortState()
    val lastDiscoveredPairingPort: Int? get() = pairingDiscoveryPorts.pairingPort
    val lastDiscoveredConnectPort: Int? get() = pairingDiscoveryPorts.connectPort
    fun clearDiscoveredPorts() = pairingDiscoveryPorts.clearPorts()
    private var multicastLock: android.net.wifi.WifiManager.MulticastLock? = null
    private val multicastLeaseManager by lazy {
        MulticastLockLeaseManager(::acquireMulticastLockResource, ::releaseMulticastLockResource)
    }
    private var pairingMulticastLease: MulticastLockLeaseManager.Lease? = null
    private var pairingMdns: SafeAdbMdnsDiscovery? = null
    private var pairingConnectMdns: SafeAdbMdnsDiscovery? = null
    @Synchronized
    fun startPairingDiscovery(onPairingPortDiscovered: (Int) -> Unit, onConnectPortDiscovered: (Int) -> Unit) {
        LocalNetworkAccess.requireGranted(context)
        stopPairingDiscovery()
        val session = pairingDiscoveryPorts.begin()
        try {
            pairingMulticastLease = multicastLeaseManager.acquire()
            pairingMdns = startMdnsDiscovery(SERVICE_TYPE_TLS_PAIRING) { _, port ->
                pairingDiscoveryPorts.onPairingPort(session, port, onPairingPortDiscovered)
            }
            pairingConnectMdns = startMdnsDiscovery(SERVICE_TYPE_TLS_CONNECT) { _, port ->
                pairingDiscoveryPorts.onConnectPort(session, port, onConnectPortDiscovered)
            }
        } catch (error: Exception) {
            stopPairingDiscovery()
            throw error
        }
    }

    @Synchronized
    fun stopPairingDiscovery() {
        pairingDiscoveryPorts.stop()
        val pairing = pairingMdns
        val connect = pairingConnectMdns
        pairingMdns = null
        pairingConnectMdns = null
        try { pairing?.stop() } catch (error: Exception) { logFailure("Failed to stop pairing discovery", error) }
        try { connect?.stop() } catch (error: Exception) { logFailure("Failed to stop connect discovery", error) }
        try { pairingMulticastLease?.close() }
        catch (error: Exception) { logFailure("Failed to release pairing multicast lease", error) }
        finally { pairingMulticastLease = null }
    }

    private fun startMdnsDiscovery(
        serviceType: String,
        onFailure: (Int) -> Unit = {},
        onDiscovered: (InetAddress, Int) -> Unit
    ): SafeAdbMdnsDiscovery {
        LocalNetworkAccess.requireGranted(context)
        return SafeAdbMdnsDiscovery(context, serviceType, onFailure) { address, port ->
            if (LocalAdbEndpointPolicy.isLocalDeviceAddress(address) && port in 1..65535) {
                logSensitive { "Local mDNS endpoint: $address:$port for $serviceType" }
                onDiscovered(address, port)
            } else {
                Log.w(TAG, "Ignoring invalid or non-local mDNS service")
            }
        }.also { it.start() }
    }

    private fun acquireMulticastLockResource(): Boolean = try {
        val held = multicastLock?.isHeld == true
        if (held) true else {
            multicastLock = null
            val wifi = context.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            val lock = wifi?.createMulticastLock("ShutterSoundZeroMdns")?.apply {
                setReferenceCounted(false)
                acquire()
            }
            if (lock?.isHeld == true) {
                multicastLock = lock
                true
            } else false
        }
    } catch (error: Exception) {
        multicastLock = null
        logFailure("Failed to acquire MulticastLock", error)
        false
    }

    private fun releaseMulticastLockResource() {
        try { multicastLock?.let { if (it.isHeld) it.release() } }
        catch (error: Exception) { logFailure("Failed to release MulticastLock", error) }
        finally { multicastLock = null }
    }

    private suspend fun operation(message: String, block: suspend () -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) {
            val result = operationRunner.run(block)
            val error = result.exceptionOrNull()
            if (error == null || error is LocalNetworkPermissionRequiredException ||
                error is AdbServiceNotFoundException || error is AdbConnectionFailedException) result
            else {
                logFailure(message, error)
                val userMessage = when (error) {
                    is BlockingOperationTimeoutException, is SocketTimeoutException ->
                        "ADB 응답 시간이 초과되었습니다. 무선 디버깅 상태를 확인하고 다시 시도해 주세요."
                    is BlockingOperationBusyException ->
                        "이전 ADB 작업을 정리하고 있습니다. 잠시 후 다시 시도해 주세요."
                    else -> message
                }
                Result.failure(IOException(userMessage, error))
            }
        }

    private suspend fun <T> diagnosed(stage: DiagnosticLogger.Stage, block: suspend () -> T): T {
        DiagnosticLogger.record(context, stage, DiagnosticLogger.Outcome.STARTED)
        try {
            return block().also {
                DiagnosticLogger.record(context, stage, DiagnosticLogger.Outcome.SUCCESS)
            }
        } catch (error: CancellationException) {
            DiagnosticLogger.record(context, stage, DiagnosticLogger.Outcome.CANCELLED)
            throw error
        } catch (error: Exception) {
            DiagnosticLogger.record(context, stage, DiagnosticLogger.Outcome.FAILURE, error)
            throw error
        }
    }

    suspend fun pairLocal(port: Int, pairingCode: String): Result<Unit> = operation(
        "페어링 중 오류가 발생했습니다. 무선 디버깅 상태와 코드를 확인해 주세요."
    ) {
        require(port in 1..65535) { "Invalid pairing port" }
        require(PairingCode.isValid(pairingCode)) { "Invalid pairing code" }
        val host = LOCAL_ADB_HOST
        val password = pairingCode.toByteArray(Charsets.UTF_8)
        val client = try {
            CancellablePairingConnection(host, port, password, keyPairAndCert.first, keyPairAndCert.second, DEVICE_NAME)
        } finally { password.fill(0) }
        client.use {
            runInterruptible(Dispatchers.IO) {
                BoundedBlockingOperation.run(PAIRING_OPERATION_TIMEOUT_MS, "SSZ-AdbPairing", client::close) {
                    client.start(PAIRING_OPERATION_TIMEOUT_MS)
                }
            }
        }
    }

    suspend fun applyCameraMuteViaAdb(connectPort: Int? = null): Result<Unit> = operation(
        "ADB 권한 적용 중 오류가 발생했습니다. 무선 디버깅 상태를 확인해 주세요."
    ) {
        ensureConnection(connectPort, 7_000)
        val prefs = PreferencesRepository.getInstance(context)
        val userId = AdbShellIdentity.androidUserIdForUid(context.applicationInfo.uid)
        executeShellCommand(AdbShellCommands.grantWriteSecureSettings(context.packageName, userId))
        if (!CscMuteManager.hasWritePermission(context)) throw IOException("WRITE_SECURE_SETTINGS 권한 부여 상태를 확인할 수 없습니다.")
        prefs.isPermissionRevokedByUser = false
        applyAndVerifyMute(true)
        prefs.shouldMuteOnBoot = true
    }

    suspend fun revokePermissionViaAdb(): Result<Unit> = operation(
        "권한 연동 해제 중 오류가 발생했습니다. 무선 디버깅 상태를 확인해 주세요."
    ) {
        val prefs = PreferencesRepository.getInstance(context)
        try {
            ensureConnection(null, 4_000)
            applyAndVerifyMute(false)
            prefs.shouldMuteOnBoot = false
            var commandFailure: Exception? = null
            try {
                val userId = AdbShellIdentity.androidUserIdForUid(context.applicationInfo.uid)
                executeShellCommand(AdbShellCommands.revokeWriteSecureSettings(context.packageName, userId))
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                commandFailure = error
                logFailure("Permission revoke command did not complete cleanly", error)
            }
            if (CscMuteManager.hasWritePermission(context)) {
                prefs.isPermissionRevokedByUser = false
                throw commandFailure ?: IOException("WRITE_SECURE_SETTINGS 권한 회수 상태를 확인할 수 없습니다.")
            }
            prefs.lastConnectPort = -1
            prefs.isPermissionRevokedByUser = true
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            if (!CscMuteManager.hasWritePermission(context)) {
                prefs.lastConnectPort = -1
                prefs.isPermissionRevokedByUser = true
            }
            throw error
        }
    }

    suspend fun setCameraMute(enableMute: Boolean): Result<Unit> = operation(
        "셔터음 설정 변경 중 오류가 발생했습니다. 무선 디버깅 상태를 확인해 주세요."
    ) {
        diagnosed(if (enableMute) DiagnosticLogger.Stage.CSC_REAPPLY else DiagnosticLogger.Stage.CSC_RESTORE) {
            ensureConnection(null, 4_000)
            applyAndVerifyMute(enableMute)
            PreferencesRepository.getInstance(context).shouldMuteOnBoot = enableMute
        }
    }

    private suspend fun applyAndVerifyMute(mute: Boolean) {
        val userId = AdbShellIdentity.androidUserIdForUid(context.applicationInfo.uid)
        diagnosed(DiagnosticLogger.Stage.CSC_WRITE) {
            executeShellCommand(AdbShellCommands.setCameraMute(mute, userId))
        }
        diagnosed(DiagnosticLogger.Stage.CSC_VERIFY) {
            if (!CscStateVerifier.waitFor(mute) { CscMuteManager.isCscShutterSoundMuted(context) }) {
                throw IOException("카메라 설정 적용 상태를 확인할 수 없습니다.")
            }
        }
    }

    private suspend fun ensureConnection(requestedPort: Int?, discoveryTimeout: Long) {
        if (connection?.isConnected == true) return
        val prefs = PreferencesRepository.getInstance(context)
        try {
            val port = AdbConnectionResolver.resolve(
                candidatePorts = listOfNotNull(requestedPort, lastDiscoveredConnectPort, prefs.lastConnectPort),
                connect = { port ->
                    diagnosed(DiagnosticLogger.Stage.ADB_CONNECT) {
                        if (!connectInterruptibly(LOCAL_ADB_HOST, port)) throw IOException("ADB connection did not complete")
                        true
                    }
                },
                discover = {
                    diagnosed(DiagnosticLogger.Stage.ADB_CONNECT_DISCOVERY) {
                        discoverLocalTlsPort(discoveryTimeout) ?: throw AdbServiceNotFoundException()
                    }
                }
            )
            rememberConnectedPort(port)
        } catch (error: AdbServiceNotFoundException) {
            clearDiscoveredPorts()
            prefs.clearTransientAdbConnectionState()
            throw error
        } catch (error: AdbConnectionFailedException) {
            clearDiscoveredPorts()
            prefs.clearTransientAdbConnectionState()
            throw error
        }
    }

    private suspend fun connectInterruptibly(host: String, port: Int): Boolean {
        disconnect() // No failed or stale session can be overwritten by the next attempt.
        val attempt = CancellableAdbConnection(host, port, keyPairAndCert.first, keyPairAndCert.second, Build.VERSION.SDK_INT,
            "${context.packageName}.adb-auth", AdbShellIdentity.androidUserIdForUid(context.applicationInfo.uid))
        connection = attempt // Ownership is published BEFORE TCP creation/handshake starts.
        var success = false
        try {
            runInterruptible(Dispatchers.IO) {
                BoundedBlockingOperation.run(ADB_CONNECTION_TIMEOUT_MS, "SSZ-AdbConnect", attempt::cancel) {
                    attempt.connect(ADB_CONNECTION_TIMEOUT_MS)
                }
            }
            success = attempt.isConnected
            return success
        } finally {
            // Also covers cancellation while a successful worker result is being dispatched.
            if (!success) {
                connection = null
                attempt.close()
            }
        }
    }

    private fun disconnect() {
        val previous = connection
        connection = null
        previous?.close()
    }

    private fun rememberConnectedPort(port: Int) {
        if (port !in 1..65535) return
        pairingDiscoveryPorts.rememberConnectedPort(port)
        PreferencesRepository.getInstance(context).lastConnectPort = port
    }

    private suspend fun discoverLocalTlsPort(timeoutMs: Long): Int? {
        LocalNetworkAccess.requireGranted(context)
        val endpoint = AtomicReference<Pair<InetAddress, Int>?>(null)
        val failure = AtomicReference<IOException?>(null)
        val discovered = CountDownLatch(1)
        val mdns = startMdnsDiscovery(SERVICE_TYPE_TLS_CONNECT, onFailure = {
            failure.set(IOException("로컬 ADB 서비스 주소를 확인하지 못했습니다. ($it)"))
        }) { address, port ->
            if (endpoint.compareAndSet(null, address to port)) discovered.countDown()
        }
        return try {
            val found = runInterruptible(Dispatchers.IO) { discovered.await(timeoutMs, TimeUnit.MILLISECONDS) }
            if (!found) {
                failure.get()?.let { throw AdbServiceNotFoundException(it) }
                null
            } else {
                // mDNS validates that the service belongs to this device, but only its port is
                // consumed. Never send credentials to a Wi-Fi address that may later change owner.
                endpoint.get()?.second
            }
        } finally { mdns.stop() }
    }

    private suspend fun executeShellCommand(cmd: String): String {
        val session = connection ?: throw IOException("Not connected to ADB")
        return runInterruptible(Dispatchers.IO) {
            BoundedBlockingOperation.run(SHELL_COMMAND_TOTAL_TIMEOUT_MS, "SSZ-AdbShell", session::cancel) {
                executeShellCommandBlocking(session, cmd)
            }
        }
    }

    private fun executeShellCommandBlocking(session: CancellableAdbConnection, cmd: String): String {
        val marker = "__SSZ_EXIT_${SystemClock.elapsedRealtimeNanos()}_${shellCommandSequence.incrementAndGet()}__"
        val wrappedCommand = "$cmd; printf '\n$marker:%d\n' \$?"
        val output = ByteArrayOutputStream()
        var commandResult: AdbShellCommandResult? = null
        session.readShell(wrappedCommand, SHELL_COMMAND_TIMEOUT_MS) { data ->
            if (output.size() + data.size > MAX_SHELL_OUTPUT_BYTES) throw IOException("ADB 명령 출력이 허용 크기를 초과했습니다.")
            output.write(data)
            commandResult = AdbShellCommandResultParser.parseOrNull(output.toString(Charsets.UTF_8.name()), marker)
            commandResult != null
        }
        val result = commandResult ?: throw IOException("ADB 명령의 완료 상태를 확인할 수 없습니다.")
        if (result.exitCode != 0) {
            if (isDebuggable) {
                val errorOutput = result.output.lineSequence().joinToString(" ").trim().take(512).ifBlank { "출력 없음" }
                Log.w(TAG, "ADB shell command failed with exit code ${result.exitCode}: $errorOutput")
            } else Log.w(TAG, "ADB shell command failed with exit code ${result.exitCode}")
            throw IOException("ADB 명령이 종료 코드 ${result.exitCode}로 실패했습니다.")
        }
        return result.output
    }
}
