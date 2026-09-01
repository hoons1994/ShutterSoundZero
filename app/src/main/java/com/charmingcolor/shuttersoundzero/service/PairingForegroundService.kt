package com.charmingcolor.shuttersoundzero.service

import android.app.Service
import android.database.ContentObserver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.charmingcolor.shuttersoundzero.core.CscMuteManager
import com.charmingcolor.shuttersoundzero.core.adb.StandaloneAdbManager
import com.charmingcolor.shuttersoundzero.ui.notification.PairingNotificationHelper

class PairingForegroundService : Service() {
    companion object {
        private const val TAG = "PairingForegroundService"
        private const val ACTION_START = "com.charmingcolor.shuttersoundzero.action.START_PAIRING"
        private const val ACTION_STOP = "com.charmingcolor.shuttersoundzero.action.STOP_PAIRING"
        private const val ACTION_COMPLETE = "com.charmingcolor.shuttersoundzero.action.COMPLETE_PAIRING"
        private const val EXTRA_DEV_OPTIONS_OFF = "dev_options_off"

        fun start(context: Context, isDevOptionsOff: Boolean) {
            val intent = Intent(context, PairingForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DEV_OPTIONS_OFF, isDevOptionsOff)
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
            Log.w(TAG, "Unable to start pairing discovery: ${e.message}")
            PairingNotificationHelper.showPairingNotification(
                this,
                statusMessage = "⚠️ 무선 페어링 탐색을 시작할 수 없습니다."
            )
        }
    }

    private fun stopPairing(showSuccess: Boolean) {
        unregisterDeveloperOptionsObserver()
        adbManager.stopPairingDiscovery()
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (showSuccess) PairingNotificationHelper.showSuccessNotification(this)
        stopSelf()
    }

    override fun onDestroy() {
        unregisterDeveloperOptionsObserver()
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
            Log.w(TAG, "Unable to observe developer options: ${e.message}")
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
