package io.github.hoons1994.shuttersoundzero.logging

import android.content.Context
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * ShutterSoundZero 자체 동작만 기록하는 작은 진단 로그 저장소.
 *
 * 민감정보가 섞일 수 있는 임의 문자열은 받지 않고, 정해진 단계/결과와 예외 클래스명만 저장한다.
 * 페어링 코드, IP 주소, SSID/BSSID, 기기 식별자, ADB 키/인증서는 기록하지 않는다.
 */
object DiagnosticLogger {
    internal const val MAX_EVENTS = 200
    internal const val MAX_FILE_BYTES = 256 * 1024

    enum class Stage {
        PAIRING_DISCOVERY,
        PAIRING_SERVICE_DISCOVERED,
        CONNECT_SERVICE_DISCOVERED,
        PAIRING_CODE_SUBMITTED,
        PAIRING,
        ADB_PERMISSION_AND_CSC_APPLY,
        ADB_CONNECT_DISCOVERY,
        ADB_CONNECT,
        CSC_WRITE,
        CSC_VERIFY,
        CSC_REAPPLY,
        CSC_RESTORE,
        WIRELESS_DEBUGGING_CLEANUP,
        PAIRING_WORKFLOW
    }

    enum class Outcome {
        STARTED,
        INFO,
        SUCCESS,
        FAILURE,
        TIMEOUT,
        CANCELLED
    }

    private const val DIRECTORY_NAME = "diagnostics"
    private const val FILE_NAME = "events.log"
    private val lock = Any()

    fun record(
        context: Context,
        stage: Stage,
        outcome: Outcome,
        error: Throwable? = null
    ) {
        val errorType = error?.javaClass?.simpleName
            ?.takeIf { it.matches(Regex("[A-Za-z0-9_$]{1,80}")) }
        val line = buildString {
            append(formatTimestamp(System.currentTimeMillis()))
            append(" | ")
            append(stage.name)
            append(" | ")
            append(outcome.name)
            if (errorType != null) {
                append(" | error=")
                append(errorType)
            }
        }

        synchronized(lock) {
            runCatching {
                val file = logFile(context)
                val existing = if (file.exists()) file.readLines(Charsets.UTF_8) else emptyList()
                val bounded = trimForStorage(existing + line)
                file.parentFile?.mkdirs()
                file.writeText(
                    bounded.joinToString(separator = "\n", postfix = if (bounded.isEmpty()) "" else "\n"),
                    Charsets.UTF_8
                )
            }
        }
    }

    fun read(context: Context): List<String> = synchronized(lock) {
        runCatching {
            val file = logFile(context)
            if (!file.exists()) emptyList() else trimForStorage(file.readLines(Charsets.UTF_8))
        }.getOrDefault(emptyList())
    }

    /** true: deleted; false: already absent. An uncertain or failed deletion is a failure. */
    fun clear(context: Context): Result<Boolean> = synchronized(lock) {
        runCatching { clear(logFile(context)).getOrThrow() }
    }

    internal fun clear(file: File): Result<Boolean> = runCatching {
        when {
            file.delete() -> true
            Files.notExists(file.toPath()) -> false
            else -> throw IOException("Unable to delete diagnostic log")
        }
    }

    internal fun trimForStorage(lines: List<String>): List<String> {
        var result = lines
            .asSequence()
            .filter { it.isNotBlank() }
            .map { it.take(512) }
            .toList()
            .takeLast(MAX_EVENTS)

        while (
            result.isNotEmpty() &&
            result.sumOf { it.toByteArray(Charsets.UTF_8).size + 1 } > MAX_FILE_BYTES
        ) {
            result = result.drop(1)
        }
        return result
    }

    private fun logFile(context: Context): File =
        File(File(context.filesDir, DIRECTORY_NAME), FILE_NAME)

    private fun formatTimestamp(timestampMillis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(timestampMillis))
    }
}
