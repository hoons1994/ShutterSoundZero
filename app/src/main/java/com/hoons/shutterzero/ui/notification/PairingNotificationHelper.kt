package com.hoons.shutterzero.ui.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.hoons.shutterzero.MainActivity
import com.hoons.shutterzero.R
import com.hoons.shutterzero.receiver.PairingNotificationReceiver

object PairingNotificationHelper {
    const val CHANNEL_ID = "adb_pairing_channel"
    const val NOTIFICATION_ID = 2001

    const val KEY_PAIRING_CODE = "key_pairing_code"
    const val ACTION_SUBMIT_PAIRING_CODE = "com.hoons.shutterzero.ACTION_SUBMIT_PAIRING_CODE"
    const val ACTION_CANCEL_PAIRING = "com.hoons.shutterzero.ACTION_CANCEL_PAIRING"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "무선 페어링 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "무선 디버깅 6자리 페어링 코드를 알림창에서 직접 입력받습니다."
                setShowBadge(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun showPairingNotification(context: Context, pairingPort: Int? = null) {
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
            "코드 입력하기",
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

        val portText = if (pairingPort != null) " (감지된 포트: $pairingPort)" else ""
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("무선 디버깅 페어링 대기 중")
            .setContentText("개발자 옵션의 6자리 코드를 아래 [코드 입력하기]에 입력하세요$portText")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("개발자 옵션의 [페어링 코드로 기기 페어링] 화면을 띄운 상태에서 상단바를 내려 아래 [코드 입력하기]를 터치하고 6자리 숫자를 입력해 주세요.$portText")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(contentPendingIntent)
            .addAction(replyAction)
            .addAction(cancelAction)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {}
    }

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
            .setContentTitle("셔터 제로: 셔터음 무음화 완료 ✨")
            .setContentText("기기 페어링 및 카메라 셔터음 무음 설정이 성공적으로 완료되었습니다.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {}
    }

    fun showFailureNotification(context: Context, errorMsg: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("셔터 제로: 페어링 실패 ❌")
            .setContentText(errorMsg)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(false)
            .setAutoCancel(true)
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
