package io.github.hoons1994.shuttersoundzero.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileAuthenticationFlowTest {
    @Test fun destroyedAuthenticationOwnerCanBeReplacedAfterRotation() {
        val flow = TileAuthenticationFlow()
        assertTrue(flow.beginAuthentication())
        flow.restartAuthentication()
        assertEquals(TileAuthenticationFlowState.IDLE, flow.state.value)
        assertTrue(flow.beginAuthentication())
        assertTrue(flow.beginAction())
    }

    @Test fun rotationCannotRestartAnAlreadyRunningAction() {
        val flow = TileAuthenticationFlow()
        assertTrue(flow.beginAuthentication())
        assertTrue(flow.beginAction())
        flow.restartAuthentication()
        assertEquals(TileAuthenticationFlowState.RUNNING, flow.state.value)
        assertFalse(flow.beginAuthentication())
        assertFalse(flow.beginAction())
    }

    @Test
    fun `authenticated action can start only once`() {
        val flow = TileAuthenticationFlow()

        assertTrue(flow.beginAuthentication())
        assertTrue(flow.beginAction())
        assertFalse(flow.beginAction())
        flow.complete()

        assertEquals(TileAuthenticationFlowState.COMPLETED, flow.state.value)
    }

    @Test
    fun `recreated screen cannot restart authentication`() {
        val flow = TileAuthenticationFlow()

        assertTrue(flow.beginAuthentication())
        assertFalse(flow.beginAuthentication())
        assertEquals(TileAuthenticationFlowState.AUTHENTICATING, flow.state.value)
    }
}
