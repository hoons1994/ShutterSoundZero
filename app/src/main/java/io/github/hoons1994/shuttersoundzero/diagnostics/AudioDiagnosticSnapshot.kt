package io.github.hoons1994.shuttersoundzero.diagnostics

import io.github.hoons1994.shuttersoundzero.R
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager

/** Read-only audio state. A failed query remains unknown instead of becoming muted/zero. */
internal data class AudioDiagnosticSnapshot(
    val current: Int? = null,
    val min: Int? = null,
    val max: Int? = null,
    val ringerMode: Int? = null,
    val streamMuted: Boolean? = null,
    val fixed: Boolean? = null,
    val interruptionFilter: Int? = null
) {
    fun format(text: (Int) -> String): String = buildString {
        val unknown = text(R.string.diag_unknown)
        val mode = when (ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> R.string.diag_sound
            AudioManager.RINGER_MODE_VIBRATE -> R.string.camera_routine_vibrate
            AudioManager.RINGER_MODE_SILENT -> R.string.camera_routine_silent
            else -> R.string.diag_unknown
        }
        val doNotDisturb = when (interruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_ALL -> false
            NotificationManager.INTERRUPTION_FILTER_PRIORITY,
            NotificationManager.INTERRUPTION_FILTER_NONE,
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> true
            else -> null
        }
        appendLine(text(R.string.diag_audio))
        appendLine(text(R.string.diag_volume).format(current ?: unknown, min ?: unknown, max ?: unknown))
        appendLine(text(R.string.diag_mode).format(text(mode)))
        appendLine(text(R.string.diag_stream).format(text(diagnosticBooleanResource(streamMuted))))
        appendLine(text(R.string.diag_fixed).format(text(diagnosticBooleanResource(fixed))))
        appendLine(text(R.string.diag_dnd).format(text(diagnosticBooleanResource(doNotDisturb))))
    }.trimEnd()

    companion object {
        fun read(context: Context): AudioDiagnosticSnapshot {
            val audio = safe { context.getSystemService(AudioManager::class.java) }
            val notifications = safe { context.getSystemService(NotificationManager::class.java) }
            return AudioDiagnosticSnapshot(
                current = safe { audio?.getStreamVolume(AudioManager.STREAM_SYSTEM) },
                min = safe { audio?.getStreamMinVolume(AudioManager.STREAM_SYSTEM) },
                max = safe { audio?.getStreamMaxVolume(AudioManager.STREAM_SYSTEM) },
                ringerMode = safe { audio?.ringerMode },
                streamMuted = safe { audio?.isStreamMute(AudioManager.STREAM_SYSTEM) },
                fixed = safe { audio?.isVolumeFixed },
                interruptionFilter = safe { notifications?.currentInterruptionFilter }
            )
        }

        private fun <T> safe(read: () -> T): T? = try { read() } catch (_: RuntimeException) { null }
    }
}

internal fun diagnosticBooleanResource(value: Boolean?): Int = when (value) {
    true -> R.string.diag_yes
    false -> R.string.diag_no
    null -> R.string.diag_unknown
}
