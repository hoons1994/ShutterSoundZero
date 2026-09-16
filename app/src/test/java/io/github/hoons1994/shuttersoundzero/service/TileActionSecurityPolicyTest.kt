package io.github.hoons1994.shuttersoundzero.service

import org.junit.Assert.assertEquals
import org.junit.Test

class TileActionSecurityPolicyTest {
    @Test
    fun lockedDevice_requiresDeviceUnlockBeforeAnyOtherAction() {
        assertEquals(
            TileActionSecurityDecision.REQUEST_DEVICE_UNLOCK,
            TileActionSecurityPolicy.decide(
                isDeviceLocked = true,
                isAppLockEnabled = true
            )
        )
    }

    @Test
    fun unlockedDeviceWithAppLock_requiresOneTimeAuthentication() {
        assertEquals(
            TileActionSecurityDecision.REQUEST_APP_AUTHENTICATION,
            TileActionSecurityPolicy.decide(
                isDeviceLocked = false,
                isAppLockEnabled = true
            )
        )
    }

    @Test
    fun unlockedDeviceWithoutAppLock_executesTileAction() {
        assertEquals(
            TileActionSecurityDecision.EXECUTE,
            TileActionSecurityPolicy.decide(
                isDeviceLocked = false,
                isAppLockEnabled = false
            )
        )
    }
}
