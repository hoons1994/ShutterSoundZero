package io.github.hoons1994.shuttersoundzero.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test

class MainScreenStateTest {
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
