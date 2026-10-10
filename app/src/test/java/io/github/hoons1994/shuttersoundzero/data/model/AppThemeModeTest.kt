package io.github.hoons1994.shuttersoundzero.data.model

import io.github.hoons1994.shuttersoundzero.data.model.AppThemeMode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeModeTest {
    @Test
    fun systemMode_followsBothSystemThemes() {
        assertFalse(AppThemeMode.SYSTEM.isDark(false))
        assertTrue(AppThemeMode.SYSTEM.isDark(true))
    }

    @Test
    fun manualModes_overrideBothSystemThemes() {
        listOf(false, true).forEach { systemDark ->
            assertFalse(AppThemeMode.LIGHT.isDark(systemDark))
            assertTrue(AppThemeMode.DARK.isDark(systemDark))
        }
    }

    @Test
    fun storedModes_roundTrip() {
        AppThemeMode.entries.forEach { assertEquals(it, AppThemeMode.fromStoredValue(it.name)) }
    }

    @Test
    fun missingOrUnknownValue_defaultsToSystem() {
        listOf(null, "", "invalid").forEach {
            assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromStoredValue(it))
        }
    }
}
