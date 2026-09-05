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
            description = "무선 디버깅 6자리 페어링 코드를 빠르게 입력할 수 있도록 안내합니다."
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
     * 페어링 코드가 입력되어 성공할 때까지 꺼지지 않는 상단바 알림 표시.
     * 알림 자체를 눌러도 Activity를 전환하지 않고 실행 중인 서비스에 입력창 표시를 요청한다.
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

        val replyAction = NotificationCompat.Action.Builder(
            R.mipmap.ic_launcher,
            "코드 입력",
            submitPendingIntent
        ).addRemoteInput(remoteInput).build()

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

        // 간략 알림을 눌렀을 때 Activity를 띄우면 삼성의 페어링 코드 모드가 닫힐 수 있다.
        // 서비스 PendingIntent로 처리해 현재 설정 화면을 그대로 유지한다.
        val contentPendingIntent = PendingIntent.getService(
            context,
            10,
            PairingForegroundService.showCodeInputIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canUseOverlay = Settings.canDrawOverlays(context)
        val title = when {
            statusMessage != null -> statusMessage
            isDevOptionsOff -> "개발자 옵션 활성화"
            pairingPort != null -> "페어링 코드 입력"
            else -> "무선 디버깅 페어링"
        }
        val summaryText = when {
            statusDetail != null -> statusDetail
            isDevOptionsOff -> "강조된 [소프트웨어 정보]를 누른 후 [빌드번호]를 7번 누르세요"
            pairingPort != null && canUseOverlay -> "알림을 눌러 6자리 코드 입력창을 여세요"
            pairingPort != null -> "알림을 펼쳐 [코드 입력]으로 6자리 코드를 입력하세요"
            else -> "[무선 디버깅] → [페어링 코드로 기기 페어링]을 누르세요"
        }
        val bigText = when {
            statusDetail != null -> statusDetail
            statusMessage != null && pairingPort != null && canUseOverlay -> "$statusMessage\n이 알림을 누르면 설정 화면을 닫지 않고 6자리 코드 입력창이 표시됩니다."
            statusMessage != null && pairingPort != null -> "$statusMessage\n알림을 펼친 뒤 [코드 입력]을 눌러 6자리 코드를 입력해 주세요."
            statusMessage != null -> "$statusMessage\n화면의 안내에 따라 페어링을 계속해 주세요."
            isDevOptionsOff -> "휴대전화 정보 화면에서 강조된 [소프트웨어 정보]를 누르세요."
            pairingPort != null && canUseOverlay -> "무선 페어링 서비스가 감지되었습니다.\n이 알림을 누르면 삼성 설정 화면을 그대로 둔 채 6자리 입력창만 표시됩니다. 알림을 펼친 경우에는 아래 [코드 입력]도 사용할 수 있습니다."
            pairingPort != null -> "무선 페어링 서비스가 감지되었습니다.\n알림을 펼친 뒤 아래 [코드 입력]을 눌러 화면의 6자리 코드를 입력해 주세요."
            else -> "개발자 옵션의 [무선 디버깅] → [페어링 코드로 기기 페어링]을 눌러 6자리 코드 화면을 띄워 주세요."
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
            .setContentIntent(contentPendingIntent)

        if (statusMessage != null || pairingPort != null) {
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