package io.github.hoons1994.shuttersoundzero.ui.main

import io.github.hoons1994.shuttersoundzero.core.adb.CameraMuteFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainScreenStateTest {
    @Test fun enablingWirelessDebuggingDismissesEnableHelpWithoutReopeningItLater() {
        val disabled = MainUiState(showSwitchFailureHelp = true)
        assertTrue(disabled.withWirelessDebuggingState(false).showSwitchFailureHelp)
        val enabled = disabled.withWirelessDebuggingState(true)
        assertTrue(enabled.isWirelessDebuggingEnabled)
        assertFalse(enabled.showSwitchFailureHelp)
        assertFalse(enabled.withWirelessDebuggingState(false).showSwitchFailureHelp)
    }

    @Test fun manualWirelessCleanupDismissesCleanupHelpWithoutReopeningItLater() {
        val enabled = MainUiState(
            isWirelessDebuggingEnabled = true,
            showWirelessDebuggingCleanupHelp = true
        )
        assertTrue(enabled.withWirelessDebuggingState(true).showWirelessDebuggingCleanupHelp)
        val disabled = enabled.withWirelessDebuggingState(false)
        assertFalse(disabled.isWirelessDebuggingEnabled)
        assertFalse(disabled.showWirelessDebuggingCleanupHelp)
        assertFalse(disabled.withWirelessDebuggingState(true).showWirelessDebuggingCleanupHelp)
    }

    @Test fun wirelessSettingsChangesPreserveActualAdbFailureAndCameraState() {
        val failure = MainUiState(
            isCscMuted = false,
            hasCscPermission = true,
            cameraMuteFailure = CameraMuteFailure.CONNECTION
        )
        val refreshed = failure.withWirelessDebuggingState(true).withWirelessDebuggingState(false)
        assertEquals(CameraMuteFailure.CONNECTION, refreshed.cameraMuteFailure)
        assertTrue(refreshed.hasCscPermission)
        assertFalse(refreshed.isCscMuted)
    }

    @Test fun pendingMuteCannotBePresentedAsReady() {
        assertEquals(HomeStatus.APPLYING, resolveHomeStatus(MainUiState(
            hasCscPermission = true,
            isCscMuted = false,
            isCscChangeInProgress = true
        )))
    }

    @Test fun pendingRestoreHidesPreviouslyConfirmedReadyState() {
        assertEquals(HomeStatus.APPLYING, resolveHomeStatus(MainUiState(
            hasCscPermission = true,
            isCscMuted = true,
            isCscChangeInProgress = true
        )))
    }

    @Test fun failedMuteReturnsToReapplyState() {
        assertEquals(HomeStatus.REAPPLY_REQUIRED, resolveHomeStatus(MainUiState(
            hasCscPermission = true,
            isCscMuted = false
        )))
    }

    @Test fun confirmedMuteCanBePresentedAsReady() {
        assertEquals(HomeStatus.READY, resolveHomeStatus(MainUiState(
            hasCscPermission = true,
            isCscMuted = true
        )))
    }
}
