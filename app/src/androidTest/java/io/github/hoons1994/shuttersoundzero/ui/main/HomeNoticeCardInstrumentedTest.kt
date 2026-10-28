package io.github.hoons1994.shuttersoundzero.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
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
        rule.onNodeWithContentDescription("다음 안내").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("설정 후 촬영음을 확인해 주세요").assertIsDisplayed()
        assertEquals(initialHeight, rule.onNodeWithTag("notice-container").fetchSemanticsNode().boundsInRoot.height, 0.5f)
        rule.onNodeWithContentDescription("이전 안내").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("무선 디버깅이 켜지지 않나요?").assertIsDisplayed()
        assertEquals(initialHeight, rule.onNodeWithTag("notice-container").fetchSemanticsNode().boundsInRoot.height, 0.5f)
    }
}
