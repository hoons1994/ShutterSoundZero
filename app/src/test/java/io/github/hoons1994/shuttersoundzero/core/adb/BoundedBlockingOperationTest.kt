package io.github.hoons1994.shuttersoundzero.core.adb

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

class BoundedBlockingOperationTest {
    @Test fun timeoutClosesOwnedResourceAndDrainsWorker() {
        val release = CountDownLatch(1)
        val exited = AtomicBoolean()
        assertThrows(BlockingOperationTimeoutException::class.java) {
            BoundedBlockingOperation.run(50, "owned-timeout", release::countDown) {
                try { release.await() } finally { exited.set(true) }
            }
        }
        assertTrue(exited.get())
        assertEquals(42, BoundedBlockingOperation.run(1_000, "next") { 42 })
    }

    @Test fun hostileWorkerRetainsSlotUntilItActuallyExits() {
        val release = CountDownLatch(1)
        val worker = AtomicReference<Thread>()
        try {
            assertThrows(BlockingOperationTimeoutException::class.java) {
                BoundedBlockingOperation.run(100, "hostile-worker") {
                    worker.set(Thread.currentThread())
                    while (release.count != 0L) {
                        try { release.await() } catch (_: InterruptedException) { }
                    }
                }
            }
            repeat(3) {
                assertThrows(BlockingOperationBusyException::class.java) {
                    BoundedBlockingOperation.run(1_000, "must-not-start") { fail("worker accumulated") }
                }
            }
        } finally {
            release.countDown()
            worker.get()?.join(2_000)
        }
        assertFalse(worker.get().isAlive)
        assertEquals("ready", BoundedBlockingOperation.run(1_000, "after-drain") { "ready" })
    }

    @Test fun workerFailureIsUnwrappedForCaller() {
        val failure = IllegalStateException("boom")
        val error = assertThrows(IllegalStateException::class.java) {
            BoundedBlockingOperation.run(1_000, "failure") { throw failure }
        }
        assertSame(failure, error)
        assertTrue(BoundedBlockingOperation.run(1_000, "after-error") { true })
    }

    @Test fun interruptedCallerClosesResourceAndPreservesInterruptStatus() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val closed = AtomicBoolean()
        val preserved = AtomicBoolean()
        val result = AtomicReference<Throwable>()
        val caller = Thread {
            try {
                BoundedBlockingOperation.run(10_000, "interrupted-worker", {
                    closed.set(true)
                    release.countDown()
                }) { entered.countDown(); release.await() }
                result.set(AssertionError("Expected interruption"))
            } catch (error: InterruptedException) {
                preserved.set(Thread.currentThread().isInterrupted)
            } catch (error: Throwable) { result.set(error) }
        }
        caller.start()
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            caller.interrupt()
            caller.join(2_000)
            assertFalse(caller.isAlive)
            assertNull(result.get())
            assertTrue(closed.get())
            assertTrue(preserved.get())
        } finally { release.countDown(); caller.interrupt(); caller.join(2_000) }
    }

    @Test fun failingCancellationHookStillInterruptsWorker() {
        val exited = AtomicBoolean()
        val error = assertThrows(BlockingOperationTimeoutException::class.java) {
            BoundedBlockingOperation.run(50, "hook-failure", { error("close failed") }) {
                try { CountDownLatch(1).await() } finally { exited.set(true) }
            }
        }
        assertTrue(exited.get())
        assertEquals("close failed", error.suppressed.single().message)
    }

    @Test fun invalidDeadlineDoesNotOccupySlot() {
        assertThrows(IllegalArgumentException::class.java) { BoundedBlockingOperation.run(0, "invalid") { } }
        assertEquals(1, BoundedBlockingOperation.run(1_000, "valid") { 1 })
    }
}
