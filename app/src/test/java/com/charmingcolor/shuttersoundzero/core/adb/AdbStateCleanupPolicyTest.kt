package com.charmingcolor.shuttersoundzero.core.adb

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbStateCleanupPolicyTest {

    @Test
    fun resetsIdentity_whenPriorLinkageExistsAndPermissionDisappeared() {
        assertTrue(
            AdbStateCleanupPolicy.shouldResetIdentityAfterUnexpectedPermissionLoss(
                hadLinkageEvidence = true,
                permissionRevokedByUser = false,
                hasWritePermission = false
            )
        )
    }

    @Test
    fun preservesIdentity_whenPermissionStillExists() {
        assertFalse(
            AdbStateCleanupPolicy.shouldResetIdentityAfterUnexpectedPermissionLoss(
                hadLinkageEvidence = true,
                permissionRevokedByUser = false,
                hasWritePermission = true
            )
        )
    }

    @Test
    fun preservesIdentity_whenUserExplicitlyRevokedLinkage() {
        assertFalse(
            AdbStateCleanupPolicy.shouldResetIdentityAfterUnexpectedPermissionLoss(
                hadLinkageEvidence = true,
                permissionRevokedByUser = true,
                hasWritePermission = false
            )
        )
    }

    @Test
    fun preservesIdentity_whenNoPriorLinkageEvidenceExists() {
        assertFalse(
            AdbStateCleanupPolicy.shouldResetIdentityAfterUnexpectedPermissionLoss(
                hadLinkageEvidence = false,
                permissionRevokedByUser = false,
                hasWritePermission = false
            )
        )
    }
}
