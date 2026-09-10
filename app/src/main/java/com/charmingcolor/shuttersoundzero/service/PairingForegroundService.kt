package com.charmingcolor.shuttersoundzero.service

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
import com.charmingcolor.shuttersoundzero.MainActivity
import com.charmingcolor.shuttersoundzero.core.CscMuteManager
import com.charmingcolor.shuttersoundzero.core.DeveloperOptionsManager
import com.charmingcolor.shuttersoundzero.core.adb.StandaloneAdbManager
import com.charmingcolor.shuttersoundzero.diagnostics.DiagnosticLogger
import com.charmingcolor.shuttersoundzero.ui.notification.PairingNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class PairingForegroundService : Service() {
    companion object {
        private const val TAG = "PairingForegroundService"
        private const val ACTION_START = "com.charmingcolor.shuttersoundzero.action.START_PAIRING"
        private const val ACTION_STOP = "com.charmingcolor.shuttersoundzero.action.STOP_PAIRING"
        private const val ACTION_COMPLETE = "com.charmingcolor.shuttersoundzero.action.COMPLETE_PAIRING"
        private const val ACTION_SUBMIT_CODE = "com.charmingcolor.shuttersoundzero.action.SUBMIT_PAIRING_CODE"
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
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pairingJob: Job? = null
    private var isDeveloperOptionsObserverRegistered = false

    private val developerOptionsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (!CscMuteManager.isDeveloperOptionsEnabled(this@PairingForegroundService)) return

            PairingNotificationHelper.showPairingNotification(
                this@PairingForegroundService,
                statusMessage = "개발자 옵션 활성화 완료",
                isDevOptionsOff = true,
                statusDetail = "앱으로 돌아가 [권한 요청]을 누르세요."
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
            DiagnosticLogger.record(
                this,
                DiagnosticLogger.Stage.PAIRING_DISCOVERY,
                DiagnosticLogger.Outcome.FAILURE,
                e
            )
            Log.w(TAG, "Unable to start pairing discovery (${e.javaClass.simpleName})")
            PairingNotificationHelper.showPairingNotification(
                this,
                statusMessage = "⚠️ 무선 페어링 탐색을 시작할 수 없습니다."
            )
        }
    }

    private fun submitPairingCode(code: String) {
        val pairingPort = adbManager.lastDiscoveredPairingPort?.takeIf { it in 1..65535 }

        if (code.length != 6 || !code.all(Char::isDigit)) {
            DiagnosticLogger.record(
                this,
                DiagnosticLogger.Stage.PAIRING_CODE_SUBMITTED,
                DiagnosticLogger.Outcome.FAILURE
            )
            val notification = PairingNotificationHelper.buildPairingNotification(
                this,
                pairingPort = pairingPort,
                statusMessage = "⚠️ 숫자 6자리 페어링 코드를 정확히 입력해 주세요."
            )
            startForeground(
                PairingNotificationHelper.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
            return
        }

        if (pairingJob?.isActive == true) return

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

        pairingJob = serviceScope.launch {
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
                        DiagnosticLogger.record(
                            this@PairingForegroundService,
                            DiagnosticLogger.Stage.PAIRING_DISCOVERY,
                            DiagnosticLogger.Outcome.TIMEOUT
                        )
                        PairingNotificationHelper.showPairingNotification(
                            this@PairingForegroundService,
                            null,
                            "⏳ 포트 탐색 대기 중: 화면의 6자리 코드를 다시 입력해 주세요."
                        )
                        return@withTimeoutOrNull true
                    }

                    adbManager.stopPairingDiscovery()
                    Log.i(TAG, "Attempting pairing using app-discovered endpoint")
                    DiagnosticLogger.record(
                        this@PairingForegroundService,
                        DiagnosticLogger.Stage.PAIRING,
                        DiagnosticLogger.Outcome.STARTED
                    )
                    val pairResult = adbManager.pairLocal(port, code)

                    if (pairResult.isSuccess) {
                        DiagnosticLogger.record(
                            this@PairingForegroundService,
                            DiagnosticLogger.Stage.PAIRING,
                            DiagnosticLogger.Outcome.SUCCESS
                        )
                        Log.i(TAG, "Pairing successful; applying camera mute and permissions")
                        delay(300)

                        DiagnosticLogger.record(
                            this@PairingForegroundService,
                            DiagnosticLogger.Stage.ADB_PERMISSION_AND_CSC_APPLY,
                            DiagnosticLogger.Outcome.STARTED
                        )
                        val muteResult = adbManager.applyCameraMuteViaAdb()
                        if (muteResult.isSuccess) {
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
                            complete(
                                this@PairingForegroundService,
                                wirelessDebuggingDisabled = wirelessDebuggingDisabled
                            )

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
                        } else {
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
                                null,
                                "⚠️ 페어링은 완료됐지만 무음 설정 적용에 실패했습니다. 무선 디버깅 상태를 확인한 뒤 다시 시도해 주세요."
                            )
                        }
                    } else {
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
                            port,
                            "❌ 페어링에 실패했습니다. 화면의 6자리 코드를 확인해 다시 입력해 주세요."
                        )
                    }
                    true
                }

                if (timedResult == null) {
                    DiagnosticLogger.record(
                        this@PairingForegroundService,
                        DiagnosticLogger.Stage.PAIRING_WORKFLOW,
                        DiagnosticLogger.Outcome.TIMEOUT
                    )
                    Log.w(TAG, "Pairing timed out after 25 seconds")
                    PairingNotificationHelper.showPairingNotification(
                        this@PairingForegroundService,
                        null,
                        "⏱️ 시간 초과: 코드를 다시 입력해 주세요."
                    )
                }
            } catch (e: java.util.concurrent.CancellationException) {
                // Successful completion stops the foreground service, which cancels its coroutine
                // scope. Cancellation during that shutdown is expected and must not overwrite the
                // success notification with a false pairing-error notification.
                Log.i(TAG, "Pairing workflow cancelled during service shutdown")
                throw e
            } catch (e: Exception) {
                DiagnosticLogger.record(
                    this@PairingForegroundService,
                    DiagnosticLogger.Stage.PAIRING_WORKFLOW,
                    DiagnosticLogger.Outcome.FAILURE,
                    e
                )
                logFailure("Pairing error", e)
                PairingNotificationHelper.showPairingNotification(
                    this@PairingForegroundService,
                    null,
                    "❌ 페어링 중 오류가 발생했습니다. 무선 디버깅 상태를 확인하고 다시 시도해 주세요."
                )
            } finally {
                pairingJob = null
            }
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
        pairingJob?.cancel()
        pairingJob = null
        adbManager.stopPairingDiscovery()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        if (showSuccess) {
            PairingNotificationHelper.showSuccessNotification(
                this,
                wirelessDebuggingDisabled = wirelessDebuggingDisabled
            )
        } else {
            PairingNotificationHelper.cancelNotification(this)
        }
    }

    private fun registerDeveloperOptionsObserver() {
        if (isDeveloperOptionsObserverRegistered) return
        contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED),
            false,
            developerOptionsObserver
        )
        isDeveloperOptionsObserverRegistered = true
    }

    private fun unregisterDeveloperOptionsObserver() {
        if (!isDeveloperOptionsObserverRegistered) return
        try {
            contentResolver.unregisterContentObserver(developerOptionsObserver)
        } catch (_: Exception) {
        }
        isDeveloperOptionsObserverRegistered = false
    }

    override fun onDestroy() {
        unregisterDeveloperOptionsObserver()
        pairingJob?.cancel()
        pairingJob = null
        adbManager.stopPairingDiscovery()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
