package io.github.hoons1994.shuttersoundzero.core.adb

internal data class AdbShellCommandResult(
    val output: String,
    val exitCode: Int
)

internal object AdbShellCommandResultParser {
    fun parseOrNull(rawOutput: String, marker: String): AdbShellCommandResult? {
        // A payload can end in the middle of an exit code or CRLF. Wait for the full line.
        val markerPattern = Regex("(?m)^${Regex.escape(marker)}:(\\d+)\\r?\\n")
        val match = markerPattern.findAll(rawOutput).lastOrNull() ?: return null
        val exitCode = match.groupValues[1].toIntOrNull() ?: return null
        return AdbShellCommandResult(
            output = rawOutput.substring(0, match.range.first).trimEnd(),
            exitCode = exitCode
        )
    }
}
