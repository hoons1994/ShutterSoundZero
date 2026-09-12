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
            "1회 설정 안내",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "기기 연결과 6자리 코드 입력 등 1회 설정 진행 상태를 안내합니다."
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
     * 6자리 코드 입력이 끝날 때까지 유지되는 1회 설정 알림.
     * 내부 연결 정보는 사용자 문구에 노출하지 않고, 현재 필요한 행동만 안내한다.
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

        val userStatusMessage = statusMessage?.let(::userFacingStatusMessage)
        val userStatusDetail = statusDetail?.let(::userFacingStatusDetail)

        val title = when {
            userStatusMessage != null -> userStatusMessage
            isDevOptionsOff -> "1회 설정 준비"
            pairingPort != null -> "6자리 코드 입력"
            else -> "1회 설정 진행"
        }
        val summaryText = when {
            userStatusDetail != null -> userStatusDetail
            isDevOptionsOff -> "[소프트웨어 정보]에서 [빌드번호]를 7번 눌러 개발자 옵션을 켜 주세요."
            pairingPort != null -> "화면에 표시된 6자리 코드를 [코드 입력]에 입력해 주세요."
            else -> "무선 디버깅에서 [페어링 코드로 기기 페어링]을 열어 주세요."
        }
        val bigText = when {
            userStatusDetail != null -> userStatusDetail
            userStatusMessage != null && pairingPort != null ->
                "[코드 입력]을 눌러 화면에 표시된 6자리 코드를 입력해 주세요."
            userStatusMessage != null -> "홈 화면의 안내에 따라 1회 설정을 계속해 주세요."
            isDevOptionsOff ->
                "휴대전화 정보의 [소프트웨어 정보]에서 [빌드번호]를 7번 눌러 개발자 옵션을 켜 주세요."
            pairingPort != null ->
                "연결 화면을 찾았습니다. 화면에 표시된 6자리 코드를 아래 [코드 입력]에 입력해 주세요."
            else ->
                "[무선 디버깅] → [페어링 코드로 기기 페어링] 화면을 연 뒤 상단 알림의 [코드 입력]을 사용해 주세요."
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

        // Plain setup notifications intentionally stay on the standard template so One UI can
        // expose the RemoteInput action naturally. Status/error messages use expanded text.
        if (userStatusMessage != null) {
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

    private fun userFacingStatusMessage(message: String): String {
        return when (message) {
            "개발자 옵션 활성화 완료" -> "개발자 옵션 준비 완료"
            "⚠️ 무선 페어링 탐색을 시작할 수 없습니다." -> "⚠️ 1회 설정을 시작하지 못했습니다."
            "⏳ 포트 탐색 대기 중: 화면의 6자리 코드를 다시 입력해 주세요." ->
                "⏳ 연결 화면을 찾고 있습니다. 6자리 코드를 다시 입력해 주세요."
            "⚠️ 페어링은 완료됐지만 무음 설정 적용에 실패했습니다. 무선 디버깅 상태를 확인한 뒤 다시 시도해 주세요." ->
                "⚠️ 기기 연결은 완료됐지만 카메라 무음 설정을 적용하지 못했습니다."
            "❌ 페어링에 실패했습니다. 화면의 6자리 코드를 확인해 다시 입력해 주세요." ->
                "❌ 기기 연결에 실패했습니다. 6자리 코드를 확인해 다시 입력해 주세요."
            "⏱️ 시간 초과: 코드를 다시 입력해 주세요." ->
                "⏱️ 입력 시간이 초과되었습니다. 6자리 코드를 다시 입력해 주세요."
            "❌ 페어링 중 오류가 발생했습니다. 무선 디버깅 상태를 확인하고 다시 시도해 주세요." ->
                "❌ 기기 연결 중 문제가 발생했습니다. 홈 화면의 안내를 확인해 주세요."
            else -> message
        }
    }

    private fun userFacingStatusDetail(detail: String): String {
        return when (detail) {
            "앱으로 돌아가 [권한 요청]을 누르세요." ->
                "무선 디버깅을 켠 뒤 [페어링 코드로 기기 페어링]을 열어 주세요."
            else -> detail
        }
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
            .setContentTitle("카메라 무음 설정 적용 중 ⏳")
            .setContentText("기기 연결을 확인하고 카메라 설정을 적용하고 있습니다.")
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

    fun showSuccessNotification(
        context: Context,
        wirelessDebuggingDisabled: Boolean = false
    ) {
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
            .setContentTitle("${context.getString(R.string.app_name)}: 설정 완료 ✨")
            .setContentText(
                if (wirelessDebuggingDisabled) {
                    "카메라 무음 설정이 완료되었습니다. 앱을 계속 열어둘 필요가 없습니다."
                } else {
                    "카메라 무음 설정은 완료되었습니다. 기기 설정에서 무선 디버깅을 직접 꺼 주세요."
                }
            )
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
            .setContentTitle(if (completed) "ShutterSoundZero 알림" else "1회 설정 진행 중")
            .setContentText(if (completed) "앱을 열어 결과를 확인하세요." else "설정을 계속하려면 기기를 잠금 해제하세요.")
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
