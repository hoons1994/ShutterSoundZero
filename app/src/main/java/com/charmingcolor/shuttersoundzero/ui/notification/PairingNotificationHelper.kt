package com.charmingcolor.shuttersoundzero.ui.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.charmingcolor.shuttersoundzero.MainActivity
import com.charmingcolor.shuttersoundzero.R
import com.charmingcolor.shuttersoundzero.receiver.PairingNotificationReceiver

object PairingNotificationHelper {
    const val CHANNEL_ID = "adb_pairing_channel"
    const val NOTIFICATION_ID = 2001

    const val KEY_PAIRING_CODE = "key_pairing_code"
    const val ACTION_SUBMIT_PAIRING_CODE = "com.charmingcolor.shuttersoundzero.ACTION_SUBMIT_PAIRING_CODE"
    const val ACTION_CANCEL_PAIRING = "com.charmingcolor.shuttersoundzero.ACTION_CANCEL_PAIRING"

    fun areNotificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun openNotificationSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "무선 페어링 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "무선 디버깅 6자리 페어링 코드를 알림창에서 직접 입력받습니다."
                setShowBadge(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 200)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * 페어링 코드가 입력되어 성공할 때까지 절대 꺼지지 않는 상단바 알림 표시
     */
    fun showPairingNotification(
        context: Context,
        pairingPort: Int? = null,
        statusMessage: String? = null,
        isDevOptionsOff: Boolean = false
    ) {
        createNotificationChannel(context)

        // 1. RemoteInput: 알림창에서 키보드로 바로 6자리를 치는 인라인 입력 필드
        val remoteInput = RemoteInput.Builder(KEY_PAIRING_CODE)
            .setLabel("6자리 코드 (예: 123456)")
            .build()

        val submitIntent = Intent(context, PairingNotificationReceiver::class.java).apply {
            action = ACTION_SUBMIT_PAIRING_CODE
            if (pairingPort != null) {
                putExtra("pairing_port", pairingPort)
            }
        }
        val submitPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            submitIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        )

        val replyAction = NotificationCompat.Action.Builder(
            R.mipmap.ic_launcher,
            "코드 입력",
            submitPendingIntent
        ).addRemoteInput(remoteInput).build()

        // 2. 취소 버튼
        val cancelIntent = Intent(context, PairingNotificationReceiver::class.java).apply {
            action = ACTION_CANCEL_PAIRING
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val cancelAction = NotificationCompat.Action.Builder(
            R.mipmap.ic_launcher,
            "취소",
            cancelPendingIntent
        ).build()

        // 3. 알림 터치 시 메인 화면으로 복귀
        val contentIntent = Intent(context, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val title = when {
            statusMessage != null -> statusMessage
            isDevOptionsOff -> "💡 '빌드번호' 7번 터치"
            pairingPort != null -> "✨ 6자리 코드 입력 준비 완료! (포트: $pairingPort)"
            else -> "🔑 6자리 코드 입력 대기 중"
        }
        val summaryText = when {
            isDevOptionsOff -> "소프트웨어 정보 화면의 [빌드번호]를 7번 누르세요"
            pairingPort != null -> "상단바 [코드 입력]을 누르고 6자리 코드를 입력하세요"
            else -> "[페어링 코드로 기기 페어링] 터치 후 아래에 입력"
        }
        val bigText = when {
            statusMessage != null -> "$statusMessage\n화면에 뜬 6자리 페어링 코드를 아래 [코드 입력]에 입력해 주세요."
            isDevOptionsOff -> "소프트웨어 정보 화면 하단의 [빌드번호] 항목을 7번 연속 터치하면 '개발자 옵션이 켜졌습니다'라는 문구가 뜹니다.\n그 후 뒤로가기를 누르고 [무선 디버깅] ➔ [페어링 코드로 기기 페어링]으로 이동해 주세요."
            pairingPort != null -> "무선 페어링 서비스가 감지되었습니다 (포트: $pairingPort)!\n화면에 뜬 6자리 페어링 코드를 아래 [코드 입력]에 입력해 주세요."
            else -> "개발자 옵션의 [무선 디버깅] ➔ [페어링 코드로 기기 페어링] 화면을 띄운 상태에서 상단바를 내려 아래 [코드 입력]을 터치해 주세요."
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(summaryText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(contentPendingIntent)

        if (!isDevOptionsOff) {
            builder.addAction(replyAction)
            builder.addAction(cancelAction)
        }

        val notification = builder.build()

        // 스와이프 및 모두 지우기로 절대 지워지지 않도록 플래그 고정
        notification.flags = notification.flags or Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {}
    }

    /**
     * 사용자가 코드를 제출했을 때 페어링 진행 중임을 표시 (꺼지지 않음)
     */
    fun showProgressNotification(context: Context) {
        createNotificationChannel(context)

        val contentIntent = Intent(context, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("무선 페어링 적용 중 ⏳")
            .setContentText("기기 페어링 및 카메라 셔터음 무음 설정을 적용하고 있습니다...")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(contentPendingIntent)
            .build()

        notification.flags = notification.flags or Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {}
    }

    /**
     * 페어링 및 무음화가 완전히 성공했을 때만 알림 완료 상태로 전환
     */
    fun showSuccessNotification(context: Context) {
        val contentIntent = Intent(context, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("셔터음 제로: 권한 연동 완료 ✨")
            .setContentText("보안 설정 권한이 연동되었습니다. 앱에서 셔터음 끄기 스위치를 켜보세요.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {}
    }

    fun cancelNotification(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}
    }
}

