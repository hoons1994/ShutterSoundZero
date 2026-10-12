package io.github.hoons1994.shuttersoundzero.service

import io.github.hoons1994.shuttersoundzero.ui.notification.PairingNotificationState

/** Settings prerequisites remain observable until the pairing service stops. */
internal data class PairingSetupState(
    val developerOptionsEnabled: Boolean,
    val wirelessDebuggingEnabled: Boolean?
) {
    val notificationState: PairingNotificationState?
        get() = when {
            !developerOptionsEnabled -> null
            wirelessDebuggingEnabled == null -> PairingNotificationState.WIRELESS_DEBUGGING_STATE_UNKNOWN
            !wirelessDebuggingEnabled -> PairingNotificationState.DEVELOPER_OPTIONS_READY
            else -> PairingNotificationState.WIRELESS_DEBUGGING_READY
        }
}
