package io.github.hoons1994.shuttersoundzero.logging

import io.github.hoons1994.shuttersoundzero.logging.DiagnosticLogger

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
    @Test fun discoveryFailureRecordsOnlyFixedReasonAndNumericNsdCode() {
        val failure = io.github.hoons1994.shuttersoundzero.core.adb.AdbServiceNotFoundException(
            IOException("192.168.1.5 secret-service-name 123456"),
            io.github.hoons1994.shuttersoundzero.core.adb.AdbDiscoveryReason.RESOLVE_FAILED, 3)
        val line = DiagnosticLogger.formatEvent(DiagnosticLogger.Stage.ADB_CONNECT_DISCOVERY,
            DiagnosticLogger.Outcome.FAILURE, failure, "time", 7000)
        assertTrue(line.contains("reason=RESOLVE_FAILED | nsd_error=3"))
        assertTrue(line.contains("duration_ms=7000"))
        for (secret in listOf("192.168.1.5", "secret-service-name", "123456")) assertFalse(line.contains(secret))
    }

    @Test fun volumeFailureRetainsRequestedAndActualValuesWithoutInventingZeroForReadFailure() {
        val line = DiagnosticLogger.formatSystemVolume(7, null, DiagnosticLogger.Outcome.FAILURE,
            DiagnosticLogger.VolumeReason.READ_FAILED, "time", 500)
        assertTrue(line.contains("requested=7 | actual=unknown | reason=READ_FAILED"))
        assertTrue(line.contains("duration_ms=500"))
        assertFalse(line.contains("actual=0"))
    }

    @Test fun pairingDetailsAreFixedCategoriesWithoutSensitiveExceptionMessages() {
        val error = io.github.muntashirakon.adb.PairingFailureException(
            io.github.muntashirakon.adb.PairingFailureException.Phase.IDENTITY,
            IOException("123456 192.168.1.5 private-key SSID"))
        val line = DiagnosticLogger.formatEvent(DiagnosticLogger.Stage.PAIRING_IDENTITY,
            DiagnosticLogger.Outcome.FAILURE, error, "time", 4)
        assertTrue(line.contains("reason=IDENTITY_UNAVAILABLE"))
        assertTrue(line.contains("duration_ms=4"))
        for (secret in listOf("123456", "192.168.1.5", "private-key", "SSID")) assertFalse(line.contains(secret))
    }

    @Test fun hundredsOfRepeatedSightingsDoNotEvictAnEarlierFailure() {
        val stage = DiagnosticLogger.Stage.CONNECT_SERVICE_DISCOVERED
        val failed = "time | PAIRING | FAILURE | error=IOException"
        var lines = listOf(failed)
        for (repeat in 0..500) {
            lines = DiagnosticLogger.trimForStorage(DiagnosticLogger.appendDiscovery(
                lines, stage, "time-$repeat | CONNECT_SERVICE_DISCOVERED | INFO", repeat))
        }
        assertEquals(2, lines.size)
        assertEquals(failed, lines.first())
        assertTrue(lines.last().startsWith("time-0 | "))
        assertTrue(lines.last().contains("repeats=500 | last_seen=time-500"))
    }

    @Test fun alternatingDiscoveryKindsAreSummarizedIndependentlyAndNewSightingsAreRetained() {
        var lines = emptyList<String>()
        for (repeat in 0..10) {
            for (stage in listOf(DiagnosticLogger.Stage.PAIRING_SERVICE_DISCOVERED,
                DiagnosticLogger.Stage.CONNECT_SERVICE_DISCOVERED)) {
                lines = DiagnosticLogger.appendDiscovery(lines, stage, "time | ${stage.name} | INFO", repeat)
            }
        }
        assertEquals(2, lines.size)
        lines = DiagnosticLogger.appendDiscovery(lines, DiagnosticLogger.Stage.PAIRING_SERVICE_DISCOVERED,
            "later | PAIRING_SERVICE_DISCOVERED | INFO", 0)
        assertEquals(3, lines.size)
    }

    @Test fun lostOrChangedEndpointStartsANewDiscoveryRecord() {
        val state = DiscoveryLogState()
        val stage = DiagnosticLogger.Stage.PAIRING_SERVICE_DISCOVERED
        assertEquals(0, state.found(stage, 37123))
        assertEquals(1, state.found(stage, 37123))
        assertEquals(0, state.found(stage, 48123))
        state.lost(stage)
        assertEquals(0, state.found(stage, 48123))
        state.reset()
        assertEquals(0, state.found(stage, 48123))
    }

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
