package io.github.hoons1994.shuttersoundzero.ui.main

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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

    @Before fun prepare() {
        originallyUnlocked = AppLockSession.isUnlocked
        val application: Context = ApplicationProvider.getApplicationContext()
        context = object : ContextWrapper(application) {
            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences("donation_prompt_test", mode)
        }
        context.getSharedPreferences("", Context.MODE_PRIVATE).edit().clear().commit()
        prefs = PreferencesRepository(context)
        prefs.hasSeenInitialNotice = true
    }

    @After fun cleanUp() {
        context.getSharedPreferences("", Context.MODE_PRIVATE).edit().clear().commit()
        if (originallyUnlocked) AppLockSession.unlock() else AppLockSession.lock()
    }

    @Test fun dialogShowsAgreedWordingAndOpensDonationOnlyOnExplicitClick() {
        var donated = false
        rule.setContent { AppTheme { DeveloperDonationPromptDialog(onDismiss = {}, onDonate = { donated = true }) } }
        rule.onNodeWithText("셔터음 제거가\n완료되었습니다.").assertExists()
        rule.onNodeWithText("셔터사운드제로가 도움이 되셨다면, 후원으로 개발을 응원하실 수 있습니다.\n\n보내주시는 응원은 앱을 꾸준히 개선하는 데 큰 힘이 됩니다.").assertExists()
        assertFalse(donated)
        val screenshot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            java.io.File(rule.activity.getExternalFilesDir(null), "donation-prompt.png").outputStream().use {
                assertTrue(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { screenshot.recycle() }
        rule.onNodeWithText("개발자 후원하기").performClick()
        assertTrue(donated)
    }

    @Test fun invitationWaitsForEligibleScreenAndNeverRepeatsAfterClosing() {
        var enabled by mutableStateOf(false)
        prefs.recordCameraMuteSuccess(true)
        rule.setContent { DeveloperDonationPromptHost(prefs, enabled) }
        rule.onNodeWithText("개발자 후원하기").assertDoesNotExist()
        rule.runOnIdle { enabled = true }
        rule.onNodeWithText("닫기").performClick()
        rule.runOnIdle {
            prefs.recordCameraMuteSuccess(true)
            enabled = false
        }
        rule.runOnIdle { enabled = true }
        rule.onNodeWithText("개발자 후원하기").assertDoesNotExist()
        assertFalse(PreferencesRepository(context).claimDonationPrompt())
    }

    @Test fun invitationWaitsForInitialNoticeAndSurvivesStateRestorationUntilDismissed() {
        prefs.hasSeenInitialNotice = false
        prefs.recordCameraMuteSuccess(true)
        val restoration = StateRestorationTester(rule)
        restoration.setContent { DeveloperDonationPromptHost(prefs, enabled = true) }
        rule.onNodeWithText("개발자 후원하기").assertDoesNotExist()
        rule.runOnIdle { prefs.hasSeenInitialNotice = true }
        rule.onNodeWithText("개발자 후원하기").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("닫기").performClick()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("개발자 후원하기").assertDoesNotExist()
    }

    @Test fun successfulRestoreCannotDisplayMuteInvitation() {
        prefs.recordCameraMuteSuccess(true)
        prefs.recordCameraMuteSuccess(false)
        rule.setContent { DeveloperDonationPromptHost(prefs, enabled = true) }
        rule.onNodeWithText("개발자 후원하기").assertDoesNotExist()
    }

    @Test fun resumedScreenCannotConsumeInvitationBeforeAppUnlock() {
        var enabled by mutableStateOf(true)
        prefs.isAppLockEnabled = true
        prefs.recordCameraMuteSuccess(true)
        AppLockSession.lock()
        rule.setContent { DeveloperDonationPromptHost(prefs, enabled) }
        rule.onNodeWithText("개발자 후원하기").assertDoesNotExist()
        rule.runOnIdle {
            AppLockSession.unlock()
            enabled = false
        }
        rule.runOnIdle { enabled = true }
        rule.onNodeWithText("개발자 후원하기").assertExists()
    }
}
