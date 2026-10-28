package io.github.hoons1994.shuttersoundzero.security

import android.app.KeyguardManager
import android.app.Application
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import android.accessibilityservice.AccessibilityServiceInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.hoons1994.shuttersoundzero.MainActivity
import io.github.hoons1994.shuttersoundzero.camera.CameraSettingsRepository
import io.github.hoons1994.shuttersoundzero.camera.ChangeCameraMute
import io.github.hoons1994.shuttersoundzero.core.CscMuteManager
import io.github.hoons1994.shuttersoundzero.data.PreferencesRepository
import io.github.hoons1994.shuttersoundzero.data.SetupIssue
import io.github.hoons1994.shuttersoundzero.ui.main.MainScreenViewModel
import io.github.hoons1994.shuttersoundzero.ui.settings.CameraSettingsOperationViewModel
import io.github.hoons1994.shuttersoundzero.ui.settings.SettingsPermissionRecovery
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith

/** Changes screen-lock settings only on a fresh emulator, never on a user's phone. */
@RunWith(AndroidJUnit4::class)
class AppLockAuthenticationInstrumentedTest {
    @get:Rule val composeTestRule = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private var configuredCredential = false
    private var originalAccessibilityFlags: Int? = null

    @Before
    fun setUp() {
        assumeTrue("Credential tests require an emulator", Build.HARDWARE in setOf("ranchu", "goldfish"))
        assumeFalse("Do not replace an existing screen-lock credential", keyguard.isDeviceSecure)
        shell("locksettings set-pin $TEST_PIN")
        configuredCredential = true
        awaitCondition("Emulator PIN was not configured") { keyguard.isDeviceSecure }
        deleteAuthenticationKey()
        val automation = instrumentation.uiAutomation
        val info = automation.serviceInfo
        originalAccessibilityFlags = info.flags
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = info
        PreferencesRepository.getInstance(context).apply {
            hasSeenInitialNotice = false
            isAppLockEnabled = false
        }
    }

