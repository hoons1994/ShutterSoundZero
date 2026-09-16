package io.github.hoons1994.shuttersoundzero.core.adb

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraMuteOperationGateTest {
    @After
    fun tearDown() {
        CameraMuteOperationGate.resetForTest()
    }

    @Test
    fun pendingOperation_remainsTrueUntilAllQueuedOperationsExit() {
        CameraMuteOperationGate.enter()
        CameraMuteOperationGate.enter()

        assertTrue(CameraMuteOperationGate.hasPendingOperations())

        CameraMuteOperationGate.exit()
        assertTrue(CameraMuteOperationGate.hasPendingOperations())

        CameraMuteOperationGate.exit()
        assertFalse(CameraMuteOperationGate.hasPendingOperations())
    }

    @Test
    fun cleanup_isRejectedWhileOperationIsPending() {
        CameraMuteOperationGate.enter()

        var cleanupRan = false
        val cleaned = CameraMuteOperationGate.runCleanupIfIdle {
            cleanupRan = true
        }

        assertFalse(cleaned)
        assertFalse(cleanupRan)

        CameraMuteOperationGate.exit()
    }

    @Test
    fun operationEntry_waitsUntilCleanupCriticalSectionFinishes() {
        val cleanupStarted = CountDownLatch(1)
        val allowCleanupFinish = CountDownLatch(1)
        val operationEntered = CountDownLatch(1)
        val cleanupCompleted = AtomicBoolean(false)

        val cleanupThread = thread(start = true, name = "cleanup-thread") {
            val cleaned = CameraMuteOperationGate.runCleanupIfIdle {
                cleanupStarted.countDown()
                allowCleanupFinish.await(2, TimeUnit.SECONDS)
            }
            cleanupCompleted.set(cleaned)
        }

        assertTrue(cleanupStarted.await(2, TimeUnit.SECONDS))

        val operationThread = thread(start = true, name = "operation-thread") {
            CameraMuteOperationGate.enter()
            try {
                operationEntered.countDown()
            } finally {
                CameraMuteOperationGate.exit()
            }
        }

        try {
            assertFalse(operationEntered.await(100, TimeUnit.MILLISECONDS))
        } finally {
            allowCleanupFinish.countDown()
        }

        assertTrue(operationEntered.await(2, TimeUnit.SECONDS))
        cleanupThread.join(2_000)
        operationThread.join(2_000)
        assertTrue(cleanupCompleted.get())
        assertFalse(cleanupThread.isAlive)
        assertFalse(operationThread.isAlive)
    }

    @Test(expected = IllegalStateException::class)
    fun unmatchedExit_failsFast() {
        CameraMuteOperationGate.exit()
    }
}
