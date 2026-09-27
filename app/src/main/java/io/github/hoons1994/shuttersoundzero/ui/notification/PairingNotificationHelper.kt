package io.github.hoons1994.shuttersoundzero.ui.notification

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
import io.github.hoons1994.shuttersoundzero.MainActivity
import io.github.hoons1994.shuttersoundzero.R
import io.github.hoons1994.shuttersoundzero.receiver.PairingNotificationReceiver
import io.github.hoons1994.shuttersoundzero.service.PairingForegroundService

object PairingNotificationHelper {
    const val CHANNEL_ID = "adb_pairing_private_v2"
    private const val LEGACY_CHANNEL_ID = "adb_pairing_channel"
    const val NOTIFICATION_ID = 2001

    const val KEY_PAIRING_CODE = "key_pairing_code"
    const val ACTION_SUBMIT_PAIRING_CODE = "io.github.hoons1994.shuttersoundzero.ACTION_SUBMIT_PAIRING_CODE"
    const val ACTION_CANCEL_PAIRING = "io.github.hoons1994.shuttersoundzero.ACTION_CANCEL_PAIRING"

    private data class PairingNotificationCopy(
        val title: String,
        val summaryText: String,
        val bigText: String
    )

    fun areNotificationsEnabled(context: Context): Boolean {
        createNotificationChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelEnabled = manager.getNotificationChannel(CHANNEL_ID)?.importance !=
            NotificationManager.IMPORTANCE_NONE
        return NotificationManagerCompat.from(context).areNotificationsEnabled() && channelEnabled
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
            context.getString(R.string.pairing_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.pairing_channel_description)
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
     * 서비스는 [PairingNotificationState]만 전달하고, 사용자 문구는 이 계층에서 결정한다.
     */
    fun buildPairingNotification(
        context: Context,
        pairingPort: Int? = null,
        state: PairingNotificationState? = null,
        isDevOptionsOff: Boolean = false
    ): Notification {
        createNotificationChannel(context)

        val remoteInput = RemoteInput.Builder(KEY_PAIRING_CODE)
            .setLabel(context.getString(R.string.pairing_code_input_hint))
            .setAllowFreeFormInput(true)
            .build()

        // RemoteInput requires a mutable PendingIntent on modern Android. Keep the authoritative
        // ADB endpoint out of that mutable intent; the service resolves the current port only
        // from app-owned mDNS discovery state.
        val submitIntent = Intent(ACTION_SUBMIT_PAIRING_CODE).setClass(
            context,
            PairingNotificationReceiver::class.java
        )
        val submitPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            submitIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        )

        val replyAction = NotificationCompat.Action.Builder(
            R.mipmap.ic_launcher,
            context.getString(R.string.pairing_action_enter_code),
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
            context.getString(R.string.action_cancel),
            cancelPendingIntent
        ).build()

        val copy = resolvePairingNotificationCopy(
            context = context,
            pairingPort = pairingPort,
            state = state,
            isDevOptionsOff = isDevOptionsOff
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(copy.title)
            .setContentText(copy.summaryText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(buildRedactedPublicVersion(context, completed = false))
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(false)

        // Plain setup notifications intentionally stay on the standard template so One UI can
        // expose the RemoteInput action naturally. State/error messages use expanded text.
        if (state != null) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(copy.bigText))
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

    private fun resolvePairingNotificationCopy(
        context: Context,
        pairingPort: Int?,
        state: PairingNotificationState?,
        isDevOptionsOff: Boolean
    ): PairingNotificationCopy {
        val defaultSummary = when {
            isDevOptionsOff -> context.getString(R.string.pairing_default_dev_options_summary)
            pairingPort != null -> context.getString(R.string.pairing_default_code_summary)
            else -> context.getString(R.string.pairing_default_open_pairing_summary)
        }
        val defaultBigText = when {
            isDevOptionsOff -> context.getString(R.string.pairing_default_dev_options_detail)
            pairingPort != null -> context.getString(R.string.pairing_default_code_detail)
            else -> context.getString(R.string.pairing_default_open_pairing_detail)
        }

        if (state == null) {
            val title = when {
                isDevOptionsOff -> context.getString(R.string.pairing_title_setup_ready)
                pairingPort != null -> context.getString(R.string.pairing_title_code_entry)
                else -> context.getString(R.string.pairing_title_setup_in_progress)
            }
            return PairingNotificationCopy(title, defaultSummary, defaultBigText)
        }

        val stateTitle = when (state) {
            PairingNotificationState.DEVELOPER_OPTIONS_READY -> context.getString(R.string.pairing_state_developer_options_ready)
            PairingNotificationState.LOCAL_NETWORK_PERMISSION_REQUIRED -> context.getString(R.string.pairing_state_local_network_permission_required)
            PairingNotificationState.DISCOVERY_START_FAILED -> context.getString(R.string.pairing_state_discovery_start_failed)
            PairingNotificationState.INVALID_PAIRING_CODE -> context.getString(R.string.pairing_state_invalid_code)
            PairingNotificationState.DISCOVERY_WAITING -> context.getString(R.string.pairing_state_discovery_waiting)
            PairingNotificationState.CAMERA_APPLY_FAILED -> context.getString(R.string.pairing_state_camera_apply_failed)
            PairingNotificationState.PAIRING_FAILED -> context.getString(R.string.pairing_state_failed)
            PairingNotificationState.PAIRING_TIMEOUT -> context.getString(R.string.pairing_state_timeout)
            PairingNotificationState.PAIRING_ERROR -> context.getString(R.string.pairing_state_error)
        }

        if (state == PairingNotificationState.DEVELOPER_OPTIONS_READY) {
            val nextStep = context.getString(R.string.pairing_state_next_step)
            return PairingNotificationCopy(stateTitle, nextStep, nextStep)
        }

        val guidance = when (state) {
            PairingNotificationState.DEVELOPER_OPTIONS_READY -> R.string.pairing_state_next_step
            PairingNotificationState.LOCAL_NETWORK_PERMISSION_REQUIRED -> R.string.pairing_help_local_network
            PairingNotificationState.DISCOVERY_START_FAILED,
            PairingNotificationState.DISCOVERY_WAITING -> R.string.pairing_help_discovery
            PairingNotificationState.INVALID_PAIRING_CODE -> R.string.pairing_help_invalid_code
            PairingNotificationState.CAMERA_APPLY_FAILED -> R.string.pairing_help_camera_apply
            PairingNotificationState.PAIRING_FAILED,
            PairingNotificationState.PAIRING_ERROR -> R.string.pairing_help_connection
            PairingNotificationState.PAIRING_TIMEOUT -> R.string.pairing_help_timeout
        }
        val detail = context.getString(guidance)
        return PairingNotificationCopy(stateTitle, detail, detail)
    }

    fun showPairingNotification(
        context: Context,
        pairingPort: Int? = null,
        state: PairingNotificationState? = null,
        isDevOptionsOff: Boolean = false
    ) {
        val notification = buildPairingNotification(
            context = context,
            pairingPort = pairingPort,
            state = state,
            isDevOptionsOff = isDevOptionsOff
        )
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    fun buildProgressNotification(context: Context): Notification {
        createNotificationChannel(context)

        val contentIntent = Intent().setClass(context, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.pairing_progress_title))
            .setContentText(context.getString(R.string.pairing_progress_text))
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

        val contentIntent = Intent().setClass(context, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(
                context.getString(
                    R.string.pairing_success_title,
                    context.getString(R.string.app_name)
                )
            )
            .setContentText(
                if (wirelessDebuggingDisabled) {
                    context.getString(R.string.pairing_success_wireless_disabled)
                } else {
                    context.getString(R.string.pairing_success_disable_wireless_manually)
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
            .setContentTitle(
                context.getString(
                    if (completed) R.string.pairing_public_title_completed
                    else R.string.pairing_public_title_in_progress
                )
            )
            .setContentText(
                context.getString(
                    if (completed) R.string.pairing_public_text_completed
                    else R.string.pairing_public_text_in_progress
                )
            )
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
