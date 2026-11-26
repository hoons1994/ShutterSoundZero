package io.github.hoons1994.shuttersoundzero.ui.settings

import io.github.hoons1994.shuttersoundzero.testing.targetString
import io.github.hoons1994.shuttersoundzero.R
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.hoons1994.shuttersoundzero.ui.AppTheme
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
                AppTheme(darkTheme = false) {
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

        composeTestRule.onNodeWithContentDescription(targetString(R.string.action_back))
            .assertHasClickAction()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(targetString(R.string.settings_camera))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(targetString(R.string.faq_title))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(targetString(R.string.support_title))
            .performScrollTo()
            .assertIsDisplayed()
    }
}
