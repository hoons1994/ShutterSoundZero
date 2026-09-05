package com.charmingcolor.shuttersoundzero.ui.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.charmingcolor.shuttersoundzero.MainActivity
import com.charmingcolor.shuttersoundzero.R
import com.charmingcolor.shuttersoundzero.receiver.PairingNotificationReceiver
import com.charmingcolor.shuttersoundzero.service.PairingForegroundService

object PairingNotificationHelper {
    const val CHANNEL_ID = "adb_pairing_private_v2"
    private const val LEGACY_CHANNEL_ID = "adb_pairing_channel"
    const val NOTIFICATION_ID = 2001

    const val KEY_PAIRING_CODE = "key_pairing_code"
    const val ACTION_SUBMIT_PAIRING_CODE = "com.charmingcolor.shuttersoundzero.ACTION_SUBMIT_PAIRING_CODE"
    const val ACTION_CANCEL_PAIRING = "com.charmingcolor.shuttersoundzero.ACTION_CANCEL_PAIRING"

    fun areNotificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "무선 페어링 알림",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "무선 디버깅 6자리 페어링 코드를 알림에서 바로 입력할 수 있도록 안내합니다."
            setShowBadge(true)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 200, 100, 200)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        // Notification channel behavior is immutable after first creation. Move existing installs
        // away from the legacy PUBLIC channel so pairing details are private by default as well.
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
    }

    /**
     * 페어링 코드가 입력되어 성공할 때까지 유지되는 상단바 알림.
     * 하나의 표준 알림을 사용해 One UI 자세히 보기에서는 기존처럼 액션을 바로 노출하고,
     * 간략히 보기에서는 시스템이 접은 알림을 펼쳐 같은 RemoteInput 액션을 사용한다.
     */
    fun buildPairingNotification(
        context: Context,
        pairingPort: Int? = null,
        statusMessage: String? = null,
        isDevOptionsOff: Boolean = false,
        statusDetail: String? = null
    ): Notification {
        createNotificationChannel(context)

        val remoteInput = RemoteInput.Builder(KEY_PAIRING_CODE)
            .setLabel("6자리 코드 (예: 123456)")
            .setAllowFreeFormInput(true)
            .build()

        // RemoteInput requires a mutable PendingIntent on modern Android. Keep the authoritative
        // ADB endpoint out of that mutable intent; the service resolves the current port only
        // from app-owned mDNS discovery state.
        val submitIntent = Intent(context, PairingNotificationReceiver::class.java).apply {
            action = ACTION_SUBMIT_PAIRING_CODE
        }
        val submitPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            submitIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        )

        // Keep this action deliberately close to the original notification implementation.
        // Detailed pop-up users can interact with it directly while brief pop-up users can expand
        // the notification and use the exact same RemoteInput path.
        val replyAction = NotificationCompat.Action.Builder(
            R.mipmap.ic_launcher,
            "코드 입력",
            submitPendingIntent
        )
            .addRemoteInput(remoteInput)
            .build()

        val cancelPendingIntent = PendingIntent.getService(
            context,
            2,
            PairingForegroundService.stopIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancelAction = NotificationCompat.Action.Builder(
            R.mipmap.ic_launcher,
            "취소",
            cancelPendingIntent
        ).build()

        val title = when {
            statusMessage != null -> statusMessage
            isDevOptionsOff -> "개발자 옵션 활성화"
            pairingPort != null -> "페어링 코드 입력 (포트: $pairingPort)"
            else -> "무선 디버깅 페어링"
        }
        val summaryText = when {
            statusDetail != null -> statusDetail
            isDevOptionsOff -> "강조된 [소프트웨어 정보]를 누른 후 [빌드번호]를 7번 누르세요"
            pairingPort != null -> "알림의 [코드 입력]을 누르고 페어링 코드를 입력하고 [전송]을 누르세요"
            else -> "[무선 디버깅] → [페어링 코드로 기기 페어링]을 누르세요"
        }
        val bigText = when {
            statusDetail != null -> statusDetail
            statusMessage != null && pairingPort != null -> "$statusMessage\n알림의 [코드 입력]을 눌러 화면에 표시된 6자리 코드를 입력해 주세요."
            statusMessage != null -> "$statusMessage\n화면의 안내에 따라 페어링을 계속해 주세요."
            isDevOptionsOff -> "휴대전화 정보 화면에서 강조된 [소프트웨어 정보]를 누르세요."
            pairingPort != null -> "무선 페어링 서비스가 감지되었습니다 (포트: $pairingPort)!\n화면에 뜬 6자리 페어링 코드를 아래 [코드 입력]에 입력해 주세요."
            else -> "개발자 옵션의 [무선 디버깅] → [페어링 코드로 기기 페어링] 화면을 띄운 상태에서 상단바를 내려 아래 [코드 입력]을 터치해 주세요."
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(summaryText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(buildRedactedPublicVersion(context, completed = false))
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(false)

        // Plain pairing notifications intentionally stay on the standard template. This lets
        // One UI Detailed pop-up render the action buttons the way it did before. BigTextStyle is
        // reserved for actual status/error messages that benefit from the extra explanation.
        if (statusMessage != null) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
        }

        if (pairingPort != null) builder.addAction(replyAction)
        builder.addAction(cancelAction)

        val notification = builder.build()
        notification.flags = notification.flags or
            Notification.FLAG_NO_CLEAR or
            Notification.FLAG_ONGOING_EVENT or
            Notification.FLAG_FOREGROUND_SERVICE

        return notification
    }

    fun showPairingNotification(
        context: Context,
        pairingPort: Int? = null,
        statusMessage: String? = null,
        isDevOptionsOff: Boolean = false,
        statusDetail: String? = null
    ) {
        val notification = buildPairingNotification(
            context,
            pairingPort,
            statusMessage,
            isDevOptionsOff,
            statusDetail
        )
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    fun buildProgressNotification(context: Context): Notification {
        createNotificationChannel(context)

        val contentIntent = Intent(context, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("무선 페어링 적용 중 ⏳")
            .setContentText("기기 페어링 및 카메라 셔터음 무음 설정을 적용하고 있습니다...")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(buildRedactedPublicVersion(context, completed = false))
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(contentPendingIntent)
            .build()
            .also {
                it.flags = it.flags or Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT
            }
    }

    fun showProgressNotification(context: Context) {
        try {
            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID,
                buildProgressNotification(context)
            )
        } catch (_: SecurityException) {
        }
    }

    fun showSuccessNotification(context: Context) {
        createNotificationChannel(context)

        val contentIntent = Intent(context, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("셔터음 제로: 권한 연동 완료 ✨")
            .setContentText("보안 설정 권한이 연동되었습니다. 앱에서 셔터음 끄기 스위치를 켜보세요.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(buildRedactedPublicVersion(context, completed = true))
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun buildRedactedPublicVersion(context: Context, completed: Boolean): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (completed) "ShutterSoundZero 알림" else "무선 디버깅 페어링 진행 중")
            .setContentText(if (completed) "앱을 열어 결과를 확인하세요." else "페어링을 계속하려면 기기를 잠금 해제하세요.")
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(!completed)
            .build()
    }

    fun cancelNotification(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        } catch (_: Exception) {
        }
    }
}
