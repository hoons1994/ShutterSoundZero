package io.github.hoons1994.shuttersoundzero.core.adb

import io.github.hoons1994.shuttersoundzero.core.coordination.CameraMuteOperationGate

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.SystemClock
import android.util.Log
import io.github.hoons1994.shuttersoundzero.data.PreferencesRepository
import io.github.hoons1994.shuttersoundzero.logging.DiagnosticLogger
import io.github.muntashirakon.adb.AdbShellIdentity
import io.github.muntashirakon.adb.CancellableAdbConnection
import io.github.muntashirakon.adb.CancellablePairingConnection
import io.github.muntashirakon.adb.PairingFailureException
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
    fun startPairingDiscovery(
        onPairingPortDiscovered: (Int) -> Unit,
        onConnectPortDiscovered: (Int) -> Unit,
        onFailure: (Int) -> Unit,
        onPairingPortLost: () -> Unit = {},
        onConnectPortLost: () -> Unit = {}
    ) {
        LocalNetworkAccess.requireGranted(context)
        stopPairingDiscovery()
        val session = pairingDiscoveryPorts.begin()
        try {
            pairingMulticastLease = multicastLeaseManager.acquire()
            pairingMdns = startMdnsDiscovery(SERVICE_TYPE_TLS_PAIRING, onFailure = onFailure,
                onLost = { pairingDiscoveryPorts.onPairingLost(session, it, onPairingPortLost) }) { _, port, service ->
                pairingDiscoveryPorts.onPairingPort(session, port, service, onPairingPortDiscovered)
            }
            pairingConnectMdns = startMdnsDiscovery(SERVICE_TYPE_TLS_CONNECT, onFailure = onFailure,
                onLost = { pairingDiscoveryPorts.onConnectLost(session, it, onConnectPortLost) }) { _, port, service ->
                pairingDiscoveryPorts.onConnectPort(session, port, service, onConnectPortDiscovered)
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
        onLost: (String) -> Unit = {},
        onServiceFound: () -> Unit = {},
        onFailureDetail: (AdbDiscoveryReason, Int?) -> Unit = { _, _ -> },
        onDiscovered: (InetAddress, Int, String) -> Unit
    ): SafeAdbMdnsDiscovery {
        LocalNetworkAccess.requireGranted(context)
        return SafeAdbMdnsDiscovery(context, serviceType, onFailure, onLost, onServiceFound, onFailureDetail) { address, port, service ->
            val local = LocalAdbEndpointPolicy.isLocalDeviceAddress(address)
            if (local && port in 1..65535) {
                logSensitive { "Local mDNS endpoint: $address:$port for $serviceType" }
                onDiscovered(address, port, service)
            } else {
                onFailureDetail(if (local) AdbDiscoveryReason.INVALID_ENDPOINT else AdbDiscoveryReason.NO_LOCAL_ENDPOINT, null)
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
                error is AdbServiceNotFoundException || error is AdbConnectionFailedException ||
                error is PairingFailureException) result
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
        val started = SystemClock.elapsedRealtime()
        DiagnosticLogger.record(context, stage, DiagnosticLogger.Outcome.STARTED)
        try {
            return block().also {
                DiagnosticLogger.record(context, stage, DiagnosticLogger.Outcome.SUCCESS,
                    durationMillis = SystemClock.elapsedRealtime() - started)
            }
        } catch (error: CancellationException) {
            DiagnosticLogger.record(context, stage, DiagnosticLogger.Outcome.CANCELLED,
                durationMillis = SystemClock.elapsedRealtime() - started)
            throw error
        } catch (error: Exception) {
            DiagnosticLogger.record(context, stage, DiagnosticLogger.Outcome.FAILURE, error,
                durationMillis = SystemClock.elapsedRealtime() - started)
            throw error
        }
    }

    suspend fun pairLocal(port: Int, pairingCode: String): Result<Unit> = operation(
        "페어링 중 오류가 발생했습니다. 무선 디버깅 상태와 코드를 확인해 주세요."
    ) {
        require(port in 1..65535) { "Invalid pairing port" }
        require(PairingCode.isValid(pairingCode)) { "Invalid pairing code" }
        val host = LOCAL_ADB_HOST
        val identityStarted = System.nanoTime()
        val keys = try {
            keyPairAndCert.also {
                recordPairingPhase(PairingFailureException.Phase.IDENTITY,
                    (System.nanoTime() - identityStarted) / 1_000_000L, null)
            }
        } catch (error: Exception) {
            val failure = PairingFailureException(PairingFailureException.Phase.IDENTITY, error)
            recordPairingPhase(failure.phase, (System.nanoTime() - identityStarted) / 1_000_000L, failure)
            throw failure
        }
        val password = pairingCode.toByteArray(Charsets.UTF_8)
        val client = try {
            CancellablePairingConnection(host, port, password, keys.first, keys.second, DEVICE_NAME)
        } finally { password.fill(0) }
        client.use {
            runInterruptible(Dispatchers.IO) {
                BoundedBlockingOperation.run(PAIRING_OPERATION_TIMEOUT_MS, "SSZ-AdbPairing", client::close) {
                    client.start(PAIRING_OPERATION_TIMEOUT_MS, ::recordPairingPhase)
                }
            }
        }
    }

    private fun recordPairingPhase(
        phase: PairingFailureException.Phase, durationMillis: Long, failure: PairingFailureException?
    ) {
        val stage = when (phase) {
            PairingFailureException.Phase.IDENTITY -> DiagnosticLogger.Stage.PAIRING_IDENTITY
            PairingFailureException.Phase.TCP_CONNECT -> DiagnosticLogger.Stage.PAIRING_TCP_CONNECT
            PairingFailureException.Phase.TLS_HANDSHAKE -> DiagnosticLogger.Stage.PAIRING_TLS_HANDSHAKE
            PairingFailureException.Phase.TLS_EXPORT -> DiagnosticLogger.Stage.PAIRING_TLS_EXPORT
            PairingFailureException.Phase.CODE_AUTHENTICATION -> DiagnosticLogger.Stage.PAIRING_CODE_AUTHENTICATION
            PairingFailureException.Phase.PEER_INFO -> DiagnosticLogger.Stage.PAIRING_PEER_INFO
        }
        DiagnosticLogger.record(context, stage,
            if (failure == null) DiagnosticLogger.Outcome.SUCCESS else DiagnosticLogger.Outcome.FAILURE,
            failure, durationMillis = durationMillis)
    }

    /** The command session is valid only inside this serialized operation. */
    internal suspend fun runCommands(
        message: String,
        block: suspend AdbCommandSession.() -> Unit
    ): Result<Unit> = operation(message) {
        val session = object : AdbCommandSession {
            override suspend fun connect(port: Int?, timeoutMs: Long) = ensureConnection(port, timeoutMs)
            override suspend fun execute(command: String): String = executeShellCommand(command)
        }
        session.block()
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
                        discoverLocalTlsPort(discoveryTimeout)
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

    private suspend fun discoverLocalTlsPort(timeoutMs: Long): Int {
        LocalNetworkAccess.requireGranted(context)
        val endpoint = AtomicReference<Pair<InetAddress, Int>?>(null)
        val trace = AdbDiscoveryTrace()
        val discovered = CountDownLatch(1)
        val mdns = try {
            startMdnsDiscovery(SERVICE_TYPE_TLS_CONNECT,
                onServiceFound = trace::serviceFound,
                onFailureDetail = { reason, code ->
                    trace.failed(reason, code)
                    // A start failure is terminal; a single resolution failure may still recover.
                    if (reason == AdbDiscoveryReason.START_FAILED) discovered.countDown()
                }
            ) { address, port, _ ->
                if (endpoint.compareAndSet(null, address to port)) discovered.countDown()
            }
        } catch (error: RuntimeException) {
            throw AdbServiceNotFoundException(error, AdbDiscoveryReason.START_FAILED)
        }
        return try {
            runInterruptible(Dispatchers.IO) { discovered.await(timeoutMs, TimeUnit.MILLISECONDS) }
            // Only a validated local endpoint wins, even if a failure callback released the latch.
            endpoint.get()?.second ?: throw trace.failure()
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
