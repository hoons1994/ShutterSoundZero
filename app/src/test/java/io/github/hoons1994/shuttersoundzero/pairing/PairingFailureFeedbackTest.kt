package io.github.hoons1994.shuttersoundzero.pairing

import io.github.hoons1994.shuttersoundzero.data.SetupIssue
import io.github.hoons1994.shuttersoundzero.ui.notification.PairingNotificationState
import io.github.muntashirakon.adb.PairingFailureException
import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.*
import org.junit.Test

class PairingFailureFeedbackTest {
    @Test fun identityFailureNeverAsksForAnotherPairingCode() {
        val feedback = PairingFailureFeedback.from(failure(PairingFailureException.Phase.IDENTITY))
        assertEquals(SetupIssue.PAIRING_IDENTITY, feedback.issue)
        assertEquals(PairingNotificationState.PAIRING_IDENTITY_FAILED, feedback.notification)
        assertEquals(feedback.notification, PairingFailureFeedback.notificationStateFor(feedback.issue))
    }

    @Test fun tlsAndCodeAuthenticationFailuresRequireDifferentRecovery() {
        for (phase in listOf(PairingFailureException.Phase.TLS_HANDSHAKE, PairingFailureException.Phase.TLS_EXPORT)) {
            assertEquals(SetupIssue.PAIRING_TLS, PairingFailureFeedback.from(failure(phase)).issue)
        }
        assertEquals(SetupIssue.PAIRING_AUTHENTICATION,
            PairingFailureFeedback.from(failure(PairingFailureException.Phase.CODE_AUTHENTICATION)).issue)
        assertEquals(SetupIssue.PAIRING_CONNECTION,
            PairingFailureFeedback.from(failure(PairingFailureException.Phase.TCP_CONNECT)).issue)
    }

    @Test fun codeAuthenticationTimeoutDoesNotBlameTheCode() {
        val feedback = PairingFailureFeedback.from(PairingFailureException(
            PairingFailureException.Phase.CODE_AUTHENTICATION, SocketTimeoutException()))
        assertEquals(SetupIssue.PAIRING_TIMEOUT, feedback.issue)
        assertEquals(PairingNotificationState.PAIRING_TIMEOUT, feedback.notification)
    }

    private fun failure(phase: PairingFailureException.Phase) = PairingFailureException(phase, IOException())
}
