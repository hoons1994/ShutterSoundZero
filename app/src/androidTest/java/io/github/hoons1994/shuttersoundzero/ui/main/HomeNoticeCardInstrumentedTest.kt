package io.github.hoons1994.shuttersoundzero.ui.main

import io.github.hoons1994.shuttersoundzero.testing.targetString
import io.github.hoons1994.shuttersoundzero.R
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.hoons1994.shuttersoundzero.theme.ShutterSoundZeroTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeNoticeCardInstrumentedTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun switchingNoticesKeepsCardHeight() = checkStableHeight(fontScale = 1f)

    @Test
    fun switchingNoticesKeepsCardHeightWithLargeText() = checkStableHeight(fontScale = 1.5f)

    @Test
    fun narrowCardKeepsControlsInsideAndHeightStableAtDoubleFontSize() =
        checkStableHeight(fontScale = 2f, width = 280, darkTheme = true)

    @Test
    fun swipeAndRestorationPreserveManuallySelectedNotice() {
        val restoration = StateRestorationTester(rule)
        val status = mutableStateOf(HomeStatus.SETUP_REQUIRED)
        restoration.setContent { MaterialTheme { HomeNoticeCard(status.value) } }
        rule.onNodeWithText(targetString(R.string.home_notice_wireless_title)).performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithContentDescription(targetString(R.string.home_notice_reapply_label)).assertIsSelected()
        rule.runOnUiThread { status.value = HomeStatus.READY }
        rule.waitForIdle()
        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        rule.onNodeWithContentDescription(targetString(R.string.home_notice_reapply_label)).assertIsSelected()
    }

    @Test
    fun noticeOffersSettingsActionWithoutDuplicatingCameraAction() {
        var settingsCalls = 0
        rule.setContent {
            MaterialTheme {
                HomeNoticeCard(onOpenCameraSettings = { settingsCalls++ })
            }
        }
        rule.onNodeWithContentDescription(targetString(R.string.home_notice_reapply_label)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(targetString(R.string.home_notice_open_camera_settings)).performClick()
        rule.runOnIdle { assertEquals(1, settingsCalls) }
        rule.onNodeWithContentDescription(targetString(R.string.home_notice_camera_label)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("카메라 열기").assertDoesNotExist()
        rule.runOnIdle { assertEquals(1, settingsCalls) }
    }

    @Test
    fun readyHomeOffersOnlyTheHeroCameraAction() {
        var cameraCalls = 0
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                ShutterSoundZeroTheme(darkTheme = true) {
                    Box(Modifier.width(280.dp)) {
                        HomeContent(
                            uiState = MainUiState(isCscMuted = true, hasCscPermission = true),
                            onOpenCamera = { cameraCalls++ }
                        )
                    }
                }
            }
        }
        rule.onNodeWithText("카메라 열기").assertDoesNotExist()
        rule.onNodeWithText(targetString(R.string.home_open_camera)).performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1, cameraCalls) }
    }

    @Test
    fun manualSelectionSurvivesHomeStatusRefresh() {
        val status = mutableStateOf(HomeStatus.STATE_UNKNOWN)
        rule.setContent { MaterialTheme { HomeNoticeCard(status.value) } }
        rule.runOnUiThread { status.value = HomeStatus.READY }
        rule.waitForIdle()
        rule.onNodeWithText(targetString(R.string.home_notice_camera_title)).assertIsDisplayed()
        rule.onNodeWithContentDescription(targetString(R.string.home_notice_reapply_label)).performClick()
        rule.waitForIdle()
        rule.runOnUiThread { status.value = HomeStatus.SETUP_REQUIRED }
        rule.waitForIdle()
        rule.onNodeWithText(targetString(R.string.home_notice_reapply_title)).assertIsDisplayed()
    }

    @Test
    fun reapplyRequiredStartsWithUpdateGuidanceAndPreviousWraps() {
        rule.setContent { MaterialTheme { HomeNoticeCard(HomeStatus.REAPPLY_REQUIRED) } }
        rule.onNodeWithText(targetString(R.string.home_notice_reapply_title)).assertIsDisplayed()
        rule.onNodeWithText("1 / 3").assertIsDisplayed()
        rule.onNodeWithContentDescription(targetString(R.string.home_notice_previous)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(targetString(R.string.home_notice_camera_title)).assertIsDisplayed()
        rule.onNodeWithText("3 / 3").assertIsDisplayed()
    }

    private fun checkStableHeight(fontScale: Float, width: Int = 320, darkTheme: Boolean = false) {
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                ShutterSoundZeroTheme(darkTheme = darkTheme) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Box(Modifier.width(width.dp).testTag("notice-container")) {
                            HomeNoticeCard()
                        }
                    }
                }
            }
        }
        rule.onNodeWithText(targetString(R.string.home_notice_wireless_title)).assertIsDisplayed()
        val initialHeight = cardHeight()
        listOf(
            targetString(R.string.home_notice_reapply_label) to targetString(R.string.home_notice_reapply_title),
            targetString(R.string.home_notice_camera_label) to targetString(R.string.home_notice_camera_title)
        ).forEach { (label, _) ->
            rule.onNodeWithContentDescription(label).performScrollTo().performClick()
            rule.waitForIdle()
            rule.onNodeWithContentDescription(label).assertIsSelected()
            assertEquals(initialHeight, cardHeight(), 0.5f)
        }
        val card = rule.onNodeWithTag("notice-container").getUnclippedBoundsInRoot()
        listOf(targetString(R.string.home_notice_previous), targetString(R.string.home_notice_next), targetString(R.string.home_notice_wireless_label), targetString(R.string.home_notice_reapply_label), targetString(R.string.home_notice_camera_label)).forEach { label ->
            val bounds = rule.onNodeWithContentDescription(label).getUnclippedBoundsInRoot()
            assertTrue("Control outside card: $label", bounds.left >= card.left && bounds.right <= card.right)
        }
        rule.onNodeWithContentDescription(targetString(R.string.home_notice_next)).performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription(targetString(R.string.home_notice_wireless_label)).assertIsSelected()
        rule.onNodeWithContentDescription(targetString(R.string.home_notice_previous)).performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription(targetString(R.string.home_notice_camera_label)).assertIsSelected()
        rule.onNodeWithContentDescription(targetString(R.string.home_notice_wireless_label)).performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription(targetString(R.string.home_notice_wireless_label)).assertIsSelected()
        assertEquals(initialHeight, cardHeight(), 0.5f)
    }

    private fun cardHeight(): Float =
        rule.onNodeWithTag("notice-container").getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }
}
