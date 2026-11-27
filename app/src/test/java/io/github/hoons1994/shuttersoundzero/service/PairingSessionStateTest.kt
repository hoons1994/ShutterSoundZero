package io.github.hoons1994.shuttersoundzero.service

import io.github.hoons1994.shuttersoundzero.core.adb.PairingDiscoveryPortState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class PairingSessionStateTest {
    @Test fun minimizingSszSuppressesRecoveryQuestionDespiteRunningPairingService() {
        val visibility = io.github.hoons1994.shuttersoundzero.core.AppVisibility
        val first = Any()
        val replacement = Any()
        val session = PairingSessionState()
        val id = session.begin(revision = 7)
        try {
            visibility.started(first)
            visibility.started(replacement)
            visibility.stopped(first)
            assertTrue(visibility.isVisible)
            visibility.stopped(replacement)
            assertFalse(visibility.isVisible)
            assertTrue(visibility.canOfferRecovery { true })
            assertFalse(visibility.canOfferRecovery { false })
            visibility.userLeftTask()
            assertFalse(visibility.canOfferRecovery { true })
            session.dismissNotification(id)
            session.requestDismissalConfirmation(id, 7, { visibility.isVisible }) {
                fail("A minimized app must not publish a recovery question")
            }
            visibility.started(first)
            assertTrue(visibility.canOfferRecovery { false })
            session.requestDismissalConfirmation(id, 7, { visibility.isVisible }) {
                fail("Returning home must not replay a background dismissal")
            }
            assertTrue(session.notificationDismissed)
            var recovered = false
            session.recoverNotification(id, 7, { true }, { fail("Blocked") }) { recovered = true }
            assertTrue(recovered)
        } finally {
            visibility.stopped(first)
            visibility.stopped(replacement)
        }
    }

    @Test fun everyDismissedCodeNotificationAsksBeforeRecovering() {
        val session = PairingSessionState()
        session.begin(targetMuted = false, revision = 8)
        var questions = 0
        var recoveries = 0
        fun ask(id: String? = session.id, revision: Long = 8, allowed: Boolean = true) {
            session.requestDismissalConfirmation(id, revision, { allowed }) { questions++ }
        }
        ask()
        assertEquals(0, questions)
        session.dismissNotification(session.id)
        ask("stale")
        ask(revision = 9)
        assertEquals(0, questions)
        ask()
        ask()
        assertEquals(1, questions)
        assertTrue(session.notificationDismissed)
        session.recoverNotification(session.id, 8, { true }, { fail("Blocked") }) { target ->
            assertFalse(target)
            recoveries++
            session.begin(targetMuted = target, revision = 8, recovering = true)
        }
        assertEquals(1, recoveries)
        session.dismissNotification(session.id)
        ask()
        ask()
        assertEquals(2, questions)
        session.setSubmitting(true)
        ask()
        session.markPaired()
        ask()
        session.end()
        ask()
        assertEquals(2, questions)
    }

    @Test fun blockedOrEndedSessionsCannotShowRecoveryQuestion() {
        val session = PairingSessionState()
        val id = session.begin(revision = 2)
        session.dismissNotification(id)
        session.requestDismissalConfirmation(id, 2, { false }) { fail("Notifications blocked") }
        assertTrue(session.notificationDismissed)
        session.end()
        session.requestDismissalConfirmation(id, 2, { true }) { fail("Session ended") }
        val busy = session.begin(revision = 3)
        session.setSubmitting(true)
        session.dismissNotification(busy)
        session.requestDismissalConfirmation(busy, 3, { true }) { fail("Code submission in progress") }
    }

    @Test fun repeatedRecoveryDoesNotRestartDiscoveryEvenIfTheCodeWindowAppearsImmediately() {
        val prompt = MutableStateFlow<PairingCodePrompt?>(null)
        val session = PairingSessionState(prompt)
        val original = session.begin(targetMuted = false, revision = 4)
        var starts = 0
        fun tap() = session.recoverNotification(session.id, 4, { true }, { fail("Blocked") }) { target ->
            assertFalse(target)
            starts++
            session.begin(targetMuted = target, revision = 4, recovering = true)
        }
        tap()
        session.finishNotificationRecovery(original)
        assertEquals(NotificationRecovery.SEARCHING, prompt.value!!.recovery)
        tap()
        session.updateRecoveryPort(true)
        assertEquals(NotificationRecovery.CODE_AVAILABLE, prompt.value!!.recovery)
        tap()
        assertEquals(1, starts)
        session.updateRecoveryPort(false)
        session.finishNotificationRecovery(session.id!!)
        tap()
        assertEquals(2, starts)
        session.dismissNotification(session.id)
        tap()
        assertEquals(3, starts)
    }

    @Test fun dismissedPromptCanRecoverTheSameTargetOnlyWithNewDiscoveryEvidence() {
        val prompt = MutableStateFlow<PairingCodePrompt?>(null)
        val session = PairingSessionState(prompt)
        val ports = PairingDiscoveryPortState()
        val oldDiscovery = ports.begin()
        ports.onPairingPort(oldDiscovery, 37123) {}
        val oldId = session.begin(targetMuted = false, revision = 8)
        session.dismissNotification(oldId)
        assertTrue(session.notificationDismissed)
        assertEquals(PairingCodePrompt(oldId, 8), prompt.value)
        session.recoverNotification(oldId, 8, { true }, { fail("Unexpected permission block") }) { target ->
            assertFalse(target)
            session.begin(targetMuted = target, revision = 8)
            val freshDiscovery = ports.begin()
            assertNull(ports.pairingPort)
            assertFalse(ports.onPairingPort(oldDiscovery, 37123) { fail("Stale window") })
            assertTrue(ports.onPairingPort(freshDiscovery, 38123) {})
        }
        assertEquals(38123, ports.pairingPort)
        session.dismissNotification(oldId)
        assertFalse(session.notificationDismissed)
        assertNotEquals(oldId, prompt.value?.sessionId)
        assertFalse(session.targetMuted)
    }

    @Test fun recoveryRequiresVisibleSessionAndPermissionAndCannotInterruptSubmission() {
        val prompt = MutableStateFlow<PairingCodePrompt?>(null)
        val session = PairingSessionState(prompt)
        val id = session.begin(revision = 3)
        var blocked = 0
        var posts = 0
        fun recover(revision: Long = 3, allowed: Boolean = true) =
            session.recoverNotification(id, revision, { allowed }, { blocked++ }, { posts++ })
        session.dismissNotification(id)
        recover(allowed = false)
        assertEquals(1, blocked)
        assertTrue(session.notificationDismissed)
        recover(revision = 4)
        session.setSubmitting(true)
        assertTrue(prompt.value!!.submitting)
        recover()
        session.markPaired()
        session.setSubmitting(false)
        assertNull(prompt.value)
        recover()
        session.end()
        val cancelled = session.begin(revision = 4)
        session.end()
        assertNull(prompt.value)
        session.recoverNotification(cancelled, 4, { true }, { blocked++ }, { posts++ })
        assertEquals(0, posts)
        assertEquals(1, blocked)
    }

    @Test fun restoreTargetIsRetainedAcrossPairingAndStaleCommandsCannotReplaceIt() {
        val session = PairingSessionState()
        val old = session.begin()
        val restore = session.begin(targetMuted = false)
        session.markPaired()
        assertFalse(session.targetMuted)
        assertFalse(session.dispatchCommand(old) { error("stale session") })
        assertTrue(session.dispatchCode(restore, { error("already paired") }, { assertFalse(session.targetMuted) }))
        session.end()
        session.begin()
        assertTrue(session.targetMuted)
    }

    @Test fun newlyCreatedServiceDoesNotRunNetworkWorkFromAnOldCodeNotification() {
        val session = PairingSessionState()
        assertFalse(session.dispatchCode("old-session", { error("stale code must not pair") },
            { error("inactive session must not apply") }))
    }

    @Test fun cancellationRejectsLateCodeAndCannotBeUndoneByLateAuthentication() {
        val session = PairingSessionState()
        val id = session.begin()
        session.markPaired()
        session.end()
        session.markPaired()
        assertFalse(session.isActive)
        assertFalse(session.hasPaired)
        assertFalse(session.dispatchCode(id, { error("cancelled session must not pair") },
            { error("cancelled session must not apply") }))
    }

    @Test fun explicitFreshStartAcceptsOnlyItsOwnCodeAfterCancellation() {
        val session = PairingSessionState()
        val old = session.begin(paired = true)
        session.end()
        val current = session.begin()
        assertNotEquals(old, current)
        assertFalse(session.dispatchCode(old, { error("old code must not pair on the new port") },
            { error("old code must not retry apply") }))
        var paired = false
        assertTrue(session.dispatchCode(current, { paired = true }, { error("fresh setup must pair") }))
        assertTrue(paired)
        assertTrue(session.isActive)
    }

    @Test fun oldRetryAndStopCannotAffectAReplacementSession() {
        val session = PairingSessionState()
        val old = session.begin(paired = true)
        session.end()
        val current = session.begin(paired = true)
        var retries = 0
        assertFalse(session.dispatchCommand(old) { retries++ })
        assertFalse(session.dispatchCommand(old, session::end))
        assertTrue(session.isActive)
        assertTrue(session.hasPaired)
        assertEquals(current, session.id)
        assertEquals(0, retries)
        assertTrue(session.dispatchCommand(current) { retries++ })
        assertEquals(1, retries)
        assertTrue(session.dispatchCommand(current, session::end))
        assertFalse(session.isActive)
    }

    @Test fun missingOrUnknownIdentityCannotControlAnActiveSession() {
        val session = PairingSessionState()
        val current = session.begin()
        for (id in listOf(null, "", "other")) {
            assertFalse(session.dispatchCommand(id, session::end))
            assertFalse(session.dispatchCode(id, { error("unbound code must not pair") },
                { error("unbound code must not apply") }))
        }
        assertTrue(session.isActive)
        assertEquals(current, session.id)
    }

    @Test fun recreatedServiceCannotAcceptThePreviousServiceIdentity() {
        val old = PairingSessionState().begin(paired = true)
        val replacement = PairingSessionState()
        val current = replacement.begin()
        assertNotEquals(old, current)
        assertFalse(replacement.dispatchCommand(old, replacement::end))
        assertTrue(replacement.isActive)
        assertEquals(current, replacement.id)
    }

    @Test fun authenticatedRecoveryRetriesApplyInsteadOfSubmittingAnotherCode() {
        val session = PairingSessionState()
        val id = session.begin(paired = true)
        var applied = false
        assertTrue(session.dispatchCode(id, { error("authenticated session must not pair again") }, { applied = true }))
        assertTrue(applied)
    }
}
