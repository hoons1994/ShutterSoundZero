package io.github.hoons1994.shuttersoundzero.ui.main

import io.github.hoons1994.shuttersoundzero.data.SetupIssue
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeNoticeTest {
    @Test fun failedRestoreRemainsActionableWhileTheCameraIsMuted() {
        for (issue in listOf(SetupIssue.CAMERA_APPLY, SetupIssue.PAIRING_IDENTITY, SetupIssue.LOCAL_NETWORK_PERMISSION)) {
            assertEquals(
                issue.name,
                HomeStatus.SETUP_REQUIRED,
                resolveHomeStatus(MainUiState(
                    isCscMuted = true,
                    hasCscPermission = true,
                    setupIssue = issue,
                    pendingPairingTarget = false
                ))
            )
        }
    }

    @Test fun pendingRecoveryTakesPriorityEvenBeforeAnErrorIsRecorded() {
        val ready = MainUiState(isCscMuted = true, hasCscPermission = true)
        assertEquals(HomeStatus.READY, resolveHomeStatus(ready))
        assertEquals(HomeStatus.SETUP_REQUIRED, resolveHomeStatus(ready.copy(pendingPairingTarget = false)))
        assertEquals(HomeStatus.SETUP_REQUIRED, resolveHomeStatus(ready.copy(
            pendingPairingTarget = true,
            pendingPairingIdentityReset = true
        )))
    }

    @Test fun setupAndUnreadableStateLeadWithConnectionHelp() {
        assertEquals(HomeNotice.WIRELESS_DEBUGGING, preferredHomeNotice(HomeStatus.SETUP_REQUIRED))
        assertEquals(HomeNotice.WIRELESS_DEBUGGING, preferredHomeNotice(HomeStatus.STATE_UNKNOWN))
    }

    @Test fun confirmedReapplyStateLeadsWithReapplyHelp() {
        assertEquals(HomeNotice.REAPPLY, preferredHomeNotice(HomeStatus.REAPPLY_REQUIRED))
    }

    @Test fun appliedAndApplyingStatesLeadWithCameraVerification() {
        assertEquals(HomeNotice.CHECK_CAMERA, preferredHomeNotice(HomeStatus.READY))
        assertEquals(HomeNotice.CHECK_CAMERA, preferredHomeNotice(HomeStatus.APPLYING))
    }
}
