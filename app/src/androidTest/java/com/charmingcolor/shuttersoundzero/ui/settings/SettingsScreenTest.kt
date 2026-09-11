package com.charmingcolor.shuttersoundzero.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI smoke tests for the purpose-based settings structure. */
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setup() {
        composeTestRule.setContent {
            SettingsScreen(onBackClick = {})
        }
    }

    @Test
    fun settings_areGroupedByUserGoal() {
        composeTestRule.onNodeWithText("카메라 설정").fetchSemanticsNode()
        composeTestRule.onNodeWithText("앱 동작").fetchSemanticsNode()
        composeTestRule.onNodeWithText("업데이트").fetchSemanticsNode()
        composeTestRule.onNodeWithText("도움말 및 지원").fetchSemanticsNode()
        composeTestRule.onNodeWithText("정보").fetchSemanticsNode()
    }
}
