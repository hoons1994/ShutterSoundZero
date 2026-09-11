package com.charmingcolor.shuttersoundzero.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
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
        composeTestRule.onNodeWithText("셔터음 제로").assertExists()
        composeTestRule.onNodeWithText("처음 한 번만 설정해 주세요").assertExists()
        composeTestRule.onNodeWithText("1회 설정 시작").assertExists()
        composeTestRule.onNodeWithText("1회 설정 진행").assertExists()
        composeTestRule.onNodeWithText("무선 디버깅 켜기").assertExists()
        composeTestRule.onNodeWithText("6자리 코드 입력").assertExists()
        composeTestRule.onNodeWithText("카메라 무음 적용").assertExists()
    }

    @Test
    fun home_doesNotShowDetailedLegalNotice() {
        composeTestRule.onNodeWithText("주의사항 및 법적 고지").assertDoesNotExist()
    }
}