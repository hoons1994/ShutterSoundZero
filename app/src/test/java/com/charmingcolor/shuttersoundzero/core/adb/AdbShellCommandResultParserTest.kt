package com.charmingcolor.shuttersoundzero.core.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    @Test
    fun parseOrNull_whenCommandHasNoOutput_returnsEmptyOutput() {
        val result = AdbShellCommandResultParser.parseOrNull("$marker:0\n", marker)

        assertNotNull(result)
        assertEquals("", result?.output)
        assertEquals(0, result?.exitCode)
    }

    @Test
    fun parseOrNull_whenMarkerContainsRegexCharacters_treatsMarkerLiterally() {
        val specialMarker = "__SSZ.EXIT+[TEST]__"
        val result = AdbShellCommandResultParser.parseOrNull(
            "ok\n$specialMarker:7\n",
            specialMarker
        )

        assertEquals("ok", result?.output)
        assertEquals(7, result?.exitCode)
    }

    @Test
    fun parseOrNull_whenMultipleCompleteMarkersExist_usesLastMarker() {
        val result = AdbShellCommandResultParser.parseOrNull(
            "first\n$marker:1\nsecond\n$marker:0\n",
            marker
        )

        assertEquals("first\n$marker:1\nsecond", result?.output)
        assertEquals(0, result?.exitCode)
    }

    @Test
    fun parseOrNull_whenExitCodeIsNotNumeric_returnsNull() {
        val result = AdbShellCommandResultParser.parseOrNull(
            "failure\n$marker:not-a-number\n",
            marker
        )

        assertNull(result)
    }

    @Test
    fun parseOrNull_whenMarkerHasTrailingCharacters_doesNotAcceptIt() {
        val result = AdbShellCommandResultParser.parseOrNull(
            "output\n$marker:0 extra\n",
            marker
        )

        assertNull(result)
    }
}
