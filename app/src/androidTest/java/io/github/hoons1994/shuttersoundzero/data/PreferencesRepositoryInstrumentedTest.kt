package io.github.hoons1994.shuttersoundzero.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import io.github.hoons1994.shuttersoundzero.theme.AppThemeMode

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
    fun themeMode_defaultsToSystemAndPersistsEveryChoice() {
        assertEquals(AppThemeMode.SYSTEM, PreferencesRepository(context).appThemeMode)
        AppThemeMode.entries.forEach { mode ->
            PreferencesRepository(context).appThemeMode = mode
            assertEquals(mode, PreferencesRepository(context).appThemeMode)
        }
    }

    @Test
    fun changingTheme_preservesCameraAndSetupFailurePreferences() {
        val repository = PreferencesRepository(context)
        repository.shouldMuteOnBoot = true
        repository.lastConnectPort = 43210
        repository.lastSetupIssue = SetupIssue.CAMERA_APPLY
        AppThemeMode.entries.forEach { repository.appThemeMode = it }

        val reloaded = PreferencesRepository(context)
        assertTrue(reloaded.shouldMuteOnBoot)
        assertTrue(reloaded.hasAdbLinkageHistory)
        assertEquals(43210, reloaded.lastConnectPort)
        assertEquals(SetupIssue.CAMERA_APPLY, reloaded.lastSetupIssue)
    }

    @Test
    fun unknownThemeMode_fallsBackToSystem() {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString("app_theme_mode", "unknown").commit()
        assertEquals(AppThemeMode.SYSTEM, PreferencesRepository(context).appThemeMode)
    }

    @Test
    fun themeListener_observesOtherRepositoryAndCanBeRemoved() {
        val repository = PreferencesRepository(context)
        val observed = mutableListOf<AppThemeMode>()
        lateinit var listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            listener = repository.registerThemeChangeListener { observed += it }
            PreferencesRepository(context).appThemeMode = AppThemeMode.DARK
            repository.unregisterThemeChangeListener(listener)
            PreferencesRepository(context).appThemeMode = AppThemeMode.LIGHT
        }
        assertEquals(listOf(AppThemeMode.DARK), observed)
    }

    @Test
    fun permissionRevokedFlag_persistsAcrossRepositoryInstances() {
        PreferencesRepository(context).isPermissionRevokedByUser = true

        assertTrue(PreferencesRepository(context).isPermissionRevokedByUser)
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

    @Test
    fun clearingTransientAdbState_removesPortButPreservesUserSettingsAndLinkageHistory() {
        val repository = PreferencesRepository(context)
        repository.shouldMuteOnBoot = true
        repository.isAppLockEnabled = true
        repository.lastConnectPort = 43210

        assertTrue(repository.hasAdbLinkageHistory)

        repository.clearTransientAdbConnectionState()

        val reloaded = PreferencesRepository(context)
        assertEquals(-1, reloaded.lastConnectPort)
        assertTrue(reloaded.hasAdbLinkageHistory)
        assertTrue(reloaded.shouldMuteOnBoot)
        assertTrue(reloaded.isAppLockEnabled)
    }

    @Test
    fun adbLinkageHistoryMigration_convertsLegacyPortEvidence() {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt("last_connect_port", 43210)
            .commit()

        val repository = PreferencesRepository(context)
        repository.ensureAdbLinkageHistoryMigration(hasWritePermission = false)

        assertTrue(repository.hasAdbLinkageHistory)
    }

    @Test
    fun adbLinkageHistoryMigration_doesNotRestoreExplicitlyRevokedLinkage() {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt("last_connect_port", 43210)
            .putBoolean("permission_revoked_by_user", true)
            .commit()

        val repository = PreferencesRepository(context)
        repository.ensureAdbLinkageHistoryMigration(hasWritePermission = false)

        assertFalse(repository.hasAdbLinkageHistory)
    }

    @Test
    fun explicitPermissionRevoke_clearsLinkageHistory() {
        val repository = PreferencesRepository(context)
        repository.lastConnectPort = 43210
        assertTrue(repository.hasAdbLinkageHistory)

        repository.isPermissionRevokedByUser = true

        assertFalse(repository.hasAdbLinkageHistory)
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
