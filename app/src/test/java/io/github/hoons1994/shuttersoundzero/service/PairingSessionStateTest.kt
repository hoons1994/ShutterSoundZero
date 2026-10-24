package io.github.hoons1994.shuttersoundzero.service

import org.junit.Assert.*
import org.junit.Test

class PairingSessionStateTest {
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
