package io.github.hoons1994.shuttersoundzero.pairing

import io.github.hoons1994.shuttersoundzero.core.adb.LocalNetworkPermissionRequiredException
import io.github.hoons1994.shuttersoundzero.data.SetupIssue
import io.github.hoons1994.shuttersoundzero.ui.notification.PairingNotificationState
import io.github.muntashirakon.adb.PairingFailureException

internal data class PairingFailureFeedback(val issue: SetupIssue, val notification: PairingNotificationState) {
    companion object {
        fun from(error: Throwable?): PairingFailureFeedback {
            val issue = when {
                error is LocalNetworkPermissionRequiredException -> SetupIssue.LOCAL_NETWORK_PERMISSION
                error is PairingFailureException -> when {
                    error.phase == PairingFailureException.Phase.IDENTITY -> SetupIssue.PAIRING_IDENTITY
                    error.reason == PairingFailureException.Reason.TIMEOUT -> SetupIssue.PAIRING_TIMEOUT
                    error.phase == PairingFailureException.Phase.TLS_HANDSHAKE ||
                        error.phase == PairingFailureException.Phase.TLS_EXPORT -> SetupIssue.PAIRING_TLS
                    error.phase == PairingFailureException.Phase.CODE_AUTHENTICATION -> SetupIssue.PAIRING_AUTHENTICATION
                    else -> SetupIssue.PAIRING_CONNECTION
                }
                else -> SetupIssue.PAIRING_CONNECTION
            }
            val state = notificationStateFor(issue) ?: when (issue) {
                SetupIssue.LOCAL_NETWORK_PERMISSION -> PairingNotificationState.LOCAL_NETWORK_PERMISSION_REQUIRED
                SetupIssue.PAIRING_TIMEOUT -> PairingNotificationState.PAIRING_TIMEOUT
                else -> PairingNotificationState.PAIRING_FAILED
            }
            return PairingFailureFeedback(issue, state)
        }

        /** Keep specific recovery instructions visible while discovery restarts. */
        fun notificationStateFor(issue: SetupIssue?): PairingNotificationState? = when (issue) {
            SetupIssue.PAIRING_IDENTITY -> PairingNotificationState.PAIRING_IDENTITY_FAILED
            SetupIssue.PAIRING_TLS -> PairingNotificationState.PAIRING_TLS_FAILED
            SetupIssue.PAIRING_AUTHENTICATION -> PairingNotificationState.PAIRING_AUTHENTICATION_FAILED
            else -> null
        }
    }
}
