package com.charmingcolor.shuttersoundzero.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferencesRepositoryInstrumentedTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearPreferences()
    }

    @After
    fun tearDown() {
        clearPreferences()
    }

    @Test
    fun permissionRevokedFlag_persistsAcrossRepositoryInstances() {
        PreferencesRepository(context).isPermissionRevokedByUser = true

        assertTrue(PreferencesRepository(context).isPermissionRevokedByUser)
    }

    @Test
    fun clearingKnownUpdateVersion_doesNotResetOtherState() {
        val repository = PreferencesRepository(context)
        repository.shouldMuteOnBoot = true
        repository.knownAvailableAppUpdateVersion = "9.9.9"

        repository.knownAvailableAppUpdateVersion = null

        assertNull(PreferencesRepository(context).knownAvailableAppUpdateVersion)
        assertTrue(PreferencesRepository(context).shouldMuteOnBoot)
    }

    @Test
    fun initialNoticeMigration_distinguishesFreshAndExistingInstall() {
        val freshRepository = PreferencesRepository(context)
        freshRepository.ensureInitialNoticeMigration()
        assertFalse(freshRepository.hasSeenInitialNotice)

        clearPreferences()
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("should_mute_on_boot", true)
            .commit()

        val existingRepository = PreferencesRepository(context)
        existingRepository.ensureInitialNoticeMigration()
        assertTrue(existingRepository.hasSeenInitialNotice)
    }

    private fun clearPreferences() {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private companion object {
        const val PREF_NAME = "galaxy_camera_mute_prefs"
    }
}
