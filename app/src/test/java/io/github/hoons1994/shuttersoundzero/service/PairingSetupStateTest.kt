package io.github.hoons1994.shuttersoundzero.service

import io.github.hoons1994.shuttersoundzero.ui.notification.PairingNotificationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingSetupStateTest {
    @Test
    fun unreadableWirelessSettingRequestsVerificationInsteadOfEnablingOrPairing() {
        assertEquals(
            PairingNotificationState.WIRELESS_DEBUGGING_STATE_UNKNOWN,
            PairingSetupState(true, null).notificationState
        )
        assertNull(PairingSetupState(false, null).notificationState)
    }

    @Test
    fun enablingDeveloperOptionsThenWirelessDebuggingAdvancesBothSteps() {
        assertNull(PairingSetupState(false, false).notificationState)
        assertEquals(
            PairingNotificationState.DEVELOPER_OPTIONS_READY,
            PairingSetupState(true, false).notificationState
        )
        assertEquals(
            PairingNotificationState.WIRELESS_DEBUGGING_READY,
            PairingSetupState(true, true).notificationState
        )
    }

    @Test
    fun disablingAndReenablingWirelessDebuggingReturnsToTheRequiredStep() {
        val enabled = PairingSetupState(true, true)
        val disabled = enabled.copy(wirelessDebuggingEnabled = false)
        assertEquals(PairingNotificationState.DEVELOPER_OPTIONS_READY, disabled.notificationState)
        assertEquals(
            PairingNotificationState.WIRELESS_DEBUGGING_READY,
            disabled.copy(wirelessDebuggingEnabled = true).notificationState
        )
    }

    @Test
    fun developerOptionsMustBeEnabledEvenIfWirelessSettingHasNotCaughtUp() {
        assertNull(PairingSetupState(false, true).notificationState)
    }
}
