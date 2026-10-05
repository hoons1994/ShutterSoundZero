package io.github.hoons1994.shuttersoundzero.theme

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ThemeInstrumentedTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun lightTheme_screenAndSelectionTextMeetAaContrast() = assertThemeTextContrast(false)

    @Test
    fun darkTheme_screenAndSelectionTextMeetAaContrast() = assertThemeTextContrast(true)

    private fun assertThemeTextContrast(darkTheme: Boolean) {
        val ratios = mutableListOf<Float>()
        composeTestRule.setContent {
            ShutterSoundZeroTheme(darkTheme = darkTheme) {
                val colors = MaterialTheme.colorScheme
                ratios.clear()
                listOf(
                    colors.onBackground to colors.background,
                    colors.onSurface to colors.surface,
                    colors.onSurfaceVariant to colors.surface,
                    colors.onPrimaryContainer to colors.primaryContainer,
                    colors.primary to colors.surface
                ).forEach { (foreground, background) ->
                    ratios += contrastRatio(foreground, background)
                }
            }
        }
        composeTestRule.runOnIdle {
            assertEquals(5, ratios.size)
            ratios.forEach { assertTrue("Text contrast $it is below AA", it >= MinimumAaTextContrast) }
        }
    }

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

    @Test
    fun lightTheme_statusColorsMeetAaContrast() {
        var surface = Color.Unspecified
        var green = Color.Unspecified
        var amber = Color.Unspecified

        composeTestRule.setContent {
            ShutterSoundZeroTheme(darkTheme = false) {
                surface = MaterialTheme.colorScheme.surface
                green = StatusGreen
                amber = StatusAmber
            }
        }

        composeTestRule.runOnIdle {
            assertEquals(StatusGreenLight, green)
            assertEquals(StatusAmberLight, amber)
            assertTrue(contrastRatio(green, surface) >= MinimumAaTextContrast)
            assertTrue(contrastRatio(amber, surface) >= MinimumAaTextContrast)
        }
    }

    @Test
    fun darkTheme_statusColorsMeetAaContrast() {
        var surface = Color.Unspecified
        var green = Color.Unspecified
        var amber = Color.Unspecified

        composeTestRule.setContent {
            ShutterSoundZeroTheme(darkTheme = true) {
                surface = MaterialTheme.colorScheme.surface
                green = StatusGreen
                amber = StatusAmber
            }
        }

        composeTestRule.runOnIdle {
            assertEquals(StatusGreenDark, green)
            assertEquals(StatusAmberDark, amber)
            assertTrue(contrastRatio(green, surface) >= MinimumAaTextContrast)
            assertTrue(contrastRatio(amber, surface) >= MinimumAaTextContrast)
        }
    }

    private fun contrastRatio(first: Color, second: Color): Float {
        val firstLuminance = first.luminance()
        val secondLuminance = second.luminance()
        val lighter = max(firstLuminance, secondLuminance)
        val darker = min(firstLuminance, secondLuminance)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private companion object {
        const val MinimumAaTextContrast = 4.5f
    }
}
