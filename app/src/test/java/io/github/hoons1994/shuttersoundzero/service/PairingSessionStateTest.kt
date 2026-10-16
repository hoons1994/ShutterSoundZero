package io.github.hoons1994.shuttersoundzero.service

import org.junit.Assert.*
import org.junit.Test

class PairingSessionStateTest {
    @Test fun newlyCreatedServiceDoesNotRunNetworkWorkFromAnOldCodeNotification() {
        val session = PairingSessionState()
        assertFalse(session.dispatchCode({ error("stale code must not pair") },
            { error("inactive session must not apply") }))
    }

    @Test fun cancellationRejectsLateCodeAndCannotBeUndoneByLateAuthentication() {
        val session = PairingSessionState()
        session.begin()
        session.markPaired()
        session.end()
        session.markPaired()
        assertFalse(session.isActive)
        assertFalse(session.hasPaired)
        assertFalse(session.dispatchCode({ error("cancelled session must not pair") },
            { error("cancelled session must not apply") }))
    }

    @Test fun explicitFreshStartAcceptsNewCodeAfterCancellation() {
        val session = PairingSessionState()
        session.begin(paired = true)
        session.end()
        session.begin()
        var paired = false
        assertTrue(session.dispatchCode({ paired = true }, { error("fresh setup must pair") }))
        assertTrue(paired)
    }

    @Test fun authenticatedRecoveryRetriesApplyInsteadOfSubmittingAnotherCode() {
        val session = PairingSessionState()
        session.begin(paired = true)
        var applied = false
        assertTrue(session.dispatchCode({ error("authenticated session must not pair again") }, { applied = true }))
        assertTrue(applied)
    }
}
