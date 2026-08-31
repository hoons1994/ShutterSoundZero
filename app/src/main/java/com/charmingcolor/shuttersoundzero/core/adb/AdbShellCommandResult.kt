package com.charmingcolor.shuttersoundzero.core.adb

internal data class AdbShellCommandResult(
    val output: String,
    val exitCode: Int
)

internal object AdbShellCommandResultParser {
    fun parseOrNull(rawOutput: String, marker: String): AdbShellCommandResult? {
        val markerPattern = Regex("(?m)^${Regex.escape(marker)}:(\\d+)\\r?$")
        val match = markerPattern.findAll(rawOutput).lastOrNull() ?: return null
        val exitCode = match.groupValues[1].toIntOrNull() ?: return null
        return AdbShellCommandResult(
            output = rawOutput.substring(0, match.range.first).trimEnd(),
            exitCode = exitCode
        )
    }
}
