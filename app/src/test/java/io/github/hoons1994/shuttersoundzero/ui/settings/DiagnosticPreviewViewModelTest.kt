package io.github.hoons1994.shuttersoundzero.ui.settings

import io.github.hoons1994.shuttersoundzero.R
import io.github.hoons1994.shuttersoundzero.diagnostics.DiagnosticReportBuilder.Report
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticPreviewViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val before = Report("report", "previous events")

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun sendingStaysBlockedUntilTheReplacementReportIsReady() = runTest(dispatcher) {
        val preview = DiagnosticPreviewViewModel().apply { report = before }
        val cleared = CompletableDeferred<Result<Boolean>>()
        val refreshed = CompletableDeferred<Report>()
        val messages = mutableListOf<Int>()
        preview.clearLog({ cleared.await() }, { refreshed.await() }, messages::add)
        assertTrue(preview.isClearing)
        cleared.complete(Result.success(true))
        runCurrent()
        assertTrue(preview.isClearing)
        val after = Report("report", "no events")
        refreshed.complete(after)
        runCurrent()
        assertSame(after, preview.report)
        assertFalse(preview.isClearing)
        assertEquals(listOf(R.string.report_logs_deleted), messages)
    }

    @Test fun failedDeleteRetainsReportButFailedRefreshAfterDeletionDiscardsIt() = runTest(dispatcher) {
        val preview = DiagnosticPreviewViewModel().apply { report = before }
        val messages = mutableListOf<Int>()
        preview.clearLog({ Result.failure(IOException()) }, { error("Unexpected reload") }, messages::add)
        assertSame(before, preview.report)
        preview.clearLog({ Result.success(true) }, { throw IOException() }, messages::add)
        assertNull(preview.report)
        assertFalse(preview.isClearing)
        assertEquals(listOf(R.string.report_delete_failed, R.string.report_reload_failed), messages)
    }
}
