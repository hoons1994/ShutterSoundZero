package io.github.hoons1994.shuttersoundzero.ui.settings

import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.mutableStateOf
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.hoons1994.shuttersoundzero.core.CscMuteManager
import io.github.hoons1994.shuttersoundzero.core.DeveloperOptionsManager
import io.github.hoons1994.shuttersoundzero.core.WifiConnectionStatus
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

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
            val showSettings = mutableStateOf(false)
            composeTestRule.setContent {
                if (showSettings.value) SettingsScreen(onBackClick = {})
                else androidx.compose.material3.Text("무선 디버깅 테스트 준비")
            }
            composeTestRule.onNodeWithText("무선 디버깅 테스트 준비").assertExists()
            // A fresh AVD otherwise opens SystemUI's Wi-Fi approval Activity in the middle
            // of the assertion, hiding every Compose root and introducing ON_RESUME.
            // Approve only this emulator network before exercising the observer.
            approveEmulatorWifiNetwork()
            shell("settings put global adb_wifi_enabled 0")
            awaitCondition("Wireless debugging did not stop before the test") {
                DeveloperOptionsManager.readWirelessDebuggingEnabled(context) == false
            }
            shell("settings put system ${CscMuteManager.CSC_KEY} ${if (muted) 0 else 1}")
            composeTestRule.runOnIdle { showSettings.value = true }
            composeTestRule.onNodeWithText(
                if (muted) "카메라 셔터음 원래대로 복원" else "카메라 무음 다시 적용"
            ).performClick()
            composeTestRule.onNodeWithText("무선 디버깅을 켜 주세요").assertExists()

            val resumeEvents = AtomicInteger()
            val lifecycleObserver = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) resumeEvents.incrementAndGet()
            }
            composeTestRule.runOnIdle {
                context.lifecycle.addObserver(lifecycleObserver)
                resumeEvents.set(0) // Ignore the initial lifecycle catch-up callback.
            }
            try {
                // A provider notification must close the prompt without a lifecycle refresh.
                shell("settings put global adb_wifi_enabled 1")
                composeTestRule.waitUntil(timeoutMillis = 10_000) {
                    val settingsVisible = composeTestRule.onAllNodesWithText("설정")
                        .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
                    val promptGone = composeTestRule.onAllNodesWithText("무선 디버깅을 켜 주세요")
                        .fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
                    settingsVisible && promptGone
                }
                composeTestRule.onNodeWithText("설정").assertExists()
                assertTrue(DeveloperOptionsManager.readWirelessDebuggingEnabled(context) == true)
                assertEquals("The observer must work without ON_RESUME", 0, resumeEvents.get())
            } finally {
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    context.lifecycle.removeObserver(lifecycleObserver)
                }
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

    /** The CI creates disposable AVDs; never approve a network on a physical device. */
    private fun approveEmulatorWifiNetwork() {
        check(isEmulator())
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetPackage = instrumentation.targetContext.packageName
        val automation = instrumentation.uiAutomation
        val originalFlags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        try {
            shell("svc wifi enable")
            awaitCondition("The disposable emulator did not connect to Wi-Fi") {
                WifiConnectionStatus.isWifiConnected(instrumentation.targetContext)
            }
            // Force an actual off -> on transition rather than accepting a stale
            // enabled value left by an earlier real Settings Activity.
            shell("settings put global adb_wifi_enabled 0")
            awaitCondition("Wireless debugging did not reset before approval") {
                DeveloperOptionsManager.readWirelessDebuggingEnabled(instrumentation.targetContext) == false
            }
            shell("settings put global adb_wifi_enabled 1")
            var readySince: Long? = null
            var approvalState = "waiting"
            awaitCondition("The emulator Wi-Fi debugging approval did not complete", { "; $approvalState" }) {
                val wifiApprovalActive = shell("dumpsys activity activities").lineSequence().any {
                    it.contains("ResumedActivity") && it.contains("WifiDebuggingActivity")
                }
                val wirelessEnabled = DeveloperOptionsManager.readWirelessDebuggingEnabled(instrumentation.targetContext)
                val activePackage = automation.rootInActiveWindow?.packageName?.toString()
                approvalState = "approval=${if (wifiApprovalActive) "visible" else "absent"}; " +
                    "wireless=$wirelessEnabled; activePackage=$activePackage"
                if (wifiApprovalActive) {
                    // On API 32 the active accessibility root can still belong to the
                    // previous window when the SystemUI Activity is already resumed.
                    // Inspect interactive windows as well, and refresh their snapshots.
                    val roots = automation.windows.mapNotNull { it.root } +
                        listOfNotNull(automation.rootInActiveWindow)
                    for (root in roots) {
                        root.refresh()
                        if (root.packageName?.toString() !in setOf("com.android.systemui", "android")) continue
                        val checkbox = root.findAccessibilityNodeInfosByViewId("android:id/alwaysUse")
                            .singleOrNull()
                        val allow = root.findAccessibilityNodeInfosByViewId("android:id/button1")
                            .singleOrNull()
                        if (checkbox != null && allow != null && allow.isEnabled) {
                            if (!checkbox.isChecked) {
                                check(checkbox.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                            }
                            check(allow.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                            approvalState = "approval accepted"
                            break
                        }
                    }
                }
                // The setting becomes true before the approval Activity appears. Require a
                // stable return to the test Activity. Internal adbd properties are SELinux-
                // restricted even for shell and are not a readiness signal for this UI test.
                val ready = !wifiApprovalActive &&
                    wirelessEnabled == true && activePackage == targetPackage
                val now = SystemClock.elapsedRealtime()
                readySince = if (ready) readySince ?: now else null
                readySince?.let { now - it >= 1_000 } == true
            }
        } finally {
            automation.serviceInfo = automation.serviceInfo.apply { flags = originalFlags }
        }
    }

    private fun awaitCondition(message: String, diagnostic: () -> String = { "" }, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        fail(message + diagnostic())
    }

    private fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader().use { it.readText() }
    }

    private fun isEmulator(): Boolean = Build.FINGERPRINT.startsWith("generic") ||
        Build.FINGERPRINT.startsWith("google/sdk_gphone") || Build.MODEL.contains("sdk_gphone")
}
