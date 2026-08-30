package com.hoons.shutterzero.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hoons.shutterzero.MainActivity
import com.hoons.shutterzero.R
import com.hoons.shutterzero.core.CscMuteManager
import com.hoons.shutterzero.data.PreferencesRepository

/**
 * 스마트폰 재부팅, 펌웨어(One UI) 업데이트, 앱 업데이트 시 CSC 셔터음 설정을 자동으로 유지하고 복원하는 리시버
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
        private const val CHANNEL_ID = "firmware_updates"
        private const val NOTIFICATION_ID = 2001
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "Received broadcast intent: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON") {

            val prefs = PreferencesRepository.getInstance(context)

            // 펌웨어(OS/One UI) 업데이트 감지 로직
            val currentFingerprint = Build.FINGERPRINT
            val lastFingerprint = prefs.lastFirmwareFingerprint
            val isFirmwareUpdated = prefs.isFirmwareUpdateCheckEnabled &&
                    lastFingerprint != null &&
                    lastFingerprint != currentFingerprint

            // 최신 핑거프린트로 갱신
            prefs.lastFirmwareFingerprint = currentFingerprint

            if (isFirmwareUpdated) {
                Log.i(TAG, "Firmware update detected! Old: $lastFingerprint -> New: $currentFingerprint")
            }

            if (prefs.isAutoRestoreOnBootEnabled && prefs.shouldMuteOnBoot) {
                // 이미 기기 설정이 0(무음)으로 유지되어 있다면 불필요한 설정 쓰기 생략
                val alreadyMuted = CscMuteManager.isCscShutterSoundMuted(context)
                if (alreadyMuted) {
                    Log.i(TAG, "CSC camera mute is already active (0) after reboot. No action required.")
                    return
                }

                if (CscMuteManager.hasWritePermission(context)) {
                    val result = CscMuteManager.setCscShutterSoundMuted(context, true)
                    result.onSuccess {
                        Log.i(TAG, "Successfully restored CSC camera mute after boot/update")
                        if (isFirmwareUpdated) {
                            showNotification(
                                context,
                                "시스템 펌웨어 업데이트가 감지되어 셔터음 무음 설정을 자동으로 복원했습니다."
                            )
                        }
                    }.onFailure { error ->
                        Log.w(TAG, "Direct restore on boot failed: ${error.message}")
                        if (isFirmwareUpdated) {
                            showNotification(
                                context,
                                "시스템 업데이트로 셔터음 설정이 초기화되었습니다. 앱을 열어 복원해 주세요."
                            )
                        }
                    }
                } else {
                    Log.w(TAG, "Cannot restore CSC mute on boot: Missing permission")
                    if (isFirmwareUpdated) {
                        showNotification(
                            context,
                            "시스템 업데이트가 감지되었습니다. 셔터음 무음 유지를 위해 앱을 열어 상태를 확인해 주세요."
                        )
                    }
                }
            }
        }
    }

    private fun showNotification(context: Context, message: String) {
        try {
            createNotificationChannel(context)

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("셔터 제로")
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            val manager = NotificationManagerCompat.from(context)
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED ||
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            ) {
                manager.notify(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to post notification: ${e.message}")
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "시스템 업데이트 복원 알림",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "펌웨어 업데이트 후 셔터음 무음 설정 자동 복원 안내"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }
}
