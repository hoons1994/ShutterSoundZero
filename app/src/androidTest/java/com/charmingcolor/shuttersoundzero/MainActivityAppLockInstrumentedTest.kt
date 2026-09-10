package com.charmingcolor.shuttersoundzero

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.charmingcolor.shuttersoundzero.data.PreferencesRepository
import com.charmingcolor.shuttersoundzero.security.AppLockSession
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityAppLockInstrumentedTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearPreferences()
        PreferencesRepository(context).apply {
            hasSeenInitialNotice = true
            isAppLockEnabled = true
        }
        AppLockSession.unlock()
    }

    @After
    fun tearDown() {
        AppLockSession.lock()
        clearPreferences()
    }

    @Test
    fun leavingForeground_locksPreviouslyUnlockedSession() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertTrue(AppLockSession.isUnlocked)

            scenario.moveToState(Lifecycle.State.CREATED)

            assertFalse(AppLockSession.isUnlocked)
        }
    }

    @Test
    fun configurationChange_preservesUnlockedSession() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertTrue(AppLockSession.isUnlocked)

            scenario.recreate()

            assertTrue(AppLockSession.isUnlocked)
        }
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
