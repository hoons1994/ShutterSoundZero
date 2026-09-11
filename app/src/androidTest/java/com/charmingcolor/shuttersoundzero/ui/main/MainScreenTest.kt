package com.charmingcolor.shuttersoundzero.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI smoke tests for [MainScreen]. */
class MainScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setup() {
        composeTestRule.setContent { MainScreen() }
    }

    @Test
    fun setupRequiredHome_focusesOnCurrentAction() {
        composeTestRule.onNodeWithText("셔터음 제로").fetchSemanticsNode()
        composeTestRule.onNodeWithText("처음 한 번만 설정해 주세요").fetchSemanticsNode()
        composeTestRule.onNodeWithText("1회 설정 시작").fetchSemanticsNode()
        composeTestRule.onNodeWithText("1회 설정 진행").fetchSemanticsNode()
        composeTestRule.onNodeWithText("무선 디버깅 켜기").fetchSemanticsNode()
        composeTestRule.onNodeWithText("6자리 코드 입력").fetchSemanticsNode()
        composeTestRule.onNodeWithText("카메라 무음 적용").fetchSemanticsNode()
    }

    @Test
    fun home_doesNotShowDetailedLegalNotice() {
        assertTrue(
            composeTestRule
                .onAllNodesWithText("주의사항 및 법적 고지")
                .fetchSemanticsNodes()
                .isEmpty()
        )
    }
}
