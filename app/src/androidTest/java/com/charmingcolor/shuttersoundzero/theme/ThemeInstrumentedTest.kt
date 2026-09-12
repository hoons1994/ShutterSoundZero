package com.charmingcolor.shuttersoundzero.theme

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ThemeInstrumentedTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun darkTheme_usesDarkPalette() {
        var background = Color.Unspecified
        var surface = Color.Unspecified
        var onBackground = Color.Unspecified

        composeTestRule.setContent {
            ShutterSoundZeroTheme(darkTheme = true) {
                background = MaterialTheme.colorScheme.background
                surface = MaterialTheme.colorScheme.surface
                onBackground = MaterialTheme.colorScheme.onBackground
            }
        }

        composeTestRule.runOnIdle {
            assertEquals(ScreenBgDark, background)
            assertEquals(SurfaceDark, surface)
            assertEquals(TextPrimaryDark, onBackground)
        }
    }

    @Test
    fun lightTheme_usesLightPalette() {
        var background = Color.Unspecified
        var surface = Color.Unspecified
        var onBackground = Color.Unspecified

        composeTestRule.setContent {
            ShutterSoundZeroTheme(darkTheme = false) {
                background = MaterialTheme.colorScheme.background
                surface = MaterialTheme.colorScheme.surface
                onBackground = MaterialTheme.colorScheme.onBackground
            }
        }

        composeTestRule.runOnIdle {
            assertEquals(ScreenBgLight, background)
            assertEquals(SurfaceLight, surface)
            assertEquals(TextPrimaryLight, onBackground)
        }
    }
}
