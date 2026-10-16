package io.github.hoons1994.shuttersoundzero.diagnostics

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
    fun format(): String = buildString {
        appendLine("[소리 상태]")
        appendLine("시스템 음량: ${current ?: "알 수 없음"} (최소 ${min ?: "알 수 없음"}, 최대 ${max ?: "알 수 없음"})")
        appendLine("소리 모드: ${when (ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> "소리"
            AudioManager.RINGER_MODE_VIBRATE -> "진동"
            AudioManager.RINGER_MODE_SILENT -> "무음"
            else -> "알 수 없음"
        }}")
        appendLine("시스템 스트림 음소거: ${yesNo(streamMuted)}")
        appendLine("음량 고정: ${yesNo(fixed)}")
        appendLine("방해 금지: ${when (interruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_ALL -> "아니요"
            NotificationManager.INTERRUPTION_FILTER_PRIORITY,
            NotificationManager.INTERRUPTION_FILTER_NONE,
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> "예"
            else -> "알 수 없음"
        }}")
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
        private fun yesNo(value: Boolean?): String = when (value) {
            true -> "예"
            false -> "아니요"
            null -> "알 수 없음"
        }
    }
}
