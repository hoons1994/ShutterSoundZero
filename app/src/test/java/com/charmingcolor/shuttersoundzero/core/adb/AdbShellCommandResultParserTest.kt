package com.charmingcolor.shuttersoundzero.core.adb

import kotlin.random.Random
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

    @Test
    fun parseOrNull_forManyLiteralMarkers_roundTripsOutputAndExitCode() {
        val random = Random(0x5A17)
        val markerAlphabet = "abcXYZ0123456789._+[](){}?^|-$"

        repeat(512) { index ->
            val generatedMarker = buildString {
                append("__SSZ_")
                repeat(random.nextInt(4, 20)) {
                    append(markerAlphabet[random.nextInt(markerAlphabet.length)])
                }
                append("__")
            }
            val exitCode = random.nextInt(0, 1000)
            val output = "payload-$index-${random.nextLong()}"
            val lineEnding = if (random.nextBoolean()) "\n" else "\r\n"
            val raw = "$output\t$lineEnding$generatedMarker:$exitCode$lineEnding"

            val result = AdbShellCommandResultParser.parseOrNull(raw, generatedMarker)

            assertNotNull("iteration=$index marker=$generatedMarker", result)
            assertEquals("iteration=$index", output, result?.output)
            assertEquals("iteration=$index", exitCode, result?.exitCode)
        }
    }

    @Test
    fun parseOrNull_forManyIncompleteMarkerLines_neverAcceptsThem() {
        val random = Random(0xBAD5EED)
        val invalidSuffixes = listOf(" extra", "x", ":1", "-1", "+1", " 0")

        repeat(512) { index ->
            val suffix = invalidSuffixes[random.nextInt(invalidSuffixes.size)]
            val raw = "payload-$index\n$marker:${random.nextInt(0, 1000)}$suffix\n"

            assertNull("iteration=$index suffix=$suffix", AdbShellCommandResultParser.parseOrNull(raw, marker))
        }
    }

    @Test
    fun parseOrNull_forRandomMarkerLikeNoise_neverTreatsPartialLinesAsCompletion() {
        val random = Random(0xF00DCAFE.toInt())
        val separators = listOf(" ", "\t", "-", "+", ":", "x")

        repeat(512) { index ->
            val prefix = "noise-${random.nextLong()}"
            val separator = separators[random.nextInt(separators.size)]
            val raw = "$prefix\n$marker$separator${random.nextInt(0, 1000)}\n"

            assertNull(
                "iteration=$index separator=$separator",
                AdbShellCommandResultParser.parseOrNull(raw, marker)
            )
        }
    }

    @Test
    fun parseOrNull_acceptsMaximumIntExitCode() {
        val result = AdbShellCommandResultParser.parseOrNull(
            "output\n$marker:${Int.MAX_VALUE}\n",
            marker
        )

        assertEquals("output", result?.output)
        assertEquals(Int.MAX_VALUE, result?.exitCode)
    }

    @Test
    fun parseOrNull_whenNumericExitCodeOverflowsInt_returnsNull() {
        val result = AdbShellCommandResultParser.parseOrNull(
            "output\n$marker:999999999999999999999999999999\n",
            marker
        )

        assertNull(result)
    }
}
