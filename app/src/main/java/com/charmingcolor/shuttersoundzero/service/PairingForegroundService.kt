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
import com.charmingcolor.shuttersoundzero.core.adb.StandaloneAdbManager
import com.charmingcolor.shuttersoundzero.ui.notification.PairingNotificationHelper
import com.charmingcolor.shuttersoundzero.ui.pairing.PairingCodeOverlay
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
        private const val ACTION_SHOW_CODE_INPUT = "com.charmingcolor.shuttersoundzero.action.SHOW_PAIRING_CODE_INPUT"
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

        fun showCodeInputIntent(context: Context): Intent {
            return Intent(context, PairingForegroundService::class.java).setAction(ACTION_SHOW_CODE_INPUT)
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

        fun complete(context: Context) {
            try {
                context.startService(
                    Intent(context, PairingForegroundService::class.java).setAction(ACTION_COMPLETE)
                )
            } catch (_: Exception) {
                PairingNotificationHelper.cancelNotification(context)
                PairingNotificationHelper.showSuccessNotification(context)
            }
        }
    }

    private val adbManager by lazy { StandaloneAdbManager.getInstance(this) }
    private val pairingCodeOverlay by lazy { PairingCodeOverlay(this) }
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
            ACTION_SHOW_CODE_INPUT -> showPairingCodeInput()
            ACTION_STOP -> stopPairing(showSuccess = false)
            ACTION_COMPLETE -> stopPairing(showSuccess = true)
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun startPairing(isDevOptionsOff: Boolean) {
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
                    PairingNotificationHelper.showPairingNotification(this, pairingPort = port)
                },
                onConnectPortDiscovered = { _ -> }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Unable to start pairing discovery (${e.javaClass.simpleName})")
            PairingNotificationHelper.showPairingNotification(
                this,
                statusMessage = "⚠️ 무선 페어링 탐색을 시작할 수 없습니다."
            )
        }
    }

    private fun showPairingCodeInput() {
        val pairingPort = adbManager.lastDiscoveredPairingPort?.takeIf { it in 1..65535 }
        if (pairingPort == null) {
            Toast.makeText(
                this,
                "먼저 [페어링 코드로 기기 페어링]을 눌러 6자리 코드 화면을 띄워 주세요.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "간편 입력 권한이 없어 기존 알림 입력 방식을 사용합니다. 알림을 펼쳐 [코드 입력]을 눌러 주세요.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        pairingCodeOverlay.show { code ->
            submitPairingCode(code)
        }
    }

    private fun submitPairingCode(code: String) {
        val pairingPort = adbManager.lastDiscoveredPairingPort?.takeIf { it in 1..65535 }

        if (code.length != 6 || !code.all(Char::isDigit)) {
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

        pairingCodeOverlay.dismiss()
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
                        PairingNotificationHelper.showPairingNotification(
                            this@PairingForegroundService,
                            null,
                            "⏳ 포트 탐색 대기 중: 화면의 6자리 코드를 다시 입력해 주세요."
                        )
                        return@withTimeoutOrNull true
                    }

                    adbManager.stopPairingDiscovery()
                    Log.i(TAG, "Attempting pairing using app-discovered endpoint")
                    val pairResult = adbManager.pairLocal(port, code)

                    if (pairResult.isSuccess) {
                        Log.i(TAG, "Pairing successful; applying camera mute and permissions")
                        delay(300)

                        val muteResult = adbManager.applyCameraMuteViaAdb()
                        if (muteResult.isSuccess) {
                            Log.i(TAG, "Pairing workflow completed successfully")
                            complete(this@PairingForegroundService)

                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(
                                    this@PairingForegroundService,
                                    "✨ 셔터음 제로: 셔터음 무음화 연동이 완료되었습니다!",
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
                    Log.w(TAG, "Pairing timed out after 25 seconds")
                    PairingNotificationHelper.showPairingNotification(
                        this@PairingForegroundService,
                        null,
                        "⏱️ 시간 초과: 코드를 다시 입력해 주세요."
                    )
                }
            } catch (e: Exception) {
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

    private fun stopPairing(showSuccess: Boolean) {
        unregisterDeveloperOptionsObserver()
        pairingCodeOverlay.dismiss()
        pairingJob?.cancel()
        pairingJob = null
        adbManager.stopPairingDiscovery()
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (showSuccess) PairingNotificationHelper.showSuccessNotification(this)
        stopSelf()
    }

    override fun onDestroy() {
        unregisterDeveloperOptionsObserver()
        pairingCodeOverlay.dismiss()
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