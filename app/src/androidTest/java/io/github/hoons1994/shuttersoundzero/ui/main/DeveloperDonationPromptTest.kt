package io.github.hoons1994.shuttersoundzero.ui.main

import io.github.hoons1994.shuttersoundzero.testing.targetString
import io.github.hoons1994.shuttersoundzero.R
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hoons1994.shuttersoundzero.data.PreferencesRepository
import io.github.hoons1994.shuttersoundzero.security.AppLockSession
import io.github.hoons1994.shuttersoundzero.ui.AppTheme
import io.github.hoons1994.shuttersoundzero.ui.components.DeveloperDonationPromptDialog
import io.github.hoons1994.shuttersoundzero.ui.components.DeveloperDonationPromptHost
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Uses an isolated preferences file; never opens a payment application. */
class DeveloperDonationPromptTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var context: Context
    private lateinit var prefs: PreferencesRepository
    private var originallyUnlocked = false
    private var prepared = false

    @Before fun prepare() {
        originallyUnlocked = AppLockSession.isUnlocked
        val application: Context = ApplicationProvider.getApplicationContext()
        context = object : ContextWrapper(application) {
            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences("donation_prompt_test", mode)
        }
        prepared = true
        context.getSharedPreferences("", Context.MODE_PRIVATE).edit().clear().commit()
        prefs = PreferencesRepository(context)
        prefs.hasSeenInitialNotice = true
    }

    @After fun cleanUp() {
        if (!prepared) return
        context.getSharedPreferences("", Context.MODE_PRIVATE).edit().clear().commit()
        if (originallyUnlocked) AppLockSession.unlock() else AppLockSession.lock()
    }

    @Test fun dialogShowsAgreedWordingAndOpensDonationOnlyOnExplicitClick() {
        var donated = false
        rule.setContent { AppTheme { DeveloperDonationPromptDialog(onDismiss = {}, onDonate = { donated = true }) } }
        rule.onNodeWithText(targetString(R.string.donation_prompt_title)).assertExists()
        rule.onNodeWithText(targetString(R.string.donation_prompt_message)).assertExists()
        assertFalse(donated)
        val screenshot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            java.io.File(rule.activity.getExternalFilesDir(null), "donation-prompt.png").outputStream().use {
                assertTrue(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { screenshot.recycle() }
        rule.onNodeWithText(targetString(R.string.donation_prompt_action)).performClick()
        assertTrue(donated)
    }

    @Test fun invitationWaitsForEligibleScreenAndNeverRepeatsAfterClosing() {
        var enabled by mutableStateOf(false)
        prefs.recordCameraMuteSuccess(true)
        rule.setContent { DeveloperDonationPromptHost(prefs, enabled) }
        rule.onNodeWithText(targetString(R.string.donation_prompt_action)).assertDoesNotExist()
        rule.runOnIdle { enabled = true }
        rule.onNodeWithText(targetString(R.string.camera_routine_close)).performClick()
        rule.runOnIdle {
            prefs.recordCameraMuteSuccess(true)
            enabled = false
        }
        rule.runOnIdle { enabled = true }
        rule.onNodeWithText(targetString(R.string.donation_prompt_action)).assertDoesNotExist()
        assertFalse(PreferencesRepository(context).claimDonationPrompt())
    }

    @Test fun invitationWaitsForInitialNoticeAndSurvivesStateRestorationUntilDismissed() {
        prefs.hasSeenInitialNotice = false
        prefs.recordCameraMuteSuccess(true)
        val restoration = StateRestorationTester(rule)
        restoration.setContent { DeveloperDonationPromptHost(prefs, enabled = true) }
        rule.onNodeWithText(targetString(R.string.donation_prompt_action)).assertDoesNotExist()
        rule.runOnIdle { prefs.hasSeenInitialNotice = true }
        rule.onNodeWithText(targetString(R.string.donation_prompt_action)).assertExists()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText(targetString(R.string.camera_routine_close)).performClick()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText(targetString(R.string.donation_prompt_action)).assertDoesNotExist()
    }

    @Test fun successfulRestoreCannotDisplayMuteInvitation() {
        prefs.recordCameraMuteSuccess(true)
        prefs.recordCameraMuteSuccess(false)
        rule.setContent { DeveloperDonationPromptHost(prefs, enabled = true) }
        rule.onNodeWithText(targetString(R.string.donation_prompt_action)).assertDoesNotExist()
    }

    @Test fun resumedScreenCannotConsumeInvitationBeforeAppUnlock() {
        var enabled by mutableStateOf(true)
        prefs.isAppLockEnabled = true
        prefs.recordCameraMuteSuccess(true)
        AppLockSession.lock()
        rule.setContent { DeveloperDonationPromptHost(prefs, enabled) }
        rule.onNodeWithText(targetString(R.string.donation_prompt_action)).assertDoesNotExist()
        rule.runOnIdle {
            AppLockSession.unlock()
            enabled = false
        }
        rule.runOnIdle { enabled = true }
        rule.onNodeWithText(targetString(R.string.donation_prompt_action)).assertExists()
    }
}
