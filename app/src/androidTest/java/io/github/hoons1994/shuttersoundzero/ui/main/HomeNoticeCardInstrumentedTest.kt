package io.github.hoons1994.shuttersoundzero.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
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

    private fun checkStableHeight(fontScale: Float) {
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                MaterialTheme {
                    Box(Modifier.width(320.dp).testTag("notice-container")) {
                        HomeNoticeCard()
                    }
                }
            }
        }
        rule.onNodeWithText("무선 디버깅이 켜지지 않나요?").assertIsDisplayed()
        val initialHeight = rule.onNodeWithTag("notice-container").fetchSemanticsNode().boundsInRoot.height
        listOf(
            "업데이트 후 안내" to "업데이트 후 촬영음이 다시 나나요?",
            "촬영음 확인 안내" to "설정 후 실제 촬영음을 확인해 주세요"
        ).forEach { (label, title) ->
            rule.onNodeWithContentDescription(label).performClick()
            rule.waitForIdle()
            rule.onNodeWithText(title).assertIsDisplayed()
            assertEquals(initialHeight, rule.onNodeWithTag("notice-container").fetchSemanticsNode().boundsInRoot.height, 0.5f)
        }
        rule.onNodeWithContentDescription("다음 안내").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("무선 디버깅이 켜지지 않나요?").assertIsDisplayed()
        rule.onNodeWithContentDescription("이전 안내").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("설정 후 실제 촬영음을 확인해 주세요").assertIsDisplayed()
        rule.onNodeWithContentDescription("무선 디버깅 안내").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("무선 디버깅이 켜지지 않나요?").assertIsDisplayed()
        assertEquals(initialHeight, rule.onNodeWithTag("notice-container").fetchSemanticsNode().boundsInRoot.height, 0.5f)
    }
}
