package io.github.hoons1994.shuttersoundzero.ui.main

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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
        checkStableHeight(fontScale = 2f, width = 280)

    @Test
    fun swipeAndRestorationPreserveManuallySelectedNotice() {
        val restoration = StateRestorationTester(rule)
        val status = mutableStateOf(HomeStatus.SETUP_REQUIRED)
        restoration.setContent { MaterialTheme { HomeNoticeCard(status.value) } }
        rule.onNodeWithText("무선 디버깅이 켜지지 않나요?").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("업데이트 후 안내").assertIsSelected()
        rule.runOnUiThread { status.value = HomeStatus.READY }
        rule.waitForIdle()
        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("업데이트 후 안내").assertIsSelected()
    }

    @Test
    fun actionButtonsCallTheirOwnDestinationOnly() {
        var cameraCalls = 0
        var settingsCalls = 0
        rule.setContent {
            MaterialTheme {
                HomeNoticeCard(onOpenCamera = { cameraCalls++ }, onOpenCameraSettings = { settingsCalls++ })
            }
        }
        rule.onNodeWithContentDescription("업데이트 후 안내").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("카메라 설정 열기").performClick()
        rule.runOnIdle { assertEquals(1, settingsCalls); assertEquals(0, cameraCalls) }
        rule.onNodeWithContentDescription("촬영음 확인 안내").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("카메라 열기").performClick()
        rule.runOnIdle { assertEquals(1, settingsCalls); assertEquals(1, cameraCalls) }
    }

    @Test
    fun manualSelectionSurvivesHomeStatusRefresh() {
        val status = mutableStateOf(HomeStatus.STATE_UNKNOWN)
        rule.setContent { MaterialTheme { HomeNoticeCard(status.value) } }
        rule.runOnUiThread { status.value = HomeStatus.READY }
        rule.waitForIdle()
        rule.onNodeWithText("설정 후 실제 촬영음을 확인해 주세요").assertIsDisplayed()
        rule.onNodeWithContentDescription("업데이트 후 안내").performClick()
        rule.waitForIdle()
        rule.runOnUiThread { status.value = HomeStatus.SETUP_REQUIRED }
        rule.waitForIdle()
        rule.onNodeWithText("업데이트 후 촬영음이 다시 나나요?").assertIsDisplayed()
    }

    @Test
    fun reapplyRequiredStartsWithUpdateGuidanceAndPreviousWraps() {
        rule.setContent { MaterialTheme { HomeNoticeCard(HomeStatus.REAPPLY_REQUIRED) } }
        rule.onNodeWithText("업데이트 후 촬영음이 다시 나나요?").assertIsDisplayed()
        rule.onNodeWithText("1 / 3").assertIsDisplayed()
        rule.onNodeWithContentDescription("이전 안내").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("설정 후 실제 촬영음을 확인해 주세요").assertIsDisplayed()
        rule.onNodeWithText("3 / 3").assertIsDisplayed()
    }

    private fun checkStableHeight(fontScale: Float, width: Int = 320) {
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                MaterialTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Box(Modifier.width(width.dp).testTag("notice-container")) {
                            HomeNoticeCard()
                        }
                    }
                }
            }
        }
        rule.onNodeWithText("무선 디버깅이 켜지지 않나요?").assertIsDisplayed()
        val initialHeight = cardHeight()
        listOf(
            "업데이트 후 안내" to "업데이트 후 촬영음이 다시 나나요?",
            "촬영음 확인 안내" to "설정 후 실제 촬영음을 확인해 주세요"
        ).forEach { (label, _) ->
            rule.onNodeWithContentDescription(label).performScrollTo().performClick()
            rule.waitForIdle()
            rule.onNodeWithContentDescription(label).assertIsSelected()
            assertEquals(initialHeight, cardHeight(), 0.5f)
        }
        val card = rule.onNodeWithTag("notice-container").getUnclippedBoundsInRoot()
        listOf("이전 안내", "다음 안내", "무선 디버깅 안내", "업데이트 후 안내", "촬영음 확인 안내").forEach { label ->
            val bounds = rule.onNodeWithContentDescription(label).getUnclippedBoundsInRoot()
            assertTrue("Control outside card: $label", bounds.left >= card.left && bounds.right <= card.right)
        }
        rule.onNodeWithContentDescription("다음 안내").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("무선 디버깅 안내").assertIsSelected()
        rule.onNodeWithContentDescription("이전 안내").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("촬영음 확인 안내").assertIsSelected()
        rule.onNodeWithContentDescription("무선 디버깅 안내").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("무선 디버깅 안내").assertIsSelected()
        assertEquals(initialHeight, cardHeight(), 0.5f)
    }

    private fun cardHeight(): Float =
        rule.onNodeWithTag("notice-container").getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }
}
