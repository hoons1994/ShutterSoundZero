package io.github.hoons1994.shuttersoundzero.core

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hoons1994.shuttersoundzero.R
import io.github.hoons1994.shuttersoundzero.data.PreferencesRepository
import io.github.hoons1994.shuttersoundzero.data.SetupIssue
import io.github.hoons1994.shuttersoundzero.ui.main.MainScreenViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SetupSettingsNavigatorInstrumentedTest {
    private val application: Application get() = ApplicationProvider.getApplicationContext()

    @Test fun primarySettingsLaunchKeepsItsIntentAndDoesNotRetry() {
        val context = RecordingContext(application, failures = 0)
        assertTrue(SetupSettingsNavigator.openWirelessDebuggingOrDevOptions(context).isSuccess)
        assertEquals(1, context.attempts.size)
        assertEquals("com.android.settings", context.attempts.single().`package`)
        assertEquals(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, context.attempts.single().action)
        assertTrue(context.attempts.single().flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test fun fallbackSettingsLaunchDropsThePackageAndKeepsTheAction() {
        val context = RecordingContext(application, failures = 1)
        assertTrue(SetupSettingsNavigator.openWirelessDebuggingOrDevOptions(context).isSuccess)
        assertEquals(2, context.attempts.size)
        assertEquals("com.android.settings", context.attempts.first().`package`)
        assertNull(context.attempts.last().`package`)
        assertEquals(context.attempts.first().action, context.attempts.last().action)
    }

    @Test fun bothSettingsLaunchFailuresAreReported() {
        val context = RecordingContext(application, failures = Int.MAX_VALUE)
        assertTrue(SetupSettingsNavigator.openWirelessDebuggingOrDevOptions(context).isFailure)
        assertEquals(2, context.attempts.size)
    }

    @Test fun failedSettingsLaunchDoesNotStartPairingOrDiscardSavedRecoveryState() {
        verifyPairingFlow(failures = Int.MAX_VALUE, shouldStart = false)
    }

    @Test fun successfulSettingsLaunchPrecedesPairingServiceAndGuidance() {
        verifyPairingFlow(failures = 0, shouldStart = true)
    }

    private fun verifyPairingFlow(failures: Int, shouldStart: Boolean) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val prefs = PreferencesRepository.getInstance(application)
            val savedPort = prefs.lastConnectPort
            val savedIssue = prefs.lastSetupIssue
            try {
                val context = RecordingContext(application, failures)
                val viewModel = MainScreenViewModel(application)
                prefs.lastConnectPort = 43210
                prefs.lastSetupIssue = SetupIssue.PAIRING_DISCOVERY
                viewModel.startNotificationPairing(context)
                if (shouldStart) {
                    assertEquals(listOf("settings", "service"), context.events)
                    assertEquals(1, context.serviceStarts)
                    assertEquals(application.getString(R.string.main_pairing_code_entry_guidance), viewModel.uiState.value.infoMessage)
                    assertNull(viewModel.uiState.value.errorMessage)
                    assertEquals(-1, prefs.lastConnectPort)
                } else {
                    assertEquals(0, context.serviceStarts)
                    assertNull(viewModel.uiState.value.infoMessage)
                    assertEquals(application.getString(R.string.settings_launch_failed), viewModel.uiState.value.errorMessage)
                    assertEquals(43210, prefs.lastConnectPort)
                    assertEquals(SetupIssue.PAIRING_DISCOVERY, prefs.lastSetupIssue)
                }
            } finally {
                prefs.lastConnectPort = savedPort
                prefs.lastSetupIssue = savedIssue
            }
        }
    }

    private class RecordingContext(base: Context, private val failures: Int) : ContextWrapper(base) {
        val attempts = mutableListOf<Intent>()
        val events = mutableListOf<String>()
        var serviceStarts = 0
        override fun startActivity(intent: Intent) {
            attempts.add(intent)
            events.add("settings")
            if (attempts.size <= failures) throw ActivityNotFoundException("injected settings launch failure")
        }
        override fun startForegroundService(service: Intent): ComponentName? {
            serviceStarts++
            events.add("service")
            return service.component
        }
        override fun startService(service: Intent): ComponentName? = startForegroundService(service)
    }
}
