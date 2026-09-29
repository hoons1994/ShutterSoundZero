package io.github.hoons1994.shuttersoundzero.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticationCallbackGateTest {
    @Test fun cancelledOwnerCannotBeUnlockedByLateSuccess() {
        val gate = AuthenticationCallbackGate()
        gate.cancel()
        assertFalse(gate.succeed(isResumed = true))
        assertFalse(gate.fail())
        assertFalse(gate.resume())
    }

    @Test fun credentialSuccessWaitsForForegroundAndIsDeliveredOnce() {
        val gate = AuthenticationCallbackGate()
        assertFalse(gate.succeed(isResumed = false))
        assertFalse(gate.succeed(isResumed = true))
        assertFalse(gate.fail())
        assertTrue(gate.resume())
        assertFalse(gate.resume())
        assertFalse(gate.succeed(isResumed = true))
    }

    @Test fun destroyedOwnerDiscardsDeferredCredentialSuccess() {
        val gate = AuthenticationCallbackGate()
        assertFalse(gate.succeed(isResumed = false))
        gate.cancel()
        assertFalse(gate.resume())
    }

    @Test fun failedAuthenticationCannotLaterSucceed() {
        val gate = AuthenticationCallbackGate()
        assertTrue(gate.fail())
        assertFalse(gate.succeed(isResumed = true))
    }

    @Test fun foregroundSuccessIsDeliveredOnce() {
        val gate = AuthenticationCallbackGate()
        assertTrue(gate.succeed(isResumed = true))
        assertFalse(gate.succeed(isResumed = true))
        assertFalse(gate.fail())
    }
}
