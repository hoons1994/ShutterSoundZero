package com.charmingcolor.shuttersoundzero.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferencesRepositoryFirstRunMigrationTest {
    @Test
    fun freshInstall_hasNoExistingPreferenceEvidence() {
        assertFalse(isExistingInstall(emptyMap()))
    }

    @Test
    fun previousInstall_withAnyStoredPreference_isDetected() {
        assertTrue(isExistingInstall(mapOf("last_firmware_fingerprint" to "build")))
        assertTrue(isExistingInstall(mapOf("firmware_update_check" to false)))
    }

    private fun isExistingInstall(values: Map<String, Any?>): Boolean = values.isNotEmpty()
}
