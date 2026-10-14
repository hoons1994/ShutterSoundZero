package io.github.hoons1994.shuttersoundzero.pairing

import io.github.hoons1994.shuttersoundzero.service.OperationGeneration
import io.github.hoons1994.shuttersoundzero.core.adb.AdbServiceNotFoundException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PairingWorkflowTest {
    @Test fun slowPairingLeavesEnoughTimeForBothConnectionDiscoveryAttempts() = runTest {
        var attempts = 0
        var paired = 0
        val events = mutableListOf<PairingEvent>()
        PairingWorkflow({ 37123 }, { _, _ ->
            paired++
            delay(12_000)
            Result.success(Unit)
        }, {
            attempts++
            delay(7_000)
            if (attempts == 1) Result.failure(AdbServiceNotFoundException()) else Result.success(Unit)
        }).run("123456", events::add)
        assertEquals(1, paired)
        assertEquals(2, attempts)
        assertEquals(26_800L, currentTime)
        assertEquals(PairingEvent.ApplySucceeded, events.last())
        assertFalse(events.contains(PairingEvent.TimedOut))
    }

    @Test fun discoveryFailureRetriesApplyWithoutPairingAgain() = runTest {
        var pairs = 0
        var applies = 0
        val events = mutableListOf<PairingEvent>()
        PairingWorkflow({ 37123 }, { _, _ -> pairs++; Result.success(Unit) }, {
            applies++
            if (applies == 1) Result.failure(AdbServiceNotFoundException()) else Result.success(Unit)
        }).run("123456", events::add)
        assertEquals(1, pairs)
        assertEquals(2, applies)
        assertTrue(events.contains(PairingEvent.ApplyRetrying))
        assertEquals(PairingEvent.ApplySucceeded, events.last())
    }

    @Test fun manualRetryDoesNotNeedCodeOrPairingPortAndIsBounded() = runTest {
        val failure = AdbServiceNotFoundException()
        val events = mutableListOf<PairingEvent>()
        var applies = 0
        PairingWorkflow({ error("must not discover pairing") }, { _, _ -> error("must not pair") }, {
            applies++
            Result.failure(failure)
        }).retryApply(events::add)
        assertEquals(2, applies)
        assertEquals(PairingEvent.ApplyFailed(failure), events.last())
        assertFalse(events.contains(PairingEvent.PairingStarted))
    }

    @Test fun cancellationWhileWaitingForRetryDoesNotApplyAgain() = runTest {
        var applies = 0
        val job = launch {
            PairingWorkflow({ null }, { _, _ -> error("must not pair") }, {
                applies++
                Result.failure(AdbServiceNotFoundException())
            }).retryApply { }
        }
        runCurrent()
        job.cancelAndJoin()
        assertEquals(1, applies)
    }

    @Test fun discoveredPortIsUsedAndApplyFollowsSuccessfulPairing() = runTest {
        val events = mutableListOf<PairingEvent>()
        var applied = false
        PairingWorkflow({ 37123 }, { port, code ->
            assertEquals(37123, port)
            assertEquals("123456", code)
            Result.success(Unit)
        }, { applied = true; Result.success(Unit) }).run("123456", events::add)
        assertTrue(applied)
        assertEquals(300L, currentTime)
        assertEquals(listOf(PairingEvent.PairingStarted, PairingEvent.PairingSucceeded,
            PairingEvent.ApplyStarted, PairingEvent.ApplySucceeded), events)
    }

    @Test fun missingOrInvalidPortTimesOutWithoutTryingToPair() = runTest {
        for (port in listOf(null, 0, 65536)) {
            val events = mutableListOf<PairingEvent>()
            PairingWorkflow({ port }, { _, _ -> error("must not pair") },
                { error("must not apply") }).run("123456", events::add)
            assertEquals(listOf(PairingEvent.DiscoveryTimedOut), events)
        }
        assertEquals(9000L, currentTime)
    }

    @Test fun portDiscoveredDuringWaitCanCompleteSetup() = runTest {
        var port: Int? = null
        val events = mutableListOf<PairingEvent>()
        val job = launch {
            PairingWorkflow({ port }, { _, _ -> Result.success(Unit) },
                { Result.success(Unit) }).run("123456", events::add)
        }
        runCurrent()
        advanceTimeBy(400)
        port = 37123
        job.join()
        assertEquals(PairingEvent.ApplySucceeded, events.last())
    }

    @Test fun pairingFailureDoesNotApplyAndRetainsOriginalCause() = runTest {
        val failure = IOException("pair denied")
        val events = mutableListOf<PairingEvent>()
        PairingWorkflow({ 37123 }, { _, _ -> Result.failure(failure) },
            { error("must not apply") }).run("123456", events::add)
        assertEquals(listOf(PairingEvent.PairingStarted, PairingEvent.PairingFailed(failure)), events)
    }

    @Test fun applyFailureIsDistinctFromPairingFailure() = runTest {
        val failure = IOException("verify failed")
        val events = mutableListOf<PairingEvent>()
        PairingWorkflow({ 37123 }, { _, _ -> Result.success(Unit) },
            { Result.failure(failure) }).run("123456", events::add)
        assertEquals(PairingEvent.ApplyFailed(failure), events.last())
        assertFalse(events.contains(PairingEvent.ApplySucceeded))
    }

    @Test fun totalTimeoutDuringApplyDoesNotEmitSuccess() = runTest {
        val events = mutableListOf<PairingEvent>()
        PairingWorkflow({ 37123 }, { _, _ -> Result.success(Unit) },
            { awaitCancellation() }).run("123456", events::add)
        assertEquals(40_000L, currentTime)
        assertEquals(PairingEvent.TimedOut, events.last())
        assertFalse(events.contains(PairingEvent.ApplySucceeded))
    }

    @Test fun manualApplyRetryStillTimesOutWithoutRepeatingCodeAuthentication() = runTest {
        val events = mutableListOf<PairingEvent>()
        PairingWorkflow({ error("must not discover pairing") }, { _, _ -> error("must not pair") },
            { awaitCancellation() }).retryApply(events::add)
        assertEquals(25_000L, currentTime)
        assertEquals(listOf(PairingEvent.ApplyStarted, PairingEvent.TimedOut), events)
    }

    @Test fun serviceCancellationDoesNotReportFailureOrTimeout() = runTest {
        val events = mutableListOf<PairingEvent>()
        val job = launch {
            PairingWorkflow({ 37123 }, { _, _ -> awaitCancellation() },
                { error("must not apply") }).run("123456", events::add)
        }
        runCurrent()
        job.cancelAndJoin()
        assertEquals(listOf(PairingEvent.PairingStarted), events)
    }

    @Test fun replacedGenerationCannotPublishSuccessOrContinueApply() = runTest {
        val generation = OperationGeneration()
        val token = generation.next()
        val events = mutableListOf<PairingEvent>()
        val workflow = PairingWorkflow({ 37123 }, { _, _ ->
            generation.invalidate()
            Result.success(Unit)
        }, { error("stale workflow must not apply") })
        try {
            workflow.run("123456") { event ->
                if (!generation.runIfCurrent(token) { events += event }) {
                    throw CancellationException("stale workflow")
                }
            }
            fail("expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(listOf(PairingEvent.PairingStarted), events)
        }
    }

    @Test fun unexpectedExceptionIsReportedWithOriginalCause() = runTest {
        val failure = IllegalStateException("unavailable")
        val events = mutableListOf<PairingEvent>()
        PairingWorkflow({ throw failure }, { _, _ -> error("must not pair") },
            { error("must not apply") }).run("123456", events::add)
        val reported = (events.single() as PairingEvent.Failed).error
        assertEquals(failure.javaClass, reported.javaClass)
        assertEquals(failure.message, reported.message)
        // Coroutine stack-trace recovery may copy the exception and retain it as the cause.
        assertTrue(generateSequence<Throwable>(reported) { it.cause }.any { it === failure })
    }
}