    @After
    fun tearDown() {
        // @After also runs for skipped @Before: leave non-emulators and existing credentials untouched.
        if (!configuredCredential) return
        try {
            deleteAuthenticationKey()
        } finally {
            shell("locksettings clear --old $TEST_PIN")
            awaitCondition("Emulator PIN cleanup failed") { !keyguard.isDeviceSecure }
            originalAccessibilityFlags?.let { flags ->
                val info = instrumentation.uiAutomation.serviceInfo
                info.flags = flags
                instrumentation.uiAutomation.serviceInfo = info
            }
            context.getSharedPreferences("galaxy_camera_mute_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test
    fun keystoreRequiresPerUseStrongBiometricOrDeviceCredential() {
        AppLockAuthenticator.createSigningOperation()
        val privateKey = keyStore().getKey(AppLockAuthenticator.KEY_ALIAS, null) as PrivateKey
        val info = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
            .getKeySpec(privateKey, KeyInfo::class.java)
        assertTrue(info.isUserAuthenticationRequired)
        assertEquals(0, info.userAuthenticationValidityDurationSeconds)
        assertEquals(KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL, info.userAuthenticationType)
    }

    @Test
    fun signingWithoutSystemAuthenticationIsRejected() {
        assertUnauthenticatedSigningRejected(AppLockAuthenticator.createSigningOperation())
    }

    @Test
    fun removingAndRestoringScreenLockRecreatesInvalidatedKey() {
        AppLockAuthenticator.createSigningOperation()
        val previous = keyStore().getCertificate(AppLockAuthenticator.KEY_ALIAS).publicKey.encoded
        shell("locksettings clear --old $TEST_PIN")
        shell("locksettings set-pin $TEST_PIN")
        val operation = AppLockAuthenticator.createSigningOperation()
        val replacement = keyStore().getCertificate(AppLockAuthenticator.KEY_ALIAS).publicKey.encoded
        assertFalse("Invalidated authentication key must be replaced", previous.contentEquals(replacement))
        assertUnauthenticatedSigningRejected(operation)
        completeActualPinAuthentication()
    }

    @Test
    fun devicePinCompletesActualCryptoAuthenticationOnce() {
        completeActualPinAuthentication()
    }

    @Test
    fun wirelessSettingsReapplyWaitsForActualUnlockAndRunsOnce() {
        wirelessSettingsRecoveryRunsOnce(true)
    }

    @Test
    fun wirelessSettingsRestoreWaitsForActualUnlockAndRunsOnce() {
        wirelessSettingsRecoveryRunsOnce(false)
    }

    @Test
    fun appPermissionSettingsReapplyWaitsForActualUnlockAndRunsOnce() {
        wirelessSettingsRecoveryRunsOnce(true, permissionSettings = true)
    }

    @Test
    fun appPermissionSettingsRestoreWaitsForActualUnlockAndRunsOnce() {
        wirelessSettingsRecoveryRunsOnce(false, permissionSettings = true)
    }

    @Test
    fun settingsReapplyAfterPermissionSettingsWaitsForUnlockAndRunsOnce() {
        settingsPermissionRecoveryRunsOnce(targetMuted = true)
    }

    @Test
    fun settingsRestoreAfterPermissionSettingsWaitsForUnlockAndConfirmation() {
        settingsPermissionRecoveryRunsOnce(targetMuted = false)
    }

    @Test
    fun settingsPermissionStillDeniedAfterUnlockDoesNotRunTheAction() {
        settingsPermissionRecoveryRunsOnce(targetMuted = true, grantOnReturn = false)
    }

    private fun settingsPermissionRecoveryRunsOnce(targetMuted: Boolean, grantOnReturn: Boolean = true) {
        val originalCsc = Settings.System.getString(context.contentResolver, CscMuteManager.CSC_KEY)
        require(originalCsc == null || originalCsc.toIntOrNull() != null)
        val hadWritePermission = CscMuteManager.hasWritePermission(context)
        val permissionFlags = if (Build.VERSION.SDK_INT >= 37) {
            shell("dumpsys package ${context.packageName}").lineSequence().firstOrNull {
                it.contains("android.permission.ACCESS_LOCAL_NETWORK:") && it.contains("granted=")
            } ?: error("Cannot preserve the original local-network permission flags")
        } else ""
        val hadUserSet = permissionFlags.contains("USER_SET")
        val hadUserFixed = permissionFlags.contains("USER_FIXED")
        val appliedTargets = CopyOnWriteArrayList<Boolean>()
        val cleanupCalls = AtomicInteger()
        val permissionGranted = AtomicBoolean(false)
        val application = context.applicationContext as Application
        val suppressAutomaticPermissionPrompt = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) {
                if (activity is MainActivity && Build.VERSION.SDK_INT >= 37) {
                    MainActivity::class.java.getDeclaredField("localNetworkPermissionDeniedThisSession").apply {
                        isAccessible = true
                        setBoolean(activity, true)
                    }
                }
            }
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }
        application.registerActivityLifecycleCallbacks(suppressAutomaticPermissionPrompt)
        try {
            shell("pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS")
            shell("settings put system ${CscMuteManager.CSC_KEY} ${if (targetMuted) 1 else 0}")
            PreferencesRepository.getInstance(context).apply {
                // Suppress the unrelated notification permission request without changing its grant.
                hasSeenInitialNotice = false
                isAppLockEnabled = true
                isPermissionRevokedByUser = false
            }
            AppLockSession.lock()
            val launchIntent = Intent(context, MainActivity::class.java)
                .putExtra("settingsPermissionRecovery", "READY_REAPPLY")
            ActivityScenario.launch<MainActivity>(launchIntent).use { scenario ->
                lateinit var settingsViewModel: CameraSettingsOperationViewModel
                scenario.onActivity { activity ->
                    val factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                            CameraSettingsOperationViewModel(
                                application,
                                ChangeCameraMute(
                                    apply = { appliedTargets += it; Result.success(Unit) },
                                    verify = { true },
                                    disableWirelessDebugging = { cleanupCalls.incrementAndGet(); Result.success(Unit) }
                                ),
                                extras.createSavedStateHandle(),
                                localNetworkGranted = { permissionGranted.get() },
                                wirelessDebuggingState = { true }
                            ) as T
                    }
                    settingsViewModel = ViewModelProvider(activity, factory)[CameraSettingsOperationViewModel::class.java]
                }
                enterSystemPin(awaitCredentialPrompt())
                awaitCondition("Initial authentication did not unlock MainActivity") { AppLockSession.isUnlocked }
                scenario.onActivity { activity ->
                    assertEquals("External Intent must not create a settings operation", null, settingsViewModel.permissionRecovery.value)
                    setInitialNoticeVisible(activity, false)
                    PreferencesRepository.getInstance(context).hasSeenInitialNotice = true
                }
                composeTestRule.onNodeWithContentDescription("설정").performClick()
                composeTestRule.onNodeWithText(
                    if (targetMuted) "카메라 무음 다시 적용" else "카메라 셔터음 원래대로 복원"
                ).performClick()
                awaitSettingsPermissionDenial()
                composeTestRule.onNodeWithText("앱 권한 설정").performClick()
                awaitCondition("App permission settings did not stop MainActivity") { scenario.state == Lifecycle.State.CREATED }
                instrumentation.waitForIdleSync()
                assertFalse(AppLockSession.isUnlocked)
                assertEquals(
                    if (targetMuted) SettingsPermissionRecovery.WAITING_REAPPLY else SettingsPermissionRecovery.WAITING_RESTORE,
                    settingsViewModel.permissionRecovery.value
                )
                assertTrue(appliedTargets.isEmpty())
                permissionGranted.set(grantOnReturn)
                context.startActivity(Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                val credential = awaitCredentialPrompt()
                assertFalse(AppLockSession.isUnlocked)
                assertTrue("A settings request must not run before authentication", appliedTargets.isEmpty())
                // Keep the unrelated notification permission prompt out of this test without
                // granting or denying it. Restore the visible notice immediately after unlock.
                scenario.onActivity { activity -> setInitialNoticeVisible(activity, true) }
                enterSystemPin(credential)
                awaitCondition("Authentication did not unlock the settings request") { AppLockSession.isUnlocked }
                scenario.onActivity { activity -> setInitialNoticeVisible(activity, false) }
                // The settings destination resumes the request from a Compose effect;
                // let the test clock advance before waiting for its asynchronous operation.
                composeTestRule.waitForIdle()
                if (!grantOnReturn) {
                    awaitSettingsPermissionDenial()
                    assertTrue(appliedTargets.isEmpty())
                } else if (!targetMuted) {
                    awaitCondition("Restoration confirmation did not return after authentication") {
                        composeTestRule.onAllNodesWithText("카메라 셔터음을 원래대로 복원할까요?")
                            .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
                    }
                    composeTestRule.onNodeWithText("카메라 셔터음을 원래대로 복원할까요?").assertExists()
                    assertTrue("Restoration needs explicit confirmation", appliedTargets.isEmpty())
                    scenario.recreate()
                    composeTestRule.onNodeWithText("카메라 셔터음을 원래대로 복원할까요?").assertExists()
                    assertTrue(appliedTargets.isEmpty())
                    composeTestRule.onNodeWithText("원래대로 복원").performClick()
                }
                if (grantOnReturn) {
                    try {
                        awaitCondition("Original settings request was not applied") { appliedTargets.size == 1 }
                    } catch (error: AssertionError) {
                        throw AssertionError(
                            "Recovery=${settingsViewModel.permissionRecovery.value}, " +
                                "operation=${settingsViewModel.uiState.value}, " +
                                "permission=${permissionGranted.get()}, unlocked=${AppLockSession.isUnlocked}",
                            error
                        )
                    }
                }
                composeTestRule.waitForIdle()
                scenario.moveToState(Lifecycle.State.STARTED)
                scenario.moveToState(Lifecycle.State.RESUMED)
                scenario.recreate()
                composeTestRule.waitForIdle()
                assertEquals(if (grantOnReturn) listOf(targetMuted) else emptyList(), appliedTargets.toList())
                assertEquals(if (grantOnReturn) 1 else 0, cleanupCalls.get())
            }
        } finally {
            application.unregisterActivityLifecycleCallbacks(suppressAutomaticPermissionPrompt)
            if (originalCsc == null) shell("settings delete system ${CscMuteManager.CSC_KEY}")
            else shell("settings put system ${CscMuteManager.CSC_KEY} ${originalCsc.toInt()}")
            if (!hadWritePermission) shell("pm revoke ${context.packageName} android.permission.WRITE_SECURE_SETTINGS")
            if (Build.VERSION.SDK_INT >= 37) {
                if (!hadUserSet) shell("pm clear-permission-flags ${context.packageName} android.permission.ACCESS_LOCAL_NETWORK user-set")
                if (!hadUserFixed) shell("pm clear-permission-flags ${context.packageName} android.permission.ACCESS_LOCAL_NETWORK user-fixed")
            }
        }
    }

    private fun awaitSettingsPermissionDenial() {
        awaitCondition("Settings permission recovery dialog did not appear") {
            val automation = instrumentation.uiAutomation
            val roots = listOfNotNull(automation.rootInActiveWindow) + automation.windows.mapNotNull { it.root }
            roots.firstNotNullOfOrNull { root ->
                findNode(root) { node ->
                    node.viewIdResourceName?.contains("permission_deny_button") == true &&
                        node.isEnabled && node.isVisibleToUser
                }
            }?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            composeTestRule.onAllNodesWithText("앱 권한 설정")
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    private fun setInitialNoticeVisible(activity: MainActivity, visible: Boolean) {
        MainActivity::class.java.getDeclaredMethod("setShowInitialNotice", Boolean::class.javaPrimitiveType).apply {
            isAccessible = true
            invoke(activity, visible)
        }
    }

    private fun wirelessSettingsRecoveryRunsOnce(targetMuted: Boolean, permissionSettings: Boolean = false) {
        // setUp has already restricted credential changes to a fresh emulator.
        val originalCsc = Settings.System.getString(context.contentResolver, CscMuteManager.CSC_KEY)
        require(originalCsc == null || originalCsc.toIntOrNull() != null)
        // Provision this once with the disposable AVD. Turning it off while the
        // real Settings page is alive can also shut down the CI's ADB transport.
        assertEquals("Enable developer options on the test AVD before instrumentation",
            1, Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0))
        assertTrue("Open Settings once to activate its developer Activity on the test AVD",
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).setPackage("com.android.settings")
                .resolveActivity(context.packageManager) != null)
        val hadWritePermission = CscMuteManager.hasWritePermission(context)
        val appliedTargets = CopyOnWriteArrayList<Boolean>()
        val cleanupCalls = AtomicInteger()
        val wirelessEnabled = AtomicBoolean(permissionSettings)
        val permissionGranted = AtomicBoolean(!permissionSettings)
        val application = context.applicationContext as Application
        // Network capability is supplied by the VM fixture below. Suppress only the
        // unrelated API 37 permission prompt, without granting LNP and invalidating
        // the separate fresh-install permission test. Authentication stays real.
        val permissionPromptFixture = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) {
                if (activity is MainActivity && Build.VERSION.SDK_INT >= 37) {
                    MainActivity::class.java.getDeclaredField("localNetworkPermissionDeniedThisSession").apply {
                        isAccessible = true
                        setBoolean(activity, true)
                    }
                }
            }
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }
        application.registerActivityLifecycleCallbacks(permissionPromptFixture)
        try {
            shell("pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS")
            // As with GrantPermissionRule, grants last for this disposable AVD; revoking a
            // dangerous permission inside instrumentation would kill its own process.
            if (Build.VERSION.SDK_INT >= 33) shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
            shell("settings put system ${CscMuteManager.CSC_KEY} 1")
            PreferencesRepository.getInstance(context).apply {
                hasSeenInitialNotice = true
                isAppLockEnabled = true
                isPermissionRevokedByUser = false
                lastConnectPort = 43210
                lastSetupIssue = SetupIssue.CAMERA_APPLY
            }
            AppLockSession.lock()
            // Try to inject the opposite operation through this exported launcher.
            // Only the later in-app request may create a pending resume command.
            val launchIntent = Intent(context, MainActivity::class.java)
                .putExtra("cameraMuteAfterWirelessSettings", !targetMuted)
                .putExtra("permissionSettingsRequest", if (targetMuted) "restore" else "mute")
            ActivityScenario.launch<MainActivity>(launchIntent).use { scenario ->
                lateinit var viewModel: MainScreenViewModel
                scenario.onActivity { activity ->
                    val factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                            MainScreenViewModel(
                                context.applicationContext as Application,
                                ChangeCameraMute(
                                    apply = { appliedTargets += it; Result.success(Unit) },
                                    verify = { true },
                                    disableWirelessDebugging = {
                                        cleanupCalls.incrementAndGet()
                                        wirelessEnabled.set(false)
                                        Result.success(Unit)
                                    }
                                ),
                                CameraSettingsRepository(context) { _, _ -> error("No new ADB or pairing flow expected") },
                                isSamsungDevice = { true },
                                localNetworkGranted = { permissionGranted.get() },
                                wirelessDebuggingState = { wirelessEnabled.get() },
                                savedStateHandle = extras.createSavedStateHandle()
                            ) as T
                    }
                    viewModel = ViewModelProvider(activity, factory)[MainScreenViewModel::class.java]
                }
                enterSystemPin(awaitCredentialPrompt())
                awaitCondition("Initial authentication did not unlock MainActivity") { AppLockSession.isUnlocked }
                composeTestRule.waitForIdle()
                scenario.onActivity {
                    assertFalse("External Intent extras created a pending camera operation",
                        viewModel.uiState.value.showSwitchFailureHelp)
                    assertEquals(null, viewModel.uiState.value.pendingCameraMuteTarget)
                    assertTrue(appliedTargets.isEmpty())
                }
                if (targetMuted) {
                    composeTestRule.onNodeWithText("다시 적용하기").performScrollTo().performClick()
                } else {
                    scenario.onActivity { viewModel.toggleCscMute(false) }
                }
                composeTestRule.onNodeWithText(if (permissionSettings) "앱 권한 설정" else "무선 디버깅 켜기").performClick()
                awaitCondition("Settings did not stop MainActivity") { scenario.state == Lifecycle.State.CREATED }
                instrumentation.waitForIdleSync()
                assertFalse(AppLockSession.isUnlocked)
                assertTrue(appliedTargets.isEmpty())
                wirelessEnabled.set(true)
                permissionGranted.set(true)
                context.startActivity(Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                val credential = awaitCredentialPrompt()
                assertFalse(AppLockSession.isUnlocked)
                assertTrue("A settings return must not apply before authentication", appliedTargets.isEmpty())
                enterSystemPin(credential)
                awaitCondition("Original camera request was lost across app lock") { appliedTargets.size == 1 }
                composeTestRule.waitForIdle()
                scenario.moveToState(Lifecycle.State.STARTED)
                scenario.moveToState(Lifecycle.State.RESUMED)
                scenario.recreate()
                composeTestRule.waitForIdle()
                assertEquals(listOf(targetMuted), appliedTargets.toList())
                assertEquals(1, cleanupCalls.get())
            }
        } finally {
            application.unregisterActivityLifecycleCallbacks(permissionPromptFixture)
            if (originalCsc == null) shell("settings delete system ${CscMuteManager.CSC_KEY}")
            else shell("settings put system ${CscMuteManager.CSC_KEY} ${originalCsc.toInt()}")
            if (!hadWritePermission) shell("pm revoke ${context.packageName} android.permission.WRITE_SECURE_SETTINGS")
        }
    }

    private fun completeActualPinAuthentication() {
        val completed = CountDownLatch(1)
        val successes = AtomicInteger()
        val error = AtomicReference<String?>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                AppLockAuthenticator.authenticate(activity, "Authentication integration test", "Enter the emulator PIN",
                    onSuccess = { successes.incrementAndGet(); completed.countDown() },
                    onError = { error.set(it); completed.countDown() },
                    onCancelled = { error.set("cancelled"); completed.countDown() })
            }
            val inputControl = awaitCredentialPrompt()
            assertEquals(0, successes.get())
            enterSystemPin(inputControl)
            assertTrue("PIN authentication did not finish: ${error.get()}", completed.await(20, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            assertEquals("PIN must unlock only after Keystore signing succeeds: ${error.get()}", 1, successes.get())
        }
    }

    private fun enterSystemPin(inputControl: AccessibilityNodeInfo) {
        if (isPinPad(inputControl)) {
            // Android 17 replaces the password EditText with a Compose system PIN pad.
            // Its buttons don't expose digit labels. Use normal keyboard input,
            // delivered to the focused system control, without changing the PIN check.
            awaitCondition("System PIN pad has no keyboard focus") {
                credentialInput()?.let { current ->
                    isPinPad(current) && findNode(current) { it.isFocused } != null
                } == true
            }
            val keys = TEST_PIN.map { "KEYCODE_$it" }.joinToString(" ")
            shell("input keyevent --delay 100 $keys KEYCODE_ENTER")
        } else {
            // Accessibility nodes are snapshots. The credential transition may replace the
            // original node, and ACTION_FOCUS can return false for an already-focused field.
            // Re-query the actual editable system field and wait for observed keyboard focus.
            awaitCondition("System PIN field could not receive focus") {
                val current = credentialInput()?.takeUnless(::isPinPad)
                if (current != null && !current.isFocused) {
                    current.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                credentialInput()?.let { !isPinPad(it) && it.isFocused } == true
            }
            val input = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, TEST_PIN)
            }
            awaitCondition("System PIN field did not accept input") {
                credentialInput()?.let {
                    !isPinPad(it) && it.isFocused &&
                        it.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, input)
                } == true
            }
            shell("input keyevent KEYCODE_ENTER")
        }
    }

    private fun assertUnauthenticatedSigningRejected(operation: Signature) {
        try {
            operation.update(ByteArray(32) { it.toByte() })
            operation.sign()
            fail("Unauthenticated Keystore signing must not be possible")
        } catch (_: GeneralSecurityException) {
            // The actual Android Keystore must enforce authentication, not only an app callback.
        }
    }

    @Test
    fun cancellingActualCredentialPromptDoesNotUnlock() {
        val completed = CountDownLatch(1)
        val successes = AtomicInteger()
        val terminalResult = AtomicReference<String?>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                AppLockAuthenticator.authenticate(activity, "Authentication cancellation test", "Cancel the emulator PIN",
                    onSuccess = { successes.incrementAndGet(); completed.countDown() },
                    onCancelled = { terminalResult.set("cancelled"); completed.countDown() },
                    onError = { terminalResult.set(it); completed.countDown() })
            }
            awaitCredentialPrompt()
            shell("input keyevent KEYCODE_BACK")
            // Older Android versions consume the first Back to dismiss the PIN keyboard.
            // Send a second Back only while the credential prompt is still present.
            if (!completed.await(3, TimeUnit.SECONDS) &&
                credentialInput() != null
            ) shell("input keyevent KEYCODE_BACK")
            assertTrue("Cancellation was not delivered", completed.await(20, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            assertEquals(0, successes.get())
            assertEquals("cancelled", terminalResult.get())
        }
    }

    private fun awaitCredentialPrompt(): AccessibilityNodeInfo {
        var field: AccessibilityNodeInfo? = null
        awaitCondition("System credential prompt did not appear") {
            field = credentialInput()
            // Cancellation needs either a password field or the newer system PIN pad.
            field != null
        }
        return requireNotNull(field)
    }

    private fun credentialUiDescription(): String {
        val nodes = mutableListOf<String>()
        fun append(node: AccessibilityNodeInfo?) {
            if (node == null || nodes.size >= 80) return
            // Record only structure, never the credential's text.
            nodes += "${node.packageName}/${node.className} id=${node.viewIdResourceName}" +
                " visible=${node.isVisibleToUser} focused=${node.isFocused} password=${node.isPassword}"
            for (index in 0 until node.childCount) append(node.getChild(index))
        }
        val automation = instrumentation.uiAutomation
        append(automation.rootInActiveWindow)
        automation.windows.forEach { append(it.root) }
        return "Credential UI: ${nodes.joinToString("; ")}"
    }

    private fun credentialInput(): AccessibilityNodeInfo? {
        val automation = instrumentation.uiAutomation
        // The IME can be the active window, so inspect the other interactive windows too.
        val roots = listOfNotNull(automation.rootInActiveWindow) + automation.windows.mapNotNull { it.root }
        return roots.firstNotNullOfOrNull { root ->
            findNode(root) { node ->
                val isSystemCredential = node.packageName?.toString() in
                    setOf("com.android.systemui", "com.android.settings")
                isSystemCredential && node.isEnabled && node.isVisibleToUser &&
                    (isPinPad(node) ||
                        ((node.isEditable || node.className?.toString() == "android.widget.EditText") &&
                            (node.isPassword || node.viewIdResourceName?.endsWith("/lockPassword") == true)))
            }
        }
    }

    private fun isPinPad(node: AccessibilityNodeInfo): Boolean =
        node.viewIdResourceName == "cred_pin_pad" ||
            node.viewIdResourceName?.endsWith("/cred_pin_pad") == true

    private fun findNode(node: AccessibilityNodeInfo?, matches: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (node == null) return null
        if (matches(node)) return node
        for (index in 0 until node.childCount) {
            findNode(node.getChild(index), matches)?.let { return it }
        }
        return null
    }

    private fun awaitCondition(message: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(100)
        }
        if (!condition()) fail("$message. ${credentialUiDescription()}")
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .bufferedReader().use { it.readText() }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun deleteAuthenticationKey() = keyStore().deleteEntry(AppLockAuthenticator.KEY_ALIAS)

    private companion object {
        const val TEST_PIN = "246810"
    }
}
