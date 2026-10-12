package io.github.hoons1994.shuttersoundzero.ui.main

import io.github.hoons1994.shuttersoundzero.core.adb.CameraMuteFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainScreenStateTest {
    @Test fun unreadableCscStateCannotBePresentedAsReadyOrConfirmedReset() {
        assertEquals(HomeStatus.STATE_UNKNOWN, resolveHomeStatus(MainUiState(
            hasCscPermission = true,
            isCscMuted = null
        )))
    }

    @Test fun recoveringCscReadReturnsToTheConfirmedState() {
        val unknown = MainUiState(hasCscPermission = true, isCscMuted = null)
        assertEquals(HomeStatus.READY, resolveHomeStatus(unknown.copy(isCscMuted = true)))
        assertEquals(HomeStatus.REAPPLY_REQUIRED, resolveHomeStatus(unknown.copy(isCscMuted = false)))
    }
    @Test fun enablingWirelessDebuggingDismissesEnableHelpWithoutReopeningItLater() {
        val disabled = MainUiState(showSwitchFailureHelp = true)
        assertTrue(disabled.withWirelessDebuggingState(false).showSwitchFailureHelp)
        val enabled = disabled.withWirelessDebuggingState(true)
        assertEquals(true, enabled.isWirelessDebuggingEnabled)
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
        assertEquals(false, disabled.isWirelessDebuggingEnabled)
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
        assertEquals(false, refreshed.isCscMuted)
    }

    @Test fun unreadableWirelessStateCannotKeepAnEnablePromptOrConfirmCleanup() {
        val state = MainUiState(
            showSwitchFailureHelp = true,
            showWirelessDebuggingCleanupHelp = true,
            cameraMuteFailure = CameraMuteFailure.CONNECTION
        ).withWirelessDebuggingState(null)
        assertEquals(null, state.isWirelessDebuggingEnabled)
        assertFalse(state.showSwitchFailureHelp)
        assertTrue(state.showWirelessDebuggingCleanupHelp)
        assertEquals(CameraMuteFailure.CONNECTION, state.cameraMuteFailure)
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
