package com.charmingcolor.shuttersoundzero.core.adb

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

    @Test(expected = IllegalStateException::class)
    fun unmatchedExit_failsFast() {
        CameraMuteOperationGate.exit()
    }
}
