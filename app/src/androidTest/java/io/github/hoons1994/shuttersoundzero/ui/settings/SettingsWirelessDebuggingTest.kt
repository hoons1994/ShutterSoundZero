package io.github.hoons1994.shuttersoundzero.ui.settings

import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hoons1994.shuttersoundzero.core.CscMuteManager
import io.github.hoons1994.shuttersoundzero.core.DeveloperOptionsManager
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Exercises the real Settings observer without resuming the Activity. Emulator only. */
class SettingsWirelessDebuggingTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun reapplyPromptClosesWhenWirelessDebuggingIsEnabled() = verifyPrompt(muted = false)
    @Test fun restorePromptClosesWhenWirelessDebuggingIsEnabled() = verifyPrompt(muted = true)

    private fun verifyPrompt(muted: Boolean) {
        assumeTrue("System settings changes are limited to an emulator", isEmulator())
        val context = composeTestRule.activity
        val resolver = context.contentResolver
        val permission = "android.permission.WRITE_SECURE_SETTINGS"
        val hadPermission = CscMuteManager.hasWritePermission(context)
        val developerOptions = Settings.Global.getString(resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED)
        val wireless = Settings.Global.getString(resolver, "adb_wifi_enabled")
        val csc = Settings.System.getString(resolver, CscMuteManager.CSC_KEY)
        assumeTrue("Original settings must be safely restorable", listOf(developerOptions, wireless, csc).all {
            it == null || it.toIntOrNull() != null
        })
        try {
            shell("pm grant ${context.packageName} $permission")
            shell("settings put global development_settings_enabled 1")
            shell("settings put global adb_wifi_enabled 0")
            shell("settings put system ${CscMuteManager.CSC_KEY} ${if (muted) 0 else 1}")
            assumeTrue("Wireless debugging must initially be off", DeveloperOptionsManager.readWirelessDebuggingEnabled(context) == false)
            composeTestRule.setContent { SettingsScreen(onBackClick = {}) }
            composeTestRule.onNodeWithText(
                if (muted) "카메라 셔터음 원래대로 복원" else "카메라 무음 다시 적용"
            ).performClick()
            composeTestRule.onNodeWithText("무선 디버깅을 켜 주세요").assertExists()

            // A settings notification must close the prompt even without ON_RESUME.
            shell("settings put global adb_wifi_enabled 1")
            assumeTrue("Emulator must support enabling wireless debugging", DeveloperOptionsManager.readWirelessDebuggingEnabled(context) == true)
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText("무선 디버깅을 켜 주세요").fetchSemanticsNodes().isEmpty()
            }
        } finally {
            restoreSetting("system", CscMuteManager.CSC_KEY, csc)
            restoreSetting("global", "adb_wifi_enabled", wireless)
            restoreSetting("global", Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, developerOptions)
            if (!hadPermission) shell("pm revoke ${context.packageName} $permission")
        }
    }

    private fun restoreSetting(namespace: String, key: String, value: String?) {
        if (value == null) shell("settings delete $namespace $key")
        else {
            // These three test settings are numeric; never interpolate arbitrary provider data.
            val numericValue = value.toInt()
            shell("settings put $namespace $key $numericValue")
        }
    }

    private fun shell(command: String) {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private fun isEmulator(): Boolean = Build.FINGERPRINT.startsWith("generic") ||
        Build.FINGERPRINT.startsWith("google/sdk_gphone") || Build.MODEL.contains("sdk_gphone")
}
