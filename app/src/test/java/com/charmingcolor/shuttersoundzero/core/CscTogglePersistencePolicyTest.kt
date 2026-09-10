package com.charmingcolor.shuttersoundzero.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CscTogglePersistencePolicyTest {

    @Test
    fun successfulAttempt_persistsRequestedMuteState() {
        assertTrue(
            CscTogglePersistencePolicy.resolve(
                previousDesiredMute = false,
                targetMuted = true,
                stateApplied = true
            )
        )

        assertFalse(
            CscTogglePersistencePolicy.resolve(
                previousDesiredMute = true,
                targetMuted = false,
                stateApplied = true
            )
        )
    }

    @Test
    fun failedAttempt_preservesPreviouslyConfirmedPreference() {
        assertTrue(
            CscTogglePersistencePolicy.resolve(
                previousDesiredMute = true,
                targetMuted = false,
                stateApplied = false
            )
        )

        assertFalse(
            CscTogglePersistencePolicy.resolve(
                previousDesiredMute = false,
                targetMuted = true,
                stateApplied = false
            )
        )
    }
}
