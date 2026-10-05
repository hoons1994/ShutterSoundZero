package io.github.hoons1994.shuttersoundzero.diagnostics

import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticLoggerTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun clearDeletesExistingLogAndIsIdempotent() {
        val file = temporaryFolder.newFile("events.log")
        assertTrue(DiagnosticLogger.clear(file).getOrThrow())
        assertFalse(file.exists())
        assertFalse(DiagnosticLogger.clear(file).getOrThrow())
    }

    @Test
    fun clearAlreadyMissingLogSucceedsWithoutCreatingAFile() {
        val file = File(temporaryFolder.root, "missing.log")
        assertFalse(DiagnosticLogger.clear(file).getOrThrow())
        assertFalse(file.exists())
    }

    @Test
    fun failedDeletionDoesNotClaimSuccessOrRemoveRemainingData() {
        val directory = temporaryFolder.newFolder("events.log")
        val child = File(directory, "test-event").apply { createNewFile() }
        val result = DiagnosticLogger.clear(directory)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertTrue(child.exists())
    }

    @Test
    fun deletionExceptionIsReturnedAsFailure() {
        val failure = SecurityException("injected deletion denial")
        val file = object : File(temporaryFolder.root, "blocked.log") {
            override fun delete(): Boolean = throw failure
        }
        assertSame(failure, DiagnosticLogger.clear(file).exceptionOrNull())
    }

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
