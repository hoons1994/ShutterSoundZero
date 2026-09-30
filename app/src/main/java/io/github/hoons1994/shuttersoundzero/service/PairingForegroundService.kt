package io.github.hoons1994.shuttersoundzero.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import io.github.hoons1994.shuttersoundzero.MainActivity
import io.github.hoons1994.shuttersoundzero.R
import io.github.hoons1994.shuttersoundzero.core.DeveloperOptionsManager
import io.github.hoons1994.shuttersoundzero.core.adb.LocalNetworkPermissionRequiredException
import io.github.hoons1994.shuttersoundzero.core.adb.PairingCode
import io.github.hoons1994.shuttersoundzero.core.adb.StandaloneAdbManager
import io.github.hoons1994.shuttersoundzero.data.PreferencesRepository
import io.github.hoons1994.shuttersoundzero.data.SetupIssue
import io.github.hoons1994.shuttersoundzero.diagnostics.DiagnosticLogger
import io.github.hoons1994.shuttersoundzero.ui.notification.PairingNotificationHelper
import io.github.hoons1994.shuttersoundzero.ui.notification.PairingNotificationState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class PairingForegroundService : Service() {
    companion object {
        private const val TAG = "PairingForegroundService"
        private const val ACTION_START = "io.github.hoons1994.shuttersoundzero.action.START_PAIRING"
        private const val ACTION_STOP = "io.github.hoons1994.shuttersoundzero.action.STOP_PAIRING"
        private const val ACTION_SUBMIT_CODE = "io.github.hoons1994.shuttersoundzero.action.SUBMIT_PAIRING_CODE"
        private const val EXTRA_DEV_OPTIONS_OFF = "dev_options_off"
        private const val EXTRA_PAIRING_CODE = "pairing_code"

        fun start(context: Context, isDevOptionsOff: Boolean) {
            val intent = Intent(context, PairingForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DEV_OPTIONS_OFF, isDevOptionsOff)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun submitCode(context: Context, code: String) {
            val intent = Intent(context, PairingForegroundService::class.java).apply {
                action = ACTION_SUBMIT_CODE
                putExtra(EXTRA_PAIRING_CODE, code)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            try {
                context.startService(stopIntent(context))
            } catch (_: Exception) {
                context.stopService(Intent(context, PairingForegroundService::class.java))
                PairingNotificationHelper.cancelNotification(context)
            }
        }

        fun stopIntent(context: Context): Intent {
            return Intent(ACTION_STOP)
                .setClass(context, PairingForegroundService::class.java)
        }

    }

    private val adbManager by lazy { StandaloneAdbManager.getInstance(this) }
    private val prefs by lazy { PreferencesRepository.getInstance(this) }
    // ADB operations dispatch their blocking work to IO; service/notification state stays on main.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pairingGeneration = OperationGeneration()
    private val discoveryNotificationGeneration = OperationGeneration()
    private var pairingJob: Job? = null
    private var isSetupObserverRegistered = false
    private var isPairingActive = false
    private var isPairingCompleting = false
    private val notificationRefresh = PairingNotificationRefreshState()

    private val setupObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            refreshPairingSetupNotification()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startPairing()
            ACTION_SUBMIT_CODE -> submitPairingCode(intent.getStringExtra(EXTRA_PAIRING_CODE).orEmpty().trim())
            ACTION_STOP -> stopPairing(showSuccess = false, wirelessDebuggingDisabled = false)
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun startPairing() {
        // 새 1회 설정 시작은 이전 코드 제출 작업의 완료보다 우선한다.
        pairingGeneration.invalidate()
        pairingJob?.cancel()
        pairingJob = null
        stopPairingDiscovery()
        adbManager.clearDiscoveredPorts()
        isPairingActive = true
        isPairingCompleting = false
        val setupState = readPairingSetupState()
        notificationRefresh.reset(setupState)

        prefs.lastSetupIssue = null
        DiagnosticLogger.record(
            this,
            DiagnosticLogger.Stage.PAIRING_DISCOVERY,
            DiagnosticLogger.Outcome.STARTED
        )

        val notification = PairingNotificationHelper.buildPairingNotification(
            this,
            state = setupState.notificationState,
            isDevOptionsOff = !setupState.developerOptionsEnabled
        )
        startForeground(
            PairingNotificationHelper.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )

        registerSetupObserver()

        if (setupState.developerOptionsEnabled && setupState.wirelessDebuggingEnabled) {
            startPairingDiscovery()
        }
        // Re-read after registration so a change during service startup cannot be missed.
        refreshPairingSetupNotification()
    }

    private fun startPairingDiscovery(): Boolean {
        notificationRefresh.clearDiscovery()
        val notificationGeneration = discoveryNotificationGeneration.next()
        return try {
            adbManager.startPairingDiscovery(
                onPairingPortDiscovered = {
                    DiagnosticLogger.record(
                        this,
                        DiagnosticLogger.Stage.PAIRING_SERVICE_DISCOVERED,
                        DiagnosticLogger.Outcome.INFO
                    )
                    mainHandler.post {
                        if (isPairingActive && !isPairingCompleting &&
                            discoveryNotificationGeneration.isCurrent(notificationGeneration)) {
                            notificationRefresh.portDiscovered()
                            refreshPairingSetupNotification()
                        }
                    }
                },
                onConnectPortDiscovered = {
                    DiagnosticLogger.record(
                        this,
                        DiagnosticLogger.Stage.CONNECT_SERVICE_DISCOVERED,
                        DiagnosticLogger.Outcome.INFO
                    )
                },
                onFailure = { errorCode ->
                    mainHandler.post {
                        if (isPairingActive && !isPairingCompleting &&
                            discoveryNotificationGeneration.isCurrent(notificationGeneration)) {
                            notificationRefresh.discoveryFailed(errorCode)
                            refreshPairingSetupNotification()
                        }
                    }
                }
            )
            true
        } catch (e: Exception) {
            stopPairingDiscovery()
            adbManager.clearDiscoveredPorts()
            val localNetworkPermissionMissing = e is LocalNetworkPermissionRequiredException
            prefs.lastSetupIssue = if (localNetworkPermissionMissing) {
                SetupIssue.LOCAL_NETWORK_PERMISSION
            } else {
                SetupIssue.PAIRING_DISCOVERY
            }
            DiagnosticLogger.record(
                this,
                DiagnosticLogger.Stage.PAIRING_DISCOVERY,
                DiagnosticLogger.Outcome.FAILURE,
                e
            )
            Log.w(TAG, "Unable to start pairing discovery (${e.javaClass.simpleName})")
            PairingNotificationHelper.showPairingNotification(
                this,
                state = if (localNetworkPermissionMissing) {
                    PairingNotificationState.LOCAL_NETWORK_PERMISSION_REQUIRED
                } else {
                    PairingNotificationState.DISCOVERY_START_FAILED
                }
            )
            false
        }
    }

    private fun submitPairingCode(code: String) {
        if (pairingJob?.isActive == true || isPairingCompleting) return
        val pairingPort = adbManager.lastDiscoveredPairingPort?.takeIf { it in 1..65535 }

        if (!PairingCode.isValid(code)) {
            prefs.lastSetupIssue = SetupIssue.PAIRING_CODE
            DiagnosticLogger.record(
                this,
                DiagnosticLogger.Stage.PAIRING_CODE_SUBMITTED,
                DiagnosticLogger.Outcome.FAILURE
            )
            val notification = PairingNotificationHelper.buildPairingNotification(
                this,
                pairingPort = pairingPort,
                state = PairingNotificationState.INVALID_PAIRING_CODE
            )
            startForeground(
                PairingNotificationHelper.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
            return
        }

        isPairingCompleting = false

        prefs.lastSetupIssue = null
        DiagnosticLogger.record(
            this,
            DiagnosticLogger.Stage.PAIRING_CODE_SUBMITTED,
            DiagnosticLogger.Outcome.INFO
        )

        startForeground(
            PairingNotificationHelper.NOTIFICATION_ID,
            PairingNotificationHelper.buildProgressNotification(this),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )

        val generation = pairingGeneration.next()
        val job = serviceScope.launch(start = CoroutineStart.LAZY) {
            try {
                val timedResult = withTimeoutOrNull(25_000) {
                    var port = adbManager.lastDiscoveredPairingPort?.takeIf { it in 1..65535 }

                    if (port == null) {
                        for (i in 0 until 15) {
                            delay(200)
                            port = adbManager.lastDiscoveredPairingPort?.takeIf { it in 1..65535 }
                            if (port != null) break
                        }
                    }

                    if (port == null) {
                        currentCoroutineContext().ensureActive()
                        commitPairingSideEffect(generation) {
                            prefs.lastSetupIssue = SetupIssue.PAIRING_DISCOVERY
                            DiagnosticLogger.record(
                                this@PairingForegroundService,
                                DiagnosticLogger.Stage.PAIRING_DISCOVERY,
                                DiagnosticLogger.Outcome.TIMEOUT
                            )
                            PairingNotificationHelper.showPairingNotification(
                                this@PairingForegroundService,
                                state = PairingNotificationState.DISCOVERY_WAITING
                            )
                        }
                        return@withTimeoutOrNull true
                    }

                    currentCoroutineContext().ensureActive()
                    commitPairingSideEffect(generation) {
                        stopPairingDiscovery()
                        Log.i(TAG, "Attempting pairing using app-discovered endpoint")
                        DiagnosticLogger.record(
                            this@PairingForegroundService,
                            DiagnosticLogger.Stage.PAIRING,
                            DiagnosticLogger.Outcome.STARTED
                        )
                    }

                    val pairResult = adbManager.pairLocal(port, code)
                    currentCoroutineContext().ensureActive()

                    if (pairResult.isSuccess) {
                        commitPairingSideEffect(generation) {
                            DiagnosticLogger.record(
                                this@PairingForegroundService,
                                DiagnosticLogger.Stage.PAIRING,
                                DiagnosticLogger.Outcome.SUCCESS
                            )
                            Log.i(TAG, "Pairing successful; applying camera mute and permissions")
                        }

                        delay(300)
                        currentCoroutineContext().ensureActive()
                        commitPairingSideEffect(generation) {
                            DiagnosticLogger.record(
                                this@PairingForegroundService,
                                DiagnosticLogger.Stage.ADB_PERMISSION_AND_CSC_APPLY,
                                DiagnosticLogger.Outcome.STARTED
                            )
                        }

                        val muteResult = adbManager.applyCameraMuteViaAdb()
                        currentCoroutineContext().ensureActive()

                        if (muteResult.isSuccess) {
                            commitPairingSideEffect(generation) {
                                isPairingCompleting = true
                                prefs.lastSetupIssue = null
                                DiagnosticLogger.record(
                                    this@PairingForegroundService,
                                    DiagnosticLogger.Stage.ADB_PERMISSION_AND_CSC_APPLY,
                                    DiagnosticLogger.Outcome.SUCCESS
                                )
                                val wirelessCleanup = DeveloperOptionsManager.disableWirelessDebugging(
                                    this@PairingForegroundService
                                )
                                val wirelessDebuggingDisabled = wirelessCleanup.isSuccess
                                DiagnosticLogger.record(
                                    this@PairingForegroundService,
                                    DiagnosticLogger.Stage.WIRELESS_DEBUGGING_CLEANUP,
                                    if (wirelessDebuggingDisabled) {
                                        DiagnosticLogger.Outcome.SUCCESS
                                    } else {
                                        DiagnosticLogger.Outcome.FAILURE
                                    },
                                    wirelessCleanup.exceptionOrNull()
                                )
                                if (wirelessDebuggingDisabled) {
                                    Log.i(TAG, "Wireless debugging disabled after successful setup")
                                } else {
                                    wirelessCleanup.exceptionOrNull()?.let {
                                        logFailure("Unable to disable wireless debugging after setup", it)
                                    }
                                }

                                DiagnosticLogger.record(
                                    this@PairingForegroundService,
                                    DiagnosticLogger.Stage.PAIRING_WORKFLOW,
                                    DiagnosticLogger.Outcome.SUCCESS
                                )
                                Log.i(TAG, "Pairing workflow completed successfully")

                                postSuccessfulCompletion(generation, wirelessDebuggingDisabled)
                            }
                        } else {
                            commitPairingSideEffect(generation) {
                                prefs.lastSetupIssue = SetupIssue.CAMERA_APPLY
                                DiagnosticLogger.record(
                                    this@PairingForegroundService,
                                    DiagnosticLogger.Stage.ADB_PERMISSION_AND_CSC_APPLY,
                                    DiagnosticLogger.Outcome.FAILURE,
                                    muteResult.exceptionOrNull()
                                )
                                muteResult.exceptionOrNull()?.let {
                                    logFailure("Mute apply failed after pairing", it)
                                } ?: Log.w(TAG, "Mute apply failed after pairing")
                                PairingNotificationHelper.showPairingNotification(
                                    this@PairingForegroundService,
                                    state = PairingNotificationState.CAMERA_APPLY_FAILED
                                )
                                restartPairingDiscovery()
                            }
                        }
                    } else {
                        commitPairingSideEffect(generation) {
                            val localNetworkPermissionMissing =
                                pairResult.exceptionOrNull() is LocalNetworkPermissionRequiredException
                            prefs.lastSetupIssue = if (localNetworkPermissionMissing) {
                                SetupIssue.LOCAL_NETWORK_PERMISSION
                            } else {
                                SetupIssue.PAIRING_CONNECTION
                            }
                            DiagnosticLogger.record(
                                this@PairingForegroundService,
                                DiagnosticLogger.Stage.PAIRING,
                                DiagnosticLogger.Outcome.FAILURE,
                                pairResult.exceptionOrNull()
                            )
                            pairResult.exceptionOrNull()?.let {
                                logFailure("Pairing failed", it)
                            } ?: Log.w(TAG, "Pairing failed")
                            PairingNotificationHelper.showPairingNotification(
                                this@PairingForegroundService,
                                state = if (localNetworkPermissionMissing) {
                                    PairingNotificationState.LOCAL_NETWORK_PERMISSION_REQUIRED
                                } else {
                                    PairingNotificationState.PAIRING_FAILED
                                }
                            )
                            if (!localNetworkPermissionMissing) restartPairingDiscovery()
                        }
                    }
                    true
                }

                currentCoroutineContext().ensureActive()
                if (timedResult == null) {
                    commitPairingSideEffect(generation) {
                        prefs.lastSetupIssue = SetupIssue.PAIRING_TIMEOUT
                        DiagnosticLogger.record(
                            this@PairingForegroundService,
                            DiagnosticLogger.Stage.PAIRING_WORKFLOW,
                            DiagnosticLogger.Outcome.TIMEOUT
                        )
                        Log.w(TAG, "Pairing timed out after 25 seconds")
                        PairingNotificationHelper.showPairingNotification(
                            this@PairingForegroundService,
                            state = PairingNotificationState.PAIRING_TIMEOUT
                        )
                        restartPairingDiscovery()
                    }
                }
            } catch (e: CancellationException) {
                Log.i(TAG, "Pairing workflow cancelled during service shutdown")
                throw e
            } catch (e: Exception) {
                commitPairingSideEffect(generation) {
                    isPairingCompleting = false
                    val localNetworkPermissionMissing = e is LocalNetworkPermissionRequiredException
                    prefs.lastSetupIssue = if (localNetworkPermissionMissing) {
                        SetupIssue.LOCAL_NETWORK_PERMISSION
                    } else {
                        SetupIssue.PAIRING_CONNECTION
                    }
                    DiagnosticLogger.record(
                        this@PairingForegroundService,
                        DiagnosticLogger.Stage.PAIRING_WORKFLOW,
                        DiagnosticLogger.Outcome.FAILURE,
                        e
                    )
                    logFailure("Pairing error", e)
                    PairingNotificationHelper.showPairingNotification(
                        this@PairingForegroundService,
                        state = if (localNetworkPermissionMissing) {
                            PairingNotificationState.LOCAL_NETWORK_PERMISSION_REQUIRED
                        } else {
                            PairingNotificationState.PAIRING_ERROR
                        }
                    )
                    if (!localNetworkPermissionMissing) restartPairingDiscovery()
                }
            } finally {
                // 이전 세대의 늦은 finally가 새 pairingJob 참조를 지우지 못하게 한다.
                if (pairingGeneration.isCurrent(generation) && pairingJob === coroutineContext[Job]) {
                    pairingJob = null
                    refreshPairingSetupNotification()
                }
            }
        }
        pairingJob = job
        job.start()
    }

    private fun commitPairingSideEffect(generation: Long, block: () -> Unit) {
        if (!pairingGeneration.runIfCurrent(generation, block)) {
            throw CancellationException("Stale pairing workflow")
        }
    }

    private fun logFailure(summary: String, error: Throwable) {
        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebuggable) {
            Log.w(TAG, "$summary: ${error.message}", error)
        } else {
            Log.w(TAG, "$summary (${error.javaClass.simpleName})")
        }
    }

    private fun restartPairingDiscovery() {
        val state = readPairingSetupState()
        if (!state.developerOptionsEnabled || !state.wirelessDebuggingEnabled) return
        if (startPairingDiscovery()) {
            Log.i(TAG, "Restarted pairing discovery after unsuccessful attempt")
        }
    }

    private fun postSuccessfulCompletion(generation: Long, wirelessDebuggingDisabled: Boolean) {
        mainHandler.post {
            val accepted = pairingGeneration.runIfCurrent(generation) {
                Toast.makeText(
                    this,
                    if (wirelessDebuggingDisabled) {
                        getString(R.string.foreground_pairing_success_wireless_disabled)
                    } else {
                        getString(R.string.foreground_pairing_success_disable_wireless_manually)
                    },
                    Toast.LENGTH_LONG
                ).show()

                try {
                    val launchIntent = Intent(this, MainActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        )
                    }
                    startActivity(launchIntent)
                } catch (e: Exception) {
                    logFailure("Background activity launch restricted", e)
                }

                stopPairing(
                    showSuccess = true,
                    wirelessDebuggingDisabled = wirelessDebuggingDisabled
                )
            }
            if (!accepted) {
                Log.i(TAG, "Ignored stale pairing completion")
            }
        }
    }

    private fun stopPairing(showSuccess: Boolean, wirelessDebuggingDisabled: Boolean) {
        isPairingActive = false
        unregisterSetupObserver()
        pairingGeneration.invalidate()
        pairingJob?.cancel()
        pairingJob = null
        stopPairingDiscovery()
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (showSuccess) {
            PairingNotificationHelper.showSuccessNotification(
                this,
                wirelessDebuggingDisabled = wirelessDebuggingDisabled
            )
        }
        stopSelf()
    }

    override fun onDestroy() {
        isPairingActive = false
        unregisterSetupObserver()
        pairingGeneration.invalidate()
        pairingJob?.cancel()
        pairingJob = null
        serviceScope.cancel()
        stopPairingDiscovery()
        super.onDestroy()
    }

    private fun readPairingSetupState() = PairingSetupState(
        developerOptionsEnabled = DeveloperOptionsManager.isDeveloperOptionsEnabled(this),
        wirelessDebuggingEnabled = DeveloperOptionsManager.isWirelessDebuggingEnabled(this)
    )

    private fun refreshPairingSetupNotification() {
        if (!isPairingActive || isPairingCompleting) return
        val state = readPairingSetupState()
        notificationRefresh.observeSetup(state)
        when (val update = notificationRefresh.takeUpdate(blocked = pairingJob != null)) {
            null -> return
            is PairingNotificationRefreshState.Update.DiscoveryFailed -> {
                stopPairingDiscovery()
                adbManager.clearDiscoveredPorts()
                prefs.lastSetupIssue = SetupIssue.PAIRING_DISCOVERY
                DiagnosticLogger.record(
                    this,
                    DiagnosticLogger.Stage.PAIRING_DISCOVERY,
                    DiagnosticLogger.Outcome.FAILURE
                )
                Log.w(TAG, "Asynchronous pairing discovery failed (${update.errorCode})")
                PairingNotificationHelper.showPairingNotification(
                    this,
                    state = PairingNotificationState.DISCOVERY_START_FAILED
                )
                return
            }
            is PairingNotificationRefreshState.Update.Setup -> {
                if (!state.developerOptionsEnabled || !state.wirelessDebuggingEnabled) {
                    stopPairingDiscovery()
                    adbManager.clearDiscoveredPorts()
                } else {
                    // Restart even after an off/on cycle during a pairing attempt.
                    if (!startPairingDiscovery()) return
                }
            }
            PairingNotificationRefreshState.Update.PortDiscovered -> Unit
        }
        if (prefs.lastSetupIssue == SetupIssue.LOCAL_NETWORK_PERMISSION) return

        val port = adbManager.lastDiscoveredPairingPort?.takeIf {
            state.developerOptionsEnabled && state.wirelessDebuggingEnabled && it in 1..65535
        }
        if (port != null && prefs.lastSetupIssue == SetupIssue.PAIRING_DISCOVERY) {
            prefs.lastSetupIssue = null
        }
        PairingNotificationHelper.showPairingNotification(
            this,
            pairingPort = port,
            state = if (port == null) state.notificationState else null,
            isDevOptionsOff = !state.developerOptionsEnabled
        )
    }

    private fun stopPairingDiscovery() {
        discoveryNotificationGeneration.invalidate()
        notificationRefresh.clearDiscovery()
        adbManager.stopPairingDiscovery()
    }

    private fun registerSetupObserver() {
        if (isSetupObserverRegistered) return
        try {
            // The same observer stays registered for both prerequisites throughout setup.
            isSetupObserverRegistered = true
            contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED),
                false,
                setupObserver
            )
            contentResolver.registerContentObserver(
                DeveloperOptionsManager.wirelessDebuggingUri,
                false,
                setupObserver
            )
        } catch (e: Exception) {
            unregisterSetupObserver()
            Log.w(TAG, "Unable to observe pairing settings (${e.javaClass.simpleName})")
        }
    }

    private fun unregisterSetupObserver() {
        if (!isSetupObserverRegistered) return
        try {
            contentResolver.unregisterContentObserver(setupObserver)
        } catch (_: Exception) {
        }
        isSetupObserverRegistered = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
