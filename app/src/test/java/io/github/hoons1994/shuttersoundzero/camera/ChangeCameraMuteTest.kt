package io.github.hoons1994.shuttersoundzero.camera

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChangeCameraMuteTest {
    @Test fun verifiedCompletionNotifiesWithExactTargetAfterCleanup() = runTest {
        for (muted in listOf(true, false)) {
            val events = mutableListOf<String>()
            val change = ChangeCameraMute(
                apply = { events += "apply:$it"; Result.success(Unit) },
                verify = { events += "verify:$it"; true },
                disableWirelessDebugging = { events += "cleanup"; Result.success(Unit) },
                onApplied = { events += "completed:$it" }
            )
            assertTrue(change(muted).isSuccess)
            assertEquals(listOf("apply:$muted", "verify:$muted", "cleanup", "completed:$muted"), events)
        }
    }

    @Test fun failedOrUnverifiedChangeCannotQueueAnInvitation() = runTest {
        for (applySucceeds in listOf(true, false)) {
            val completed = mutableListOf<Boolean>()
            val change = ChangeCameraMute(
                apply = { if (applySucceeds) Result.success(Unit) else Result.failure(IOException()) },
                verify = { false },
                disableWirelessDebugging = { error("Must not clean up") },
                onApplied = { completed += it }
            )
            assertTrue(change(true).isFailure)
            assertTrue(completed.isEmpty())
        }
    }

    @Test fun cleanupFailureStillNotifiesVerifiedMuteCompletion() = runTest {
        val completed = mutableListOf<Boolean>()
        val change = ChangeCameraMute(
            { Result.success(Unit) }, { true }, { Result.failure(IOException("cleanup failed")) },
            onApplied = { completed += it }
        )
        assertTrue(change(true).isSuccess)
        assertEquals(listOf(true), completed)
    }

    @Test fun cancellingVerificationCannotQueueAnInvitation() = runTest {
        val completed = mutableListOf<Boolean>()
        val change = ChangeCameraMute(
            { Result.success(Unit) }, { awaitCancellation() }, { error("Must not clean up") },
            onApplied = { completed += it }
        )
        val job = launch { change(true) }
        runCurrent()
        job.cancelAndJoin()
        assertTrue(completed.isEmpty())
    }

    @Test fun bothMuteAndRestoreConfirmBeforeCleanup() = runTest {
        for (muted in listOf(true, false)) {
            val calls = mutableListOf<String>()
            val change = ChangeCameraMute(
                apply = { calls += "apply:$it"; Result.success(Unit) },
                verify = { calls += "verify:$it"; true },
                disableWirelessDebugging = { calls += "cleanup"; Result.success(Unit) }
            )
            assertTrue(change(muted).getOrThrow().wirelessCleanup.isSuccess)
            assertEquals(listOf("apply:$muted", "verify:$muted", "cleanup"), calls)
        }
    }

    @Test fun failedApplyPreservesCauseAndDoesNotVerifyOrCleanup() = runTest {
        val failure = IOException("connection")
        val change = ChangeCameraMute(
            { Result.failure(failure) }, { error("must not verify") }, { error("must not clean up") }
        )
        assertSame(failure, change(true).exceptionOrNull())
    }

    @Test fun stateMismatchKeepsWirelessDebuggingAvailableForRecovery() = runTest {
        val change = ChangeCameraMute(
            { Result.success(Unit) }, { false }, { error("must not clean up") }
        )
        assertTrue(change(true).exceptionOrNull() is IOException)
    }

    @Test fun cleanupFailureStillReportsAppliedCameraSetting() = runTest {
        val failure = SecurityException("cleanup denied")
        val change = ChangeCameraMute(
            { Result.success(Unit) }, { true }, { Result.failure(failure) }
        )
        assertSame(failure, change(false).getOrThrow().wirelessCleanup.exceptionOrNull())
    }

    @Test fun cancellingVerificationCannotTurnOffWirelessDebugging() = runTest {
        val change = ChangeCameraMute(
            { Result.success(Unit) }, { awaitCancellation() }, { error("must not clean up") }
        )
        val job = launch { change(true) }
        runCurrent()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }

    @Test fun cancellationIsNotConvertedToAnOrdinaryFailure() = runTest {
        val cancellation = CancellationException("cancelled")
        val change = ChangeCameraMute(
            { throw cancellation }, { error("must not verify") }, { error("must not clean up") }
        )
        try {
            change(true)
            fail("expected cancellation")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }
}
