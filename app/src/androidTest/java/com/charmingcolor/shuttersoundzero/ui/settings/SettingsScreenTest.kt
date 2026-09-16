package com.charmingcolor.shuttersoundzero.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

    @Test
    fun detailedPopupSetting_isAvailableFromHelpSection() {
        composeTestRule.onNodeWithText("알림 팝업 자세히 보기")
            .assertHasClickAction()
    }

    @Test
    fun compatibilityReport_requiresExplicitConfirmation() {
        composeTestRule.onNodeWithText("호환성 데이터 제출")
            .performScrollTo()
            .assertHasClickAction()
            .performClick()

        composeTestRule.onNodeWithText("호환성 데이터를 제출하시겠습니까?")
            .fetchSemanticsNode()
        composeTestRule.onNodeWithText("제출될 정보")
            .fetchSemanticsNode()
        composeTestRule.onNodeWithText("제출되지 않는 정보")
            .fetchSemanticsNode()
        composeTestRule.onNodeWithText("예")
            .assertHasClickAction()
        composeTestRule.onNodeWithText("아니오")
            .assertHasClickAction()
    }
}
