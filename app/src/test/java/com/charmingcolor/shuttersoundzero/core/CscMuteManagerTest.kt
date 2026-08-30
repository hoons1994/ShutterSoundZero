package com.charmingcolor.shuttersoundzero.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CscMuteManagerTest {

    @Test
    fun cscKeyConstant_isCorrect() {
        assertEquals("csc_pref_camera_forced_shuttersound_key", CscMuteManager.CSC_KEY)
    }

    @Test
    fun directAdbCommand_whenMuteTrue_setsZero() {
        val cmd = CscMuteManager.getAdbDirectCommand(true)
        assertEquals("adb shell settings put system csc_pref_camera_forced_shuttersound_key 0", cmd)
    }

    @Test
    fun directAdbCommand_whenMuteFalse_setsOne() {
        val cmd = CscMuteManager.getAdbDirectCommand(false)
        assertEquals("adb shell settings put system csc_pref_camera_forced_shuttersound_key 1", cmd)
    }

    @Test
    fun checkAdbCommand_isCorrect() {
        val cmd = CscMuteManager.getAdbCheckCommand()
        assertEquals("adb shell settings get system csc_pref_camera_forced_shuttersound_key", cmd)
    }
}
