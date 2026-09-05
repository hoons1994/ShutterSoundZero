package com.charmingcolor.shuttersoundzero.receiver

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
import com.charmingcolor.shuttersoundzero.MainActivity
import com.charmingcolor.shuttersoundzero.R
import com.charmingcolor.shuttersoundzero.core.CscMuteManager
import com.charmingcolor.shuttersoundzero.data.PreferencesRepository

/**
 * 기기 재부팅과 앱 업데이트 시 현재 소프트웨어 빌드를 확인한다.
 *
 * 소프트웨어 업데이트 감지는 CSC 복원 여부와 독립적으로 동작한다. 업데이트가 감지되면
 * 무음 설정이 그대로 유지된 경우에도 사용자에게 결과를 알린다. 일반 재부팅에서는
 * 사용자가 무음 상태를 사용 중인 경우에만 조용히 self-healing을 시도한다.
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"

        // 기존 사용자 알림 채널 설정을 보존하기 위해 ID는 변경하지 않는다.
        private const val CHANNEL_ID = "firmware_updates"
        private const val NOTIFICATION_ID = 2001
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (!isSupportedAction(action)) return

        Log.i(TAG, "Received supported boot/update broadcast")

        val prefs = PreferencesRepository.getInstance(context)
        val currentFingerprint = Build.FINGERPRINT
        val previousFingerprint = prefs.lastSoftwareFingerprint
        val updateCheckEnabled = prefs.isSoftwareUpdateCheckEnabled
        val isSoftwareUpdated = updateCheckEnabled &&
            previousFingerprint != null &&
            previousFingerprint != currentFingerprint

        if (updateCheckEnabled) {
            if (previousFingerprint == null) {
                // 기준값이 없으면 현재 빌드를 기준으로만 저장한다. 다음 업데이트부터 비교 가능하다.
                prefs.lastSoftwareFingerprint = currentFingerprint
                Log.i(TAG, "Initialized software update baseline")
            } else if (previousFingerprint != currentFingerprint) {
                // 같은 업데이트를 다음 부팅에서 다시 알리지 않도록 즉시 최신 값으로 갱신한다.
                prefs.lastSoftwareFingerprint = currentFingerprint
                Log.i(TAG, "Software update detected")
            }
        }

        if (isSoftwareUpdated) {
            handleDetectedSoftwareUpdate(context, prefs)
            return
        }

        // 일반 재부팅에서는 사용자에게 별도 스위치를 요구하지 않고 필요한 경우에만 조용히 복원한다.
        restoreMuteSilentlyIfNeeded(context, prefs)
    }

    private fun handleDetectedSoftwareUpdate(
        context: Context,
        prefs: PreferencesRepository
    ) {
        if (!prefs.shouldMuteOnBoot) {
            showNotification(
                context,
                "소프트웨어 업데이트가 감지되었습니다. 카메라 무음 기능은 현재 사용 중이 아닙니다."
            )
            return
        }

        if (prefs.isPermissionRevokedByUser) {
            Log.i(TAG, "Skipping restore because permission linkage was revoked by user")
            showNotification(
                context,
                "소프트웨어 업데이트가 감지되었습니다. 권한 연동이 해제되어 있어 카메라 무음 설정을 확인해 주세요."
            )
            return
        }

        if (CscMuteManager.isCscShutterSoundMuted(context)) {
            Log.i(TAG, "CSC camera mute remained active after software update")
            showNotification(
                context,
                "소프트웨어 업데이트가 감지되었습니다. 카메라 무음 설정은 정상적으로 유지되고 있습니다."
            )
            return
        }

        if (!CscMuteManager.hasWritePermission(context)) {
            Log.w(TAG, "Cannot restore CSC mute after software update: missing permission")
            showNotification(
                context,
                "소프트웨어 업데이트 후 카메라 무음 설정을 확인해야 합니다. 앱을 열어 상태를 확인해 주세요."
            )
            return
        }

        CscMuteManager.setCscShutterSoundMuted(context, true)
            .onSuccess {
                Log.i(TAG, "Restored CSC camera mute after software update")
                showNotification(
                    context,
                    "소프트웨어 업데이트 후 카메라 무음 설정이 초기화되어 자동으로 복원했습니다."
                )
            }
            .onFailure { error ->
                Log.w(TAG, "Restore after software update failed (${error.javaClass.simpleName})")
                showNotification(
                    context,
                    "소프트웨어 업데이트 후 카메라 무음 설정을 복원하지 못했습니다. 앱을 열어 상태를 확인해 주세요."
                )
            }
    }

    private fun restoreMuteSilentlyIfNeeded(
        context: Context,
        prefs: PreferencesRepository
    ) {
        if (!prefs.shouldMuteOnBoot || prefs.isPermissionRevokedByUser) return
        if (CscMuteManager.isCscShutterSoundMuted(context)) return
        if (!CscMuteManager.hasWritePermission(context)) {
            Log.w(TAG, "Cannot restore CSC mute after boot: missing permission")
            return
        }

        CscMuteManager.setCscShutterSoundMuted(context, true)
            .onSuccess {
                Log.i(TAG, "Restored CSC camera mute after reboot")
            }
            .onFailure { error ->
                Log.w(TAG, "Restore after boot failed (${error.javaClass.simpleName})")
            }
    }

    private fun isSupportedAction(action: String?): Boolean {
        return action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
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
                .setSmallIcon(R.drawable.ic_qs_camera_mute)
                .setContentTitle("셔터음 제로")
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            val notificationsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

            if (notificationsAllowed) {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            } else {
                Log.i(TAG, "Software update notification suppressed because notification permission is missing")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to post software update notification (${e.javaClass.simpleName})")
        }
    }

    private fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "소프트웨어 업데이트 알림",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "소프트웨어 업데이트 후 카메라 무음 설정 상태 및 복원 안내"
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.createNotificationChannel(channel)
    }
}
