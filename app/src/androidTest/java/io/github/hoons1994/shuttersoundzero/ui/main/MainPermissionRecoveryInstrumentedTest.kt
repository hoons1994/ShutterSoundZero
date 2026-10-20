package io.github.hoons1994.shuttersoundzero.ui.main

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hoons1994.shuttersoundzero.camera.CameraSettingsRepository
import io.github.hoons1994.shuttersoundzero.camera.ChangeCameraMute
import io.github.hoons1994.shuttersoundzero.core.CscMuteManager
import io.github.hoons1994.shuttersoundzero.data.PreferencesRepository
import io.github.hoons1994.shuttersoundzero.data.SetupIssue
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Real home/VM callbacks with simulated OS results; never starts pairing or system Settings. */
class MainPermissionRecoveryInstrumentedTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val application: Application get() = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: PreferencesRepository
    private lateinit var viewModel: MainScreenViewModel
    private lateinit var registry: RecordingRegistry
    private lateinit var context: RecordingContext
    private var permissionGranted = false
    private var wirelessEnabled = true
    private val appliedTargets = mutableListOf<Boolean>()
    private var originalCsc: String? = null
    private var originalPreferences: Map<String, *> = emptyMap<String, Any>()
    private var hadWritePermission = false
    private var prepared = false

    @Before fun prepareExistingLinkage() {
        assumeTrue("System settings changes are limited to an emulator",
            Build.FINGERPRINT.startsWith("generic") || Build.FINGERPRINT.startsWith("google/sdk_gphone") ||
                Build.MODEL.contains("sdk_gphone"))
        hadWritePermission = CscMuteManager.hasWritePermission(application)
        originalCsc = Settings.System.getString(application.contentResolver, CscMuteManager.CSC_KEY)
        assumeTrue("Original CSC must be restorable", originalCsc == null || originalCsc?.toIntOrNull() != null)
        originalPreferences = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).all.toMap()
        prepared = true
        shell("pm grant ${application.packageName} android.permission.WRITE_SECURE_SETTINGS")
        shell("settings put system ${CscMuteManager.CSC_KEY} 1")
        assertTrue(CscMuteManager.hasWritePermission(application))
        prefs = PreferencesRepository.getInstance(application)
        prefs.isPermissionRevokedByUser = false
        prefs.lastConnectPort = 43210
        prefs.lastSetupIssue = SetupIssue.CAMERA_APPLY
        context = RecordingContext(composeTestRule.activity)
        registry = RecordingRegistry(composeTestRule.activity)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel = MainScreenViewModel(
                application,
                ChangeCameraMute(
                    apply = { appliedTargets += it; Result.success(Unit) },
                    verify = { true },
                    disableWirelessDebugging = { Result.success(Unit) }
                ),
                CameraSettingsRepository(application) { _, _ -> error("Must not start a new ADB/pairing flow") },
                isSamsungDevice = { true },
                localNetworkGranted = { permissionGranted },
                wirelessDebuggingState = { wirelessEnabled }
            )
        }
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = registry
        }
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalActivityResultRegistryOwner provides owner
            ) {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    @After fun restoreOriginalState() {
        if (!prepared) return
        if (originalCsc == null) shell("settings delete system ${CscMuteManager.CSC_KEY}")
        else shell("settings put system ${CscMuteManager.CSC_KEY} ${originalCsc!!.toInt()}")
        val editor = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear()
        originalPreferences.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        assertTrue(editor.commit())
        if (!hadWritePermission) shell("pm revoke ${application.packageName} android.permission.WRITE_SECURE_SETTINGS")
    }

    @Test fun existingPermissionReapplyOpensAppSettingsAndResumesWithoutAnotherCode() {
        composeTestRule.onNodeWithText("다시 적용하기").performScrollTo().performClick()
        assertPermissionRepairAvailable()
        openPermissionSettings()
        assertEquals(43210, prefs.lastConnectPort)
        assertEquals(SetupIssue.CAMERA_APPLY, prefs.lastSetupIssue)
        assertTrue(appliedTargets.isEmpty())

        permissionGranted = true
        returnFromPermissionSettings()
        composeTestRule.runOnIdle {
            assertEquals(listOf(true), appliedTargets)
            assertEquals(43210, prefs.lastConnectPort)
            assertEquals(0, context.serviceStarts)
            assertFalse(viewModel.uiState.value.infoMessage.orEmpty().contains("코드 입력"))
        }
    }

    @Test fun permanentDenialCanReopenSettingsAndResumeTheOriginalRestoreOnlyOnce() {
        beginRestore()
        openPermissionSettings()
        returnFromPermissionSettings()
        assertPermissionRepairAvailable()
        composeTestRule.runOnIdle {
            assertEquals(false, viewModel.uiState.value.pendingCameraMuteTarget)
            assertTrue(appliedTargets.isEmpty())
        }

        openPermissionSettings()
        assertEquals(2, registry.intents.size)
        permissionGranted = true
        returnFromPermissionSettings()
        returnFromPermissionSettings() // An already-consumed result must not repeat the operation.
        composeTestRule.runOnIdle {
            assertEquals(listOf(false), appliedTargets)
            assertEquals(43210, prefs.lastConnectPort)
            assertEquals(0, context.serviceStarts)
        }
    }

    @Test fun restoreGoalSurvivesPermissionRepairWhenWirelessDebuggingAlsoNeedsEnabling() {
        beginRestore()
        openPermissionSettings()
        permissionGranted = true
        wirelessEnabled = false
        returnFromPermissionSettings()
        composeTestRule.onNodeWithText("무선 디버깅을 켜 주세요").assertExists()
        composeTestRule.onNodeWithText("무선 디버깅 켜기").performClick()
        assertEquals(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, context.settingsIntents.single().action)
        wirelessEnabled = true
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeTestRule.runOnIdle {
            assertEquals(listOf(false), appliedTargets)
            assertEquals(0, context.serviceStarts)
        }
    }

    @Test fun unavailablePermissionSettingsKeepsTheRestoreRecoveryAction() {
        beginRestore()
        registry.failNextLaunch = true
        composeTestRule.onNodeWithText("앱 권한 설정").performClick()
        assertPermissionRepairAvailable()
        composeTestRule.runOnIdle {
            assertEquals(false, viewModel.uiState.value.pendingCameraMuteTarget)
            assertTrue(appliedTargets.isEmpty())
        }
        openPermissionSettings()
        permissionGranted = true
        returnFromPermissionSettings()
        composeTestRule.runOnIdle { assertEquals(listOf(false), appliedTargets) }
    }

    private fun beginRestore() {
        shell("settings put system ${CscMuteManager.CSC_KEY} 0")
        composeTestRule.runOnIdle {
            viewModel.refreshState()
            viewModel.toggleCscMute(false)
        }
    }

    private fun assertPermissionRepairAvailable() {
        composeTestRule.onNodeWithText("앱 권한을 확인해 주세요").assertExists()
        composeTestRule.onNodeWithText("앱 권한 설정").assertHasClickAction()
    }

    private fun openPermissionSettings() {
        composeTestRule.onNodeWithText("앱 권한 설정").performClick()
        val intent = registry.intents.last()
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("com.android.settings", intent.`package`)
        assertEquals("package:${application.packageName}", intent.data.toString())
    }

    private fun returnFromPermissionSettings() {
        composeTestRule.runOnIdle { registry.dispatchResult(registry.lastRequestCode, Activity.RESULT_OK, null) }
    }

    private class RecordingRegistry(private val context: Context) : ActivityResultRegistry() {
        val intents = mutableListOf<Intent>()
        var lastRequestCode = 0
        var failNextLaunch = false
        override fun <I, O> onLaunch(
            requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?
        ) {
            lastRequestCode = requestCode
            intents += contract.createIntent(context, input)
            if (failNextLaunch) {
                failNextLaunch = false
                throw ActivityNotFoundException("Injected settings launch failure")
            }
        }
    }

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        val settingsIntents = mutableListOf<Intent>()
        var serviceStarts = 0
        override fun startActivity(intent: Intent) { settingsIntents += intent }
        override fun startForegroundService(intent: Intent): ComponentName? {
            serviceStarts++
            return intent.component
        }
        override fun startService(intent: Intent): ComponentName? = startForegroundService(intent)
    }

    private fun shell(command: String): String = InstrumentationRegistry.getInstrumentation().uiAutomation
        .executeShellCommand(command).let { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        }

    private companion object { const val PREFS_NAME = "galaxy_camera_mute_prefs" }
}
