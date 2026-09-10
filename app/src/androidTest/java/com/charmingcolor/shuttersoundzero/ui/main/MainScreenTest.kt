package com.charmingcolor.shuttersoundzero.ui.main

import androidx.activity.ComponentActivity
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
    fun primarySections_arePresent() {
        composeTestRule.onNodeWithText("셔터음 제로").assertExists()
        composeTestRule.onNodeWithText("현재 상태").assertExists()
        composeTestRule.onNodeWithText("초기 설정 및 복구").assertExists()
        composeTestRule.onNodeWithText("주의사항 및 법적 고지").assertExists()
    }
}
