package com.charmingcolor.shuttersoundzero.ui.settings

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

class SettingsAccessibilityInstrumentedTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun compactScreenAt200PercentFont_keepsSettingsNavigationReachable() {
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
                        SettingsScreen(onBackClick = {})
                    }
                }
            }
        }

        composeTestRule.onNodeWithContentDescription("뒤로가기")
            .assertHasClickAction()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("카메라 설정")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("도움말 및 지원")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
