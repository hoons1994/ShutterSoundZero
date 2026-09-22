package io.github.hoons1994.shuttersoundzero.core.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AdbShellCommandsTest {
    @Test
    fun `grant targets the app Android user`() {
        assertEquals(
            "pm grant --user 10 io.github.hoons1994.shuttersoundzero android.permission.WRITE_SECURE_SETTINGS",
            AdbShellCommands.grantWriteSecureSettings("io.github.hoons1994.shuttersoundzero", 10)
        )
    }

    @Test
    fun `revoke targets the app Android user`() {
        assertEquals(
            "pm revoke --user 0 io.github.hoons1994.shuttersoundzero android.permission.WRITE_SECURE_SETTINGS",
            AdbShellCommands.revokeWriteSecureSettings("io.github.hoons1994.shuttersoundzero", 0)
        )
    }

    @Test
    fun `camera setting targets the app Android user`() {
        assertEquals(
            "settings --user 10 put system csc_pref_camera_forced_shuttersound_key 0",
            AdbShellCommands.setCameraMute(mute = true, userId = 10)
        )
        assertEquals(
            "settings --user 10 put system csc_pref_camera_forced_shuttersound_key 1",
            AdbShellCommands.setCameraMute(mute = false, userId = 10)
        )
    }

    @Test
    fun `invalid command inputs are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            AdbShellCommands.grantWriteSecureSettings("invalid package", 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AdbShellCommands.revokeWriteSecureSettings("io.github.example", -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AdbShellCommands.setCameraMute(mute = true, userId = -1)
        }
    }
}
