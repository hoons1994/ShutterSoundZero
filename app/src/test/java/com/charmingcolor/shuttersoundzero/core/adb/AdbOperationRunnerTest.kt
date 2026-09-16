package com.charmingcolor.shuttersoundzero.core.adb

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AdbOperationRunnerTest {
    @Test fun failedAcquisitionReturnsFailureAndNextRequestCanRetry() = runBlocking {
        var allowed = false
        var pending = 0
        var releases = 0
        var cleanups = 0
        val leases = MulticastLockLeaseManager({ allowed }, { releases++ })
        val runner = AdbOperationRunner({ pending++ }, { pending-- }, leases::acquire, { cleanups++ }, {})
        val failed = runner.run { error("must not run") }
        assertTrue(failed.exceptionOrNull() is IllegalStateException)
        assertEquals(0, pending)
        assertEquals(0, leases.activeLeaseCountForTest())
        allowed = true
        assertEquals(42, runner.run { 42 }.getOrThrow())
        assertEquals(0, pending)
        assertEquals(0, leases.activeLeaseCountForTest())
        assertEquals(1, releases)
        assertEquals(2, cleanups)
    }

    @Test fun thrownAcquisitionIsAlsoInsideResultBoundary() = runBlocking {
        val failure = SecurityException("denied")
        var gate = 0
        val runner = AdbOperationRunner({ gate++ }, { gate-- }, { throw failure }, {}, {})
        assertSame(failure, runner.run { Unit }.exceptionOrNull())
        assertEquals(0, gate)
    }

    @Test fun acquisitionCancellationIsNotConvertedToFailure() {
        val cancellation = CancellationException("cancel acquire")
        var pending = 0
        val runner = AdbOperationRunner({ pending++ }, { pending-- }, { throw cancellation }, {}, {})
        assertSame(cancellation, assertThrows(CancellationException::class.java) { runBlocking { runner.run {} } })
        assertEquals(0, pending)
    }

    @Test fun bodyCancellationReleasesEverythingAndKeepsItsIdentity() {
        var pending = 0
        var released = 0
        var cleaned = 0
        val runner = AdbOperationRunner({ pending++ }, { pending-- }, { AutoCloseable { released++ } }, { cleaned++ }, {})
        val cancellation = CancellationException("cancel body")
        assertSame(cancellation, assertThrows(CancellationException::class.java) {
            runBlocking { runner.run { throw cancellation } }
        })
        assertEquals(0, pending)
        assertEquals(1, released)
        assertEquals(1, cleaned)
        assertTrue(runBlocking { runner.run {} }.isSuccess)
    }

    @Test fun cancellingMutexWaiterDoesNotReleaseAnotherOperationsLease() = runBlocking {
        var pending = 0
        var acquired = 0
        var released = 0
        val finish = CompletableDeferred<Unit>()
        val runner = AdbOperationRunner({ pending++ }, { pending-- }, {
            acquired++
            AutoCloseable { released++ }
        }, {}, {})
        val first = launch(start = CoroutineStart.UNDISPATCHED) { runner.run { finish.await() } }
        val second = launch(start = CoroutineStart.UNDISPATCHED) { runner.run { error("cancelled waiter ran") } }
        assertEquals(2, pending)
        second.cancelAndJoin()
        assertEquals(1, pending)
        assertEquals(1, acquired)
        assertEquals(0, released)
        finish.complete(Unit)
        first.join()
        assertEquals(0, pending)
        assertEquals(1, released)
    }

    @Test fun cleanupExceptionsCannotHideCancellationOrPreventLeaseRelease() {
        var released = 0
        var logged = 0
        val runner = AdbOperationRunner({}, {}, { AutoCloseable { released++ } }, {
            throw IOException("cleanup")
        }, { logged++ })
        val cancellation = CancellationException("original")
        assertSame(cancellation, assertThrows(CancellationException::class.java) {
            runBlocking { runner.run { throw cancellation } }
        })
        assertTrue(runBlocking { runner.run {} }.isSuccess)
        assertEquals(2, released)
        assertEquals(2, logged)
    }
}
