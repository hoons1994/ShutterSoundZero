package io.github.hoons1994.shuttersoundzero.ui.settings

import androidx.lifecycle.ViewModelStore
import io.github.hoons1994.shuttersoundzero.update.AppVersion
import io.github.hoons1994.shuttersoundzero.update.LatestRelease
import io.github.hoons1994.shuttersoundzero.update.UpdateCheckException
import io.github.hoons1994.shuttersoundzero.update.UpdateCheckFailure
import io.github.hoons1994.shuttersoundzero.update.UpdateCheckResult
import io.github.hoons1994.shuttersoundzero.update.UpdateStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val available = UpdateCheckResult(
        UpdateStatus.AVAILABLE, LatestRelease("v1.5.14", AppVersion(1, 5, 14))
    )

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun idleDoesNotFetchAndRepeatedTapsShareOneRequest() = runTest(dispatcher) {
        val pending = CompletableDeferred<UpdateCheckResult>()
        var requests = 0
        val model = AppUpdateViewModel("1.5.13-debug") { installed ->
            assertEquals("1.5.13-debug", installed)
            requests++
            pending.await()
        }
        assertEquals(0, requests)
        model.checkForUpdates()
        model.checkForUpdates()
        runCurrent()
        assertEquals(1, requests)
        assertTrue(model.uiState.value.isChecking)
        pending.complete(available)
        advanceUntilIdle()
        assertEquals(AppUpdateUiState(result = available), model.uiState.value)
    }

    @Test fun failedRefreshClearsPreviousResultAndAllowsRetry() = runTest(dispatcher) {
        var requests = 0
        val model = AppUpdateViewModel("1.5.13") {
            requests++
            if (requests == 2) throw UpdateCheckException(UpdateCheckFailure.RATE_LIMITED)
            available
        }
        model.checkForUpdates()
        advanceUntilIdle()
        assertEquals(available, model.uiState.value.result)
        model.checkForUpdates()
        assertNull(model.uiState.value.result)
        advanceUntilIdle()
        assertEquals(AppUpdateUiState(failure = UpdateCheckFailure.RATE_LIMITED), model.uiState.value)
        model.checkForUpdates()
        advanceUntilIdle()
        assertEquals(AppUpdateUiState(result = available), model.uiState.value)
        assertEquals(3, requests)
    }

    @Test fun clearingViewModelCancelsRequestWithoutReportingNetworkFailure() = runTest(dispatcher) {
        var cancelled = false
        val model = AppUpdateViewModel("1.5.13") {
            try { awaitCancellation() } finally { cancelled = true }
        }
        val store = ViewModelStore()
        store.put("updates", model)
        model.checkForUpdates()
        runCurrent()
        store.clear()
        advanceUntilIdle()
        assertTrue(cancelled)
        assertEquals(AppUpdateUiState(), model.uiState.value)
    }
}
