package io.github.hoons1994.shuttersoundzero.core.adb

import org.junit.Assert.*
import org.junit.Test

class SerialResolutionQueueTest {
    private class Fixture(limit: Int = 64) {
        val started = mutableListOf<SerialResolutionQueue.Attempt<String>>()
        val stopped = mutableListOf<SerialResolutionQueue.Attempt<String>>()
        val timers = mutableListOf<() -> Unit>()
        val resolved = mutableListOf<String>()
        val failures = mutableListOf<Int>()
        val queue = SerialResolutionQueue<String>(started::add, stopped::add, { _, task -> timers.add(task) }, limit)
        fun add(owner: Any, value: String) = queue.submit(owner, value, value, resolved::add, failures::add)
        fun retry() = timers.removeAt(0).invoke()
    }

    @Test fun connectAndPairingAreResolvedSeriallyInEitherOrder() {
        for (order in listOf(listOf("connect", "pair"), listOf("pair", "connect"))) {
            val f = Fixture()
            order.forEach { f.add(Any(), it) }
            assertEquals(1, f.started.size)
            f.queue.resolved(f.started[0], order[0])
            assertEquals(2, f.started.size)
            f.queue.resolved(f.started[1], order[1])
            assertEquals(order, f.resolved)
        }
    }

    @Test fun duplicateFoundEventsAreCoalesced() {
        val f = Fixture()
        val owner = Any()
        repeat(20) { f.add(owner, "pair") }
        assertEquals(1, f.started.size)
        f.queue.resolved(f.started[0], "pair")
        assertEquals(1, f.started.size)
    }

    @Test fun alreadyActiveRetriesHaveFreshIdentitiesAndIgnoreOldCallbacks() {
        val f = Fixture()
        f.add(Any(), "pair")
        val old = f.started[0]
        f.queue.failed(old, 3, true)
        f.retry()
        val current = f.started[1]
        assertNotSame(old, current)
        f.queue.resolved(old, "stale")
        assertTrue(f.resolved.isEmpty())
        f.queue.resolved(current, "pair")
        assertEquals(listOf("pair"), f.resolved)
    }

    @Test fun transientFailuresHaveABoundedRetryBudget() {
        val f = Fixture()
        f.add(Any(), "pair")
        repeat(3) { index ->
            f.queue.failed(f.started[index], 3, true)
            if (index < 2) f.retry()
        }
        assertEquals(3, f.started.size)
        assertEquals(listOf(3), f.failures)
        assertTrue(f.timers.isEmpty())
    }

    @Test fun cancelledRetryDoesNotRestartLater() {
        val f = Fixture()
        val owner = Any()
        f.add(owner, "pair")
        f.queue.failed(f.started[0], 3, true)
        f.queue.cancel(owner)
        f.retry()
        assertEquals(1, f.started.size)
        assertTrue(f.failures.isEmpty())
    }

    @Test fun legacyCancellationRetainsNativeSlotButDetachesStoppedSession() {
        val f = Fixture()
        val oldOwner = Any()
        f.add(oldOwner, "old")
        f.queue.cancel(oldOwner)
        f.add(Any(), "new")
        assertEquals(1, f.started.size)
        assertEquals(1, f.stopped.size)
        assertNull(f.started[0].request.onSuccess)
        assertNull(f.started[0].request.onFailure)
        f.queue.resolved(f.started[0], "ignored")
        assertEquals(2, f.started.size)
        f.queue.resolved(f.started[1], "new")
        assertEquals(listOf("new"), f.resolved)
    }

    @Test fun modernStopAcknowledgementReleasesSlotAndLateSuccessIsIgnored() {
        val f = Fixture()
        val owner = Any()
        f.add(owner, "old")
        f.queue.cancel(owner)
        f.add(Any(), "new")
        f.queue.stopped(f.started[0])
        assertEquals(2, f.started.size)
        f.queue.resolved(f.started[0], "stale")
        f.queue.resolved(f.started[1], "new")
        assertEquals(listOf("new"), f.resolved)
    }

    @Test fun timeoutIsReportedOnceAndRequiresNativeCompletionBeforeNextRequest() {
        val f = Fixture()
        f.add(Any(), "hung")
        f.add(Any(), "next")
        f.queue.expire(f.started[0])
        f.queue.expire(f.started[0])
        assertEquals(listOf(SerialResolutionQueue.ERROR_TIMEOUT), f.failures)
        assertEquals(1, f.stopped.size)
        assertEquals(1, f.started.size)
        f.queue.failed(f.started[0], 0, false)
        assertEquals(2, f.started.size)
    }

    @Test fun lostServiceCancelsOnlyItsOwnRequest() {
        val f = Fixture()
        val owner = Any()
        f.add(owner, "one")
        f.add(owner, "two")
        f.add(owner, "three")
        f.queue.cancel(owner, "two")
        f.queue.resolved(f.started[0], "one")
        assertEquals("three", f.started[1].request.value)
    }

    @Test fun queueHasAFixedCapacity() {
        val f = Fixture(2)
        repeat(3) { f.add(Any(), "$it") }
        assertEquals(listOf(SerialResolutionQueue.ERROR_QUEUE_FULL), f.failures)
    }
}
