package io.github.hoons1994.shuttersoundzero.pairing

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import io.github.hoons1994.shuttersoundzero.data.PreferencesRepository
import io.github.hoons1994.shuttersoundzero.data.SetupIssue
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** Fault injection on the real feedback adapter without enabling wireless debugging or ADB. */
class PairingWorkflowFeedbackInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: PreferencesRepository
    private var originalIssue: SetupIssue? = null
    private var prepared = false
    private var completing = false
    private var completed: Boolean? = null
    private var failures = 0

    @Before fun prepare() {
        assumeTrue("Pairing feedback tests use an isolated emulator",
            Build.FINGERPRINT.startsWith("generic") || Build.FINGERPRINT.startsWith("google/sdk_gphone") ||
                Build.MODEL.contains("sdk_gphone"))
        prefs = PreferencesRepository.getInstance(context)
        originalIssue = prefs.lastSetupIssue
        prepared = true
    }

    @After fun restore() {
        if (prepared) prefs.lastSetupIssue = originalIssue
    }

    private fun feedback(
        cleanup: () -> Result<Unit>,
        complete: (Boolean) -> Unit = { completed = it }
    ) = PairingWorkflowFeedback(
        context = context,
        sessionId = "feedback-regression",
        stopPairingDiscovery = {},
        restartPairingDiscovery = {},
        onCompleting = { completing = true },
        onFailed = { completing = false; failures++ },
        onCompleted = complete,
        disableWirelessDebugging = cleanup
    )

    @Test fun cleanupExceptionKeepsInitialPairingAndAppliedTargetSuccessful() = runBlocking {
        for (target in listOf(true, false)) {
            completing = false
            completed = null
            prefs.lastSetupIssue = SetupIssue.CAMERA_APPLY
            val attempted = mutableListOf<Boolean>()
            val events = mutableListOf<PairingEvent>()
            val feedback = feedback(cleanup = { throw SecurityException("injected permission query failure") })
            PairingWorkflow(
                discoveredPort = { 37123 },
                pair = { _, _ -> Result.success(Unit) },
                applyCameraMute = { attempted += it; Result.success(Unit) },
                targetMuted = target
            ).run("123456") { event ->
                events += event
                feedback.handle(event)
            }
            assertEquals(listOf(target), attempted)
            assertEquals(PairingEvent.ApplySucceeded, events.last())
            assertEquals(false, completed)
            assertTrue(completing)
            assertEquals(0, failures)
            assertNull(prefs.lastSetupIssue)
        }
    }

    @Test fun cleanupFailureResultAlsoKeepsTheVerifiedPairingSuccess() {
        feedback({ Result.failure(IOException("cleanup unavailable")) }).handle(PairingEvent.ApplySucceeded)
        assertEquals(false, completed)
        assertTrue(completing)
        assertEquals(0, failures)
    }

    @Test fun failureTransitionClearsTheCompletingFlagForRetry() {
        completing = true
        feedback({ Result.success(Unit) }).handle(PairingEvent.ApplyFailed(IOException("apply failed")))
        assertFalse(completing)
        assertEquals(1, failures)
        assertEquals(SetupIssue.CAMERA_APPLY, prefs.lastSetupIssue)
    }

    @Test fun failedCompletionResetsTheFlagBeforeTheErrorIsPropagated() {
        val failure = IllegalStateException("injected completion failure")
        val adapter = feedback({ Result.success(Unit) }, complete = { throw failure })
        assertSame(failure, assertThrows(IllegalStateException::class.java) {
            adapter.handle(PairingEvent.ApplySucceeded)
        })
        assertFalse(completing)
        assertEquals(1, failures)
    }

    @Test fun cancellationIsPropagatedAndDoesNotLeaveCompletionBlocked() {
        val cancellation = CancellationException("injected cancellation")
        val adapter = feedback(cleanup = { throw cancellation })
        assertSame(cancellation, assertThrows(CancellationException::class.java) {
            adapter.handle(PairingEvent.ApplySucceeded)
        })
        assertFalse(completing)
        assertNull(completed)
        assertEquals(1, failures)
    }
}
