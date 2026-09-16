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
import io.github.hoons1994.shuttersoundzero.core.CscMuteManager
import io.github.hoons1994.shuttersoundzero.core.DeveloperOptionsManager
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
        private const val ACTION_COMPLETE = "io.github.hoons1994.shuttersoundzero.action.COMPLETE_PAIRING"
        private const val ACTION_SUBMIT_CODE = "io.github.hoons1994.shuttersoundzero.action.SUBMIT_PAIRING_CODE"
        private const val EXTRA_DEV_OPTIONS_OFF = "dev_options_off"
        private const val EXTRA_PAIRING_CODE = "pairing_code"
        private const val EXTRA_WIRELESS_DEBUGGING_DISABLED = "wireless_debugging_disabled"

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
            return Intent(context, PairingForegroundService::class.java).setAction(ACTION_STOP)
        }

        fun complete(context: Context, wirelessDebuggingDisabled: Boolean) {
            try {
                context.startService(
                    Intent(context, PairingForegroundService::class.java).apply {
                        action = ACTION_COMPLETE
                        putExtra(EXTRA_WIRELESS_DEBUGGING_DISABLED, wirelessDebuggingDisabled)
                    }
                )
            } catch (_: Exception) {
                PairingNotificationHelper.cancelNotification(context)
                PairingNotificationHelper.showSuccessNotification(
                    context,
                    wirelessDebuggingDisabled = wirelessDebuggingDisabled
                )
            }
        }
    }

    private val adbManager by lazy { StandaloneAdbManager.getInstance(this) }
    private val prefs by lazy { PreferencesRepository.getInstance(this) }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pairingGeneration = OperationGeneration()
    private var pairingJob: Job? = null
    private var isDeveloperOptionsObserverRegistered = false

    private val developerOptionsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (!CscMuteManager.isDeveloperOptionsEnabled(this@PairingForegroundService)) return

            PairingNotificationHelper.showPairingNotification(
                this@PairingForegroundService,
                state = PairingNotificationState.DEVELOPER_OPTIONS_READY,
                isDevOptionsOff = true
            )
            unregisterDeveloperOptionsObserver()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startPairing(intent.getBooleanExtra(EXTRA_DEV_OPTIONS_OFF, false))
            ACTION_SUBMIT_CODE -> submitPairingCode(intent.getStringExtra(EXTRA_PAIRING_CODE).orEmpty().trim())
            ACTION_STOP -> stopPairing(showSuccess = false, wirelessDebuggingDisabled = false)
            ACTION_COMPLETE -> stopPairing(
                showSuccess = true,
                wirelessDebuggingDisabled = intent.getBooleanExtra(
                    EXTRA_WIRELESS_DEBUGGING_DISABLED,
                    false
                )
            )
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun startPairing(isDevOptionsOff: Boolean) {
        // 새 1회 설정 시작은 이전 코드 제출 작업의 완료보다 우선한다.
        pairingGeneration.invalidate()
        pairingJob?.cancel()
        pairingJob = null

        prefs.lastSetupIssue = null
        DiagnosticLogger.record(
            this,
            DiagnosticLogger.Stage.PAIRING_DISCOVERY,
            DiagnosticLogger.Outcome.STARTED
        )

        val notification = PairingNotificationHelper.buildPairingNotification(
            this,
            isDevOptionsOff = isDevOptionsOff
        )
        startForeground(
            PairingNotificationHelper.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )

        if (isDevOptionsOff) registerDeveloperOptionsObserver()
        else unregisterDeveloperOptionsObserver()

        try {
            adbManager.startPairingDiscovery(
                onPairingPortDiscovered = { port ->
                    DiagnosticLogger.record(
                        this,
                        DiagnosticLogger.Stage.PAIRING_SERVICE_DISCOVERED,
                        DiagnosticLogger.Outcome.INFO
                    )
                    PairingNotificationHelper.showPairingNotification(this, pairingPort = port)
                },
                onConnectPortDiscovered = {
                    DiagnosticLogger.record(
                        this,
                        DiagnosticLogger.Stage.CONNECT_SERVICE_DISCOVERED,
                        DiagnosticLogger.Outcome.INFO
                    )
                }
            )
        } catch (e: Exception) {
            prefs.lastSetupIssue = SetupIssue.PAIRING_DISCOVERY
            DiagnosticLogger.record(
                this,
                DiagnosticLogger.Stage.PAIRING_DISCOVERY,
                DiagnosticLogger.Outcome.FAILURE,
                e
            )
            Log.w(TAG, "Unable to start pairing discovery (${e.javaClass.simpleName})")
            PairingNotificationHelper.showPairingNotification(
                this,
                state = PairingNotificationState.DISCOVERY_START_FAILED
            )
        }
    }

    private fun submitPairingCode(code: String) {
        val pairingPort = adbManager.lastDiscoveredPairingPort?.takeIf { it in 1..65535 }

        if (code.length != 6 || !code.all(Char::isDigit)) {
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

        if (pairingJob?.isActive == true) return

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
                        adbManager.stopPairingDiscovery()
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

                                Handler(Looper.getMainLooper()).post {
                                    Toast.makeText(
                                        this@PairingForegroundService,
                                        if (wirelessDebuggingDisabled) {
                                            "✨ 설정 완료! 카메라 무음 설정을 적용하고 무선 디버깅도 껐습니다."
                                        } else {
                                            "✨ 카메라 무음 설정은 완료됐습니다. 무선 디버깅은 직접 꺼 주세요."
                                        },
                                        Toast.LENGTH_LONG
                                    ).show()
                                }

                                try {
                                    val launchIntent = Intent(
                                        this@PairingForegroundService,
                                        MainActivity::class.java
                                    ).apply {
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

                                complete(
                                    this@PairingForegroundService,
                                    wirelessDebuggingDisabled = wirelessDebuggingDisabled
                                )
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
                            }
                        }
                    } else {
                        commitPairingSideEffect(generation) {
                            prefs.lastSetupIssue = SetupIssue.PAIRING_CODE
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
                                pairingPort = port,
                                state = PairingNotificationState.PAIRING_FAILED
                            )
                        }
                    }
                    true
                }

                currentCoroutineContext().ensureActive()
                if (timedResult == null) {
                    commitPairingSideEffect(generation) {
                        prefs.lastSetupIssue = SetupIssue.PAIRING_CODE
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
                    }
                }
            } catch (e: CancellationException) {
                Log.i(TAG, "Pairing workflow cancelled during service shutdown")
                throw e
            } catch (e: Exception) {
                commitPairingSideEffect(generation) {
                    prefs.lastSetupIssue = SetupIssue.PAIRING_CODE
                    DiagnosticLogger.record(
                        this@PairingForegroundService,
                        DiagnosticLogger.Stage.PAIRING_WORKFLOW,
                        DiagnosticLogger.Outcome.FAILURE,
                        e
                    )
                    logFailure("Pairing error", e)
                    PairingNotificationHelper.showPairingNotification(
                        this@PairingForegroundService,
                        state = PairingNotificationState.PAIRING_ERROR
                    )
                }
            } finally {
                // 이전 세대의 늦은 finally가 새 pairingJob 참조를 지우지 못하게 한다.
                if (pairingGeneration.isCurrent(generation) && pairingJob === coroutineContext[Job]) {
                    pairingJob = null
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

    private fun stopPairing(showSuccess: Boolean, wirelessDebuggingDisabled: Boolean) {
        unregisterDeveloperOptionsObserver()
        pairingGeneration.invalidate()
        pairingJob?.cancel()
        pairingJob = null
        adbManager.stopPairingDiscovery()
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
        unregisterDeveloperOptionsObserver()
        pairingGeneration.invalidate()
        pairingJob?.cancel()
        pairingJob = null
        serviceScope.cancel()
        adbManager.stopPairingDiscovery()
        super.onDestroy()
    }

    private fun registerDeveloperOptionsObserver() {
        if (isDeveloperOptionsObserverRegistered) return
        try {
            contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED),
                false,
                developerOptionsObserver
            )
            isDeveloperOptionsObserverRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Unable to observe developer options (${e.javaClass.simpleName})")
        }
    }

    private fun unregisterDeveloperOptionsObserver() {
        if (!isDeveloperOptionsObserverRegistered) return
        try {
            contentResolver.unregisterContentObserver(developerOptionsObserver)
        } catch (_: Exception) {
        }
        isDeveloperOptionsObserverRegistered = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
