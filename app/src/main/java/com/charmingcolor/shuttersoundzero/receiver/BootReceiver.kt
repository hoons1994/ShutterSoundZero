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
import com.charmingcolor.shuttersoundzero.core.adb.AdbKeyHelper
import com.charmingcolor.shuttersoundzero.core.adb.AdbStateCleanupPolicy
import com.charmingcolor.shuttersoundzero.data.PreferencesRepository

/**
 * 기기 재부팅과 앱 업데이트 시 현재 소프트웨어 빌드와 실제 CSC 상태를 확인한다.
 *
 * 비공개 Settings.System CSC 키는 일반 앱 UID에서 직접 수정할 수 없으므로,
 * 재부팅·소프트웨어 업데이트로 무음 상태가 초기화된 경우 앱이 백그라운드에서 자동 재적용하지 않는다.
 * 대신 실제 상태와 권한 연동 상태를 확인해 사용자가 무선 디버깅을 켜고 다시 적용하도록 안내한다.
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"

        // 기존 사용자 알림 채널 설정을 보존하기 위해 채널 ID는 변경하지 않는다.
        private const val CHANNEL_ID = "firmware_updates"
        // 페어링 foreground 알림(2001)과 별도 identity를 사용한다.
        private const val NOTIFICATION_ID = 2002
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (!isSupportedAction(action)) return

        Log.i(TAG, "Received supported boot/update broadcast")

        val prefs = PreferencesRepository.getInstance(context)
        val hasWritePermissionAtReceive = CscMuteManager.hasWritePermission(context)
        prefs.ensureAdbLinkageHistoryMigration(hasWritePermissionAtReceive)

        val currentFingerprint = Build.FINGERPRINT
        val previousFingerprint = prefs.lastSoftwareFingerprint
        val updateCheckEnabled = prefs.isSoftwareUpdateCheckEnabled
        val hadAdbLinkageEvidence = prefs.hasAdbLinkageHistory
        val isSoftwareUpdated = updateCheckEnabled &&
            previousFingerprint != null &&
            previousFingerprint != currentFingerprint

        if (updateCheckEnabled) {
            if (previousFingerprint == null) {
                prefs.lastSoftwareFingerprint = currentFingerprint
                Log.i(TAG, "Initialized software update baseline")
            } else if (previousFingerprint != currentFingerprint) {
                prefs.lastSoftwareFingerprint = currentFingerprint
                Log.i(TAG, "Software update detected")
            }
        }

        if (isSoftwareUpdated) {
            cleanupAdbStateAfterSoftwareUpdate(
                context = context,
                prefs = prefs,
                hadAdbLinkageEvidence = hadAdbLinkageEvidence,
                hasWritePermission = hasWritePermissionAtReceive
            )
            handleDetectedSoftwareUpdate(context, prefs)
            return
        }

        if (action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // 새 버전으로 넘어오는 시점에도 기존 연동 이력은 있는데 권한이 사라졌다면
            // 이전 앱 버전에서 남은 ADB identity까지 정리해 fresh pairing으로 복구한다.
            resetAdbIdentityIfUnexpectedPermissionLoss(
                context = context,
                prefs = prefs,
                hadAdbLinkageEvidence = hadAdbLinkageEvidence,
                hasWritePermission = hasWritePermissionAtReceive,
                reason = "app update"
            )
            notifyIfPermissionLinkageLostAfterAppUpdate(
                context = context,
                prefs = prefs,
                linkageExpected = hadAdbLinkageEvidence,
                hasWritePermission = hasWritePermissionAtReceive
            )
            prefs.clearTransientAdbConnectionState()
            Log.i(TAG, "Cleared stale ADB connect port after app update")
            return
        }

        if (isBootAction(action)) {
            // connect 포트는 재부팅 뒤에도 그대로라는 보장이 없으므로 매 부팅마다 재탐색한다.
            prefs.clearTransientAdbConnectionState()
            Log.i(TAG, "Cleared stale ADB connect port after boot")
            notifyIfMuteNeedsReapplyAfterBoot(context, prefs)
        }
    }

    private fun cleanupAdbStateAfterSoftwareUpdate(
        context: Context,
        prefs: PreferencesRepository,
        hadAdbLinkageEvidence: Boolean,
        hasWritePermission: Boolean
    ) {
        // 무선 ADB connect 포트는 새 소프트웨어 세션에서 재사용하지 않는다.
        prefs.clearTransientAdbConnectionState()
        Log.i(TAG, "Cleared stale ADB connect port after software update")

        resetAdbIdentityIfUnexpectedPermissionLoss(
            context = context,
            prefs = prefs,
            hadAdbLinkageEvidence = hadAdbLinkageEvidence,
            hasWritePermission = hasWritePermission,
            reason = "software update"
        )
    }

    private fun resetAdbIdentityIfUnexpectedPermissionLoss(
        context: Context,
        prefs: PreferencesRepository,
        hadAdbLinkageEvidence: Boolean,
        hasWritePermission: Boolean,
        reason: String
    ) {
        if (!AdbStateCleanupPolicy.shouldResetIdentityAfterUnexpectedPermissionLoss(
                hadLinkageEvidence = hadAdbLinkageEvidence,
                permissionRevokedByUser = prefs.isPermissionRevokedByUser,
                hasWritePermission = hasWritePermission
            )
        ) {
            return
        }

        try {
            // 업데이트 경계에서 기존 연동 흔적은 있는데 권한이 실제로 사라졌다면,
            // 다음 1회 설정이 재설치와 동일하게 새 로컬 ADB identity로 페어링되게 한다.
            AdbKeyHelper.resetIdentity(context)
            Log.i(TAG, "Reset local ADB identity after unexpected permission loss ($reason)")
        } catch (e: Exception) {
            Log.w(TAG, "Unable to reset local ADB identity after $reason (${e.javaClass.simpleName})")
        }
    }

    private fun handleDetectedSoftwareUpdate(
        context: Context,
        prefs: PreferencesRepository
    ) {
        if (!prefs.shouldMuteOnBoot) {
            Log.i(TAG, "Software update detected; camera mute is not in use")
            return
        }

        if (prefs.isPermissionRevokedByUser) {
            Log.i(TAG, "Permission linkage was revoked by user")
            showNotification(
                context,
                "소프트웨어 업데이트가 감지되었습니다. 권한 연동이 해제되어 있습니다. 무선 디버깅을 켠 뒤 [1회 설정 시작]을 다시 진행해 주세요."
            )
            return
        }

        if (CscMuteManager.isCscShutterSoundMuted(context)) {
            Log.i(TAG, "CSC camera mute remained active after software update; no action needed")
            return
        }

        val hasPermission = CscMuteManager.hasWritePermission(context)
        Log.w(TAG, "CSC camera mute needs reapply after software update; permission=$hasPermission")
        showNotification(
            context,
            if (hasPermission) {
                "소프트웨어 업데이트 후 카메라 무음 설정이 초기화되었습니다. 무선 디버깅을 켠 뒤 설정 → 카메라 설정에서 [카메라 무음 다시 적용]을 눌러 주세요."
            } else {
                "소프트웨어 업데이트 후 카메라 무음 설정과 권한 연동이 초기화되었습니다. 무선 디버깅을 켠 뒤 [1회 설정 시작]을 다시 진행해 주세요."
            }
        )
    }

    /**
     * 앱 자체 업데이트 완료 직후 기존 WRITE_SECURE_SETTINGS 연동이 유지됐는지 확인한다.
     *
     * Android의 정상적인 패키지 업데이트라면 권한은 유지되어야 한다. 다만 기기 정책이나
     * 제조사 동작으로 권한이 사라지는 예외 상황에 대비해, 이전 연동 흔적이 있는 경우에만
     * 실제 권한을 검증하고 재연동 안내를 표시한다.
     */
    private fun notifyIfPermissionLinkageLostAfterAppUpdate(
        context: Context,
        prefs: PreferencesRepository,
        linkageExpected: Boolean,
        hasWritePermission: Boolean
    ) {
        if (prefs.isPermissionRevokedByUser) {
            Log.i(TAG, "App updated; permission linkage was already revoked by user")
            return
        }

        if (!linkageExpected) {
            Log.i(TAG, "App updated; no prior permission linkage evidence")
            return
        }

        if (hasWritePermission) {
            Log.i(TAG, "App updated; WRITE_SECURE_SETTINGS permission remained granted")
            return
        }

        Log.w(TAG, "App updated; expected WRITE_SECURE_SETTINGS permission is missing")
        showNotification(
            context,
            "앱 업데이트 후 권한 연동 상태를 확인한 결과 보안 설정 변경 권한이 유지되지 않았습니다. 무선 디버깅을 켠 뒤 [1회 설정 시작]을 다시 진행해 주세요."
        )
    }

    private fun notifyIfMuteNeedsReapplyAfterBoot(
        context: Context,
        prefs: PreferencesRepository
    ) {
        if (!prefs.shouldMuteOnBoot || prefs.isPermissionRevokedByUser) return
        if (CscMuteManager.isCscShutterSoundMuted(context)) return

        val hasPermission = CscMuteManager.hasWritePermission(context)
        Log.w(TAG, "CSC camera mute needs reapply after reboot; permission=$hasPermission")
        showNotification(
            context,
            if (hasPermission) {
                "재부팅 후 카메라 무음 설정이 초기화되었습니다. 무선 디버깅을 켠 뒤 설정 → 카메라 설정에서 [카메라 무음 다시 적용]을 눌러 주세요."
            } else {
                "재부팅 후 카메라 무음 설정과 권한 상태를 확인해야 합니다. 무선 디버깅을 켠 뒤 [1회 설정 시작]을 다시 진행해 주세요."
            }
        )
    }

    private fun isSupportedAction(action: String?): Boolean {
        return isBootAction(action) || action == Intent.ACTION_MY_PACKAGE_REPLACED
    }

    private fun isBootAction(action: String?): Boolean {
        return action == Intent.ACTION_BOOT_COMPLETED ||
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
                .setContentTitle(context.getString(R.string.app_name))
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
                Log.i(TAG, "Update/reboot notification suppressed because notification permission is missing")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to post update/reboot notification (${e.javaClass.simpleName})")
        }
    }

    private fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "카메라 무음 상태 알림",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "재부팅·소프트웨어 업데이트 후 카메라 무음 상태 확인 및 재적용 안내"
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.createNotificationChannel(channel)
    }
}
