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
