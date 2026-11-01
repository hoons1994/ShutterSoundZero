package io.github.hoons1994.shuttersoundzero.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupIssueTest {
    @Test fun failedRestoreIsNotResolvedByThePreviousMutedState() {
        assertFalse(isSetupTargetApplied(hasPermission = true, mutedState = true, pendingTarget = false))
    }

    @Test fun restoreIsResolvedWhenItsExactTargetIsObserved() {
        assertTrue(isSetupTargetApplied(hasPermission = true, mutedState = false, pendingTarget = false))
    }

    @Test fun muteStillRequiresTheMutedState() {
        assertFalse(isSetupTargetApplied(true, false, true))
        assertTrue(isSetupTargetApplied(true, true, true))
    }

    @Test fun legacySetupWithoutATargetDefaultsToMute() {
        assertFalse(isSetupTargetApplied(true, false, null))
        assertTrue(isSetupTargetApplied(true, true, null))
    }

    @Test fun missingPermissionOrUnknownStateDoesNotResolveAnyTarget() {
        for (target in listOf(true, false, null)) {
            assertFalse(isSetupTargetApplied(true, null, target))
            assertFalse(isSetupTargetApplied(false, true, target))
            assertFalse(isSetupTargetApplied(false, false, target))
        }
    }
}
