package io.github.hoons1994.shuttersoundzero.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeNoticeTest {
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
