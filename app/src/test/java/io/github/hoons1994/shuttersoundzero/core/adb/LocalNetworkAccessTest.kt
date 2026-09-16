package io.github.hoons1994.shuttersoundzero.core.adb

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkAccessTest {

    @Test
    fun runtimePermission_isRequiredOnlyFromAndroid17() {
        assertFalse(LocalNetworkAccess.requiresRuntimePermission(36))
        assertTrue(LocalNetworkAccess.requiresRuntimePermission(37))
        assertTrue(LocalNetworkAccess.requiresRuntimePermission(38))
    }

    @Test
    fun existingLinkage_requestsPermissionAfterAndroid17Upgrade() {
        assertTrue(
            LocalNetworkAccess.shouldRequestForExistingLinkage(
                sdkInt = 37,
                linkageExpected = true,
                permissionRevokedByUser = false,
                permissionGranted = false
            )
        )
    }

    @Test
    fun existingLinkage_doesNotPromptWhenNotNeeded() {
        assertFalse(
            LocalNetworkAccess.shouldRequestForExistingLinkage(
                sdkInt = 36,
                linkageExpected = true,
                permissionRevokedByUser = false,
                permissionGranted = false
            )
        )
        assertFalse(
            LocalNetworkAccess.shouldRequestForExistingLinkage(
                sdkInt = 37,
                linkageExpected = false,
                permissionRevokedByUser = false,
                permissionGranted = false
            )
        )
        assertFalse(
            LocalNetworkAccess.shouldRequestForExistingLinkage(
                sdkInt = 37,
                linkageExpected = true,
                permissionRevokedByUser = true,
                permissionGranted = false
            )
        )
        assertFalse(
            LocalNetworkAccess.shouldRequestForExistingLinkage(
                sdkInt = 37,
                linkageExpected = true,
                permissionRevokedByUser = false,
                permissionGranted = true
            )
        )
    }

    @Test
    fun permissionRequiredException_hasActionableMessage() {
        assertTrue(
            LocalNetworkPermissionRequiredException().message.orEmpty().contains("로컬 네트워크 권한")
        )
    }
}
