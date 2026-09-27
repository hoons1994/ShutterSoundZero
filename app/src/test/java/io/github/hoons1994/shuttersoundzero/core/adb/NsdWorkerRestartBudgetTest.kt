package io.github.hoons1994.shuttersoundzero.core.adb

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NsdWorkerRestartBudgetTest {
    @Test fun repeatedRestartsOpenARecoverableCooldown() {
        val budget = NsdWorkerRestartBudget(maxRestarts = 3, windowMs = 60_000, cooldownMs = 30_000)
        repeat(2) { budget.recordRestart(it * 1_000L) }
        assertTrue(budget.canStart(2_000))
        budget.recordRestart(2_000)
        assertFalse(budget.canStart(31_999))
        assertTrue(budget.canStart(32_000))
    }

    @Test fun spacedRestartsDoNotExhaustBudget() {
        val budget = NsdWorkerRestartBudget(maxRestarts = 3, windowMs = 60_000, cooldownMs = 30_000)
        repeat(10) { budget.recordRestart(it * 60_000L) }
        assertTrue(budget.canStart(600_000))
    }

    @Test fun successfulResolutionClearsPreviousFailures() {
        val budget = NsdWorkerRestartBudget(maxRestarts = 3, windowMs = 60_000, cooldownMs = 30_000)
        budget.recordRestart(0)
        budget.recordRestart(1_000)
        budget.recovered()
        budget.recordRestart(2_000)
        assertTrue(budget.canStart(3_000))
    }
}
