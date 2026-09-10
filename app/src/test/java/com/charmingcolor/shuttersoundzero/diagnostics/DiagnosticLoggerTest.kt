package com.charmingcolor.shuttersoundzero.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticLoggerTest {

    @Test
    fun trimForStorage_keepsLatestTwoHundredEvents() {
        val lines = (1..250).map { "event-$it" }

        val result = DiagnosticLogger.trimForStorage(lines)

        assertEquals(DiagnosticLogger.MAX_EVENTS, result.size)
        assertEquals("event-51", result.first())
        assertEquals("event-250", result.last())
    }

    @Test
    fun trimForStorage_limitsIndividualLineLength() {
        val longLine = "x".repeat(800)

        val result = DiagnosticLogger.trimForStorage(listOf(longLine))

        assertEquals(1, result.size)
        assertEquals(512, result.single().length)
    }

    @Test
    fun trimForStorage_respectsByteLimit() {
        val lines = (1..200).map { "가".repeat(500) }

        val result = DiagnosticLogger.trimForStorage(lines)
        val storedBytes = result.sumOf { it.toByteArray(Charsets.UTF_8).size + 1 }

        assertTrue(storedBytes <= DiagnosticLogger.MAX_FILE_BYTES)
        assertTrue(result.size <= DiagnosticLogger.MAX_EVENTS)
    }
}
