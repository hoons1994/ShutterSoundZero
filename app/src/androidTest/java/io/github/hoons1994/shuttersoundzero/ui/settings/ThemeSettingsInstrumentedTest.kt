package io.github.hoons1994.shuttersoundzero.ui.settings

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.hoons1994.shuttersoundzero.data.PreferencesRepository
import io.github.hoons1994.shuttersoundzero.data.model.AppThemeMode
import io.github.hoons1994.shuttersoundzero.theme.ScreenBgDark
import io.github.hoons1994.shuttersoundzero.theme.ScreenBgLight
import io.github.hoons1994.shuttersoundzero.ui.AppTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ThemeSettingsInstrumentedTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var prefs: PreferencesRepository
    private lateinit var previousMode: AppThemeMode

    @Before
    fun setUp() {
        prefs = PreferencesRepository.getInstance(rule.activity)
        previousMode = prefs.appThemeMode
        rule.runOnUiThread { prefs.appThemeMode = AppThemeMode.SYSTEM }
    }

    @After
    fun tearDown() {
        rule.runOnUiThread { prefs.appThemeMode = previousMode }
    }

    @Test
    fun settingsSelection_updatesOuterAppThemeImmediatelyAndRestoresSavedChoice() {
        var background = Color.Unspecified
        rule.setContent {
            AppTheme {
                background = MaterialTheme.colorScheme.background
                SettingsScreen(onBackClick = {})
            }
        }
        rule.onNodeWithText("시스템 기본값").performScrollTo().assertIsSelected()
        rule.onNodeWithText("다크 모드").performScrollTo().performClick().assertIsSelected()
        rule.onNodeWithText("라이트 모드").assertIsNotSelected()
        rule.runOnIdle {
            assertEquals(ScreenBgDark, background)
            assertEquals(AppThemeMode.DARK, PreferencesRepository(rule.activity).appThemeMode)
        }
        rule.onNodeWithText("라이트 모드").performClick().assertIsSelected()
        rule.runOnIdle { assertEquals(ScreenBgLight, background) }
    }

    @Test
    fun savedManualChoice_isAppliedWhenAppCompositionIsRecreated() {
        rule.runOnUiThread { prefs.appThemeMode = AppThemeMode.DARK }
        val generation = mutableStateOf(0)
        val lightConfiguration = Configuration(rule.activity.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
        }
        var background = Color.Unspecified
        rule.setContent {
            CompositionLocalProvider(LocalConfiguration provides lightConfiguration) {
                key(generation.value) {
                    AppTheme {
                        background = MaterialTheme.colorScheme.background
                        ThemeSettingsSection(PreferencesRepository(rule.activity))
                    }
                }
            }
        }
        rule.onNodeWithText("다크 모드").assertIsSelected()
        rule.runOnIdle {
            assertEquals(ScreenBgDark, background)
            generation.value++
        }
        rule.onNodeWithText("다크 모드").assertIsSelected()
        rule.runOnIdle { assertEquals(ScreenBgDark, background) }
    }

    @Test
    fun systemChanges_followSystemOnlyWhileSystemChoiceIsSelected() {
        val configuration = mutableStateOf(Configuration(rule.activity.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
        })
        var background = Color.Unspecified
        rule.setContent {
            CompositionLocalProvider(LocalConfiguration provides configuration.value) {
                AppTheme {
                    background = MaterialTheme.colorScheme.background
                    ThemeSettingsSection(prefs)
                }
            }
        }
        rule.runOnIdle { assertEquals(ScreenBgLight, background) }
        rule.runOnIdle {
            configuration.value = Configuration(configuration.value).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            }
        }
        rule.runOnIdle { assertEquals(ScreenBgDark, background) }
        rule.onNodeWithText("라이트 모드").performClick()
        rule.runOnIdle { assertEquals(ScreenBgLight, background) }
        rule.onNodeWithText("다크 모드").performClick()
        rule.runOnIdle {
            configuration.value = Configuration(configuration.value).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
            }
        }
        rule.runOnIdle { assertEquals(ScreenBgDark, background) }
        rule.onNodeWithText("시스템 기본값").performClick().assertIsSelected()
        rule.runOnIdle { assertEquals(ScreenBgLight, background) }
    }

    @Test
    fun compactScreenAt200PercentFont_keepsAllThreeChoicesReachable() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                AppTheme {
                    Box(Modifier.width(320.dp).height(640.dp)) {
                        SettingsScreen(onBackClick = {})
                    }
                }
            }
        }
        listOf("시스템 기본값", "라이트 모드", "다크 모드").forEach { label ->
            rule.onNodeWithText(label).performScrollTo().assertIsDisplayed().assertHasClickAction()
                .performClick().assertIsSelected()
        }
    }
}
