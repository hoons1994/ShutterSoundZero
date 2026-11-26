package io.github.hoons1994.shuttersoundzero.ui.settings

import io.github.hoons1994.shuttersoundzero.testing.targetString
import io.github.hoons1994.shuttersoundzero.R
import androidx.activity.ComponentActivity
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.hoons1994.shuttersoundzero.camera.ChangeCameraMute
import java.util.Locale
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test

/** UI smoke tests for the purpose-based settings structure. */
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun showSettings() {
        composeTestRule.setContent {
            SettingsScreen(onBackClick = {})
        }
    }

    @Test
    fun settings_areGroupedByUserGoal() {
        showSettings()
        composeTestRule.onNodeWithText(targetString(R.string.settings_camera)).fetchSemanticsNode()
        composeTestRule.onNodeWithText(targetString(R.string.settings_security)).fetchSemanticsNode()
        composeTestRule.onNodeWithText(targetString(R.string.settings_status_updates)).fetchSemanticsNode()
        composeTestRule.onNodeWithText(targetString(R.string.settings_device)).fetchSemanticsNode()
        composeTestRule.onNodeWithText(targetString(R.string.faq_title)).fetchSemanticsNode()
        composeTestRule.onNodeWithText(targetString(R.string.support_title)).fetchSemanticsNode()
        composeTestRule.onNodeWithText(targetString(R.string.settings_about)).fetchSemanticsNode()
    }

    @Test
    fun detailedPopupSetting_isAvailableFromDeviceSettings() {
        showSettings()
        composeTestRule.onNodeWithText(targetString(R.string.support_popup_title))
            .assertHasClickAction()
    }

    @Test
    fun developerLinks_areAvailableFromHelpSection() {
        showSettings()
        composeTestRule.onNodeWithText(targetString(R.string.support_contact))
            .performScrollTo()
            .assertHasClickAction()
        composeTestRule.onNodeWithText(targetString(R.string.support_contact_chat))
            .fetchSemanticsNode()
        composeTestRule.onNodeWithText(targetString(R.string.support_donate))
            .performScrollTo()
            .assertHasClickAction()
        composeTestRule.onNodeWithText(targetString(R.string.support_donate_body))
            .fetchSemanticsNode()
    }

    @Test
    fun deviceReportEntry_isNotAvailable() {
        showSettings()
        composeTestRule.onNodeWithText("호환성 데이터 제출")
            .assertDoesNotExist()
        composeTestRule.onNodeWithText("호환성 데이터를 제출하시겠습니까?")
            .assertDoesNotExist()
    }

    @Test
    fun errorReporting_isStillAvailable() {
        showSettings()
        composeTestRule.onNodeWithText(targetString(R.string.support_unresolved))
            .performScrollTo()
            .assertHasClickAction()
            .performClick()

        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithText(targetString(R.string.report_preview)).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(targetString(R.string.report_error)).assertExists()
        composeTestRule.onNodeWithText(targetString(R.string.report_preview)).assertExists()
        composeTestRule.onNodeWithText(targetString(R.string.report_send)).assertHasClickAction()
        composeTestRule.onNodeWithText(targetString(R.string.camera_routine_close)).performClick()
        composeTestRule.onNodeWithText(targetString(R.string.report_error)).assertDoesNotExist()
    }

    @Test fun restoredResultUsesTheCurrentLanguageAfterRecreation() {
        val activity = composeTestRule.activity
        val factory = viewModelFactory {
            initializer {
                CameraSettingsOperationViewModel(
                    activity.application,
                    ChangeCameraMute({ Result.success(Unit) }, { true }, { Result.success(Unit) }),
                    SavedStateHandle()
                )
            }
        }
        composeTestRule.runOnUiThread {
            ViewModelProvider(activity, factory)[CameraSettingsOperationViewModel::class.java].setCameraMute(false)
        }
        fun strings(locale: Locale) = activity.createConfigurationContext(
            Configuration().apply { setLocale(locale) }
        ).resources
        val korean = strings(Locale.KOREAN)
        val japanese = strings(Locale.JAPANESE)
        val resources = mutableStateOf(korean)
        val restoration = StateRestorationTester(composeTestRule)
        restoration.setContent {
            CompositionLocalProvider(LocalResources provides resources.value) {
                SettingsScreen(onBackClick = {})
            }
        }
        composeTestRule.onNodeWithText(korean.getString(R.string.change_restored)).assertExists()
        composeTestRule.runOnIdle { resources.value = japanese }
        restoration.emulateSavedInstanceStateRestore()
        composeTestRule.onNodeWithText(japanese.getString(R.string.change_restored)).assertExists()
        composeTestRule.onNodeWithText(korean.getString(R.string.change_restored)).assertDoesNotExist()
    }
}
