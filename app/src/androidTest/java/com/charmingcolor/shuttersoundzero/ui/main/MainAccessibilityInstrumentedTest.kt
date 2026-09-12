package com.charmingcolor.shuttersoundzero.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.charmingcolor.shuttersoundzero.theme.ShutterSoundZeroTheme
import org.junit.Rule
import org.junit.Test

class MainAccessibilityInstrumentedTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun compactScreenAt200PercentFont_keepsPrimaryFlowReachable() {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f)
            ) {
                ShutterSoundZeroTheme(darkTheme = false) {
                    Box(
                        modifier = Modifier
                            .width(320.dp)
                            .height(640.dp)
                    ) {
                        HomeContent(uiState = MainUiState())
                    }
                }
            }
        }

        composeTestRule.onNodeWithContentDescription("설정")
            .assertHasClickAction()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("1회 설정 시작")
            .performScrollTo()
            .assertHasClickAction()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("카메라 무음 적용")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun importantHomeActions_exposeTalkBackLabelsAndActions() {
        composeTestRule.setContent {
            ShutterSoundZeroTheme(darkTheme = false) {
                HomeContent(uiState = MainUiState())
            }
        }

        composeTestRule.onNodeWithContentDescription("설정")
            .assertHasClickAction()
        composeTestRule.onNodeWithText("1회 설정 시작")
            .assertHasClickAction()
    }
}
