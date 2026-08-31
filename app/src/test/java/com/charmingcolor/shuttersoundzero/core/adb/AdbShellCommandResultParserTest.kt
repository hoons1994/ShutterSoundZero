package com.charmingcolor.shuttersoundzero.core.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdbShellCommandResultParserTest {
    private val marker = "__SSZ_EXIT_TEST__"

    @Test
    fun parseOrNull_whenCommandSucceeds_returnsOutputAndZeroExitCode() {
        val result = AdbShellCommandResultParser.parseOrNull(
            "command output\n$marker:0\n",
            marker
        )

        assertEquals("command output", result?.output)
        assertEquals(0, result?.exitCode)
    }

    @Test
    fun parseOrNull_whenCommandFails_preservesNonZeroExitCodeAndErrorOutput() {
        val result = AdbShellCommandResultParser.parseOrNull(
            "Security exception\r\n$marker:255\r\n",
            marker
        )

        assertEquals("Security exception", result?.output)
        assertEquals(255, result?.exitCode)
    }

    @Test
    fun parseOrNull_whenMarkerIsMissing_returnsNull() {
        val result = AdbShellCommandResultParser.parseOrNull("command output only", marker)

        assertNull(result)
    }

    @Test
    fun parseOrNull_whenOutputContainsMarkerText_usesCompleteMarkerLine() {
        val result = AdbShellCommandResultParser.parseOrNull(
            "prefix $marker:9 suffix\n$marker:0\n",
            marker
        )

        assertEquals("prefix $marker:9 suffix", result?.output)
        assertEquals(0, result?.exitCode)
    }
}
