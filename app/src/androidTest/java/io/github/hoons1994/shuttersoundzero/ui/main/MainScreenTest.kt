package io.github.hoons1994.shuttersoundzero.ui.main

import io.github.hoons1994.shuttersoundzero.testing.targetString
import io.github.hoons1994.shuttersoundzero.R
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import io.github.hoons1994.shuttersoundzero.data.SetupIssue
import io.github.hoons1994.shuttersoundzero.service.PairingCodePrompt
import io.github.hoons1994.shuttersoundzero.service.NotificationRecovery
import androidx.compose.runtime.mutableStateOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** UI smoke tests for the state-focused home flow. */
class MainScreenTest {
    @Test fun pairingRecoveryAllowsOurSettingsTaskButStopsAfterHome() {
        val activity = composeTestRule.activity
        val visibility = io.github.hoons1994.shuttersoundzero.core.AppVisibility
        val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        try {
            composeTestRule.runOnUiThread {
                io.github.hoons1994.shuttersoundzero.core.SetupSettingsNavigator
                    .openWirelessDebuggingOrDevOptions(activity).getOrThrow()
            }
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                !visibility.isVisible && visibility.canOfferRecovery(activity)
            }
            automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
            composeTestRule.waitUntil(timeoutMillis = 5_000) { !visibility.canOfferRecovery(activity) }
        } finally {
            composeTestRule.runOnUiThread {
                activity.startActivity(android.content.Intent(activity, activity.javaClass).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                })
            }
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
            }
        }
    }

    @Test fun existingSetupButtonRestoresOnlyWhileWaitingForCode() {
        val prompt = mutableStateOf<PairingCodePrompt?>(null)
        var clicks = 0
        composeTestRule.setContent {
            HomeContent(uiState = MainUiState(), codePrompt = prompt.value, onSetup = { clicks++ })
        }
        val restore = composeTestRule.onNodeWithText(targetString(R.string.pairing_restore_notification))
        restore.assertDoesNotExist()
        composeTestRule.runOnIdle { prompt.value = PairingCodePrompt("current", 3) }
        restore.assertIsEnabled().performClick()
        composeTestRule.onNodeWithText(targetString(R.string.home_start)).assertDoesNotExist()
        composeTestRule.runOnIdle { prompt.value = prompt.value!!.copy(recovery = NotificationRecovery.SEARCHING) }
        restore.assertIsNotEnabled()
        composeTestRule.onNodeWithText(targetString(R.string.pairing_restore_searching)).assertExists()
        composeTestRule.runOnIdle { prompt.value = prompt.value!!.copy(recovery = NotificationRecovery.CODE_AVAILABLE) }
        restore.assertIsNotEnabled()
        composeTestRule.onNodeWithText(targetString(R.string.pairing_restore_code_available)).assertExists()
        composeTestRule.runOnIdle { prompt.value = prompt.value!!.copy(recovery = NotificationRecovery.IDLE) }
        restore.assertIsEnabled()
        composeTestRule.runOnIdle { prompt.value = prompt.value!!.copy(submitting = true) }
        restore.assertDoesNotExist()
        composeTestRule.onNode(hasText(targetString(R.string.home_busy)) and hasClickAction()).assertIsNotEnabled()
        composeTestRule.runOnIdle { prompt.value = null }
        restore.assertDoesNotExist()
        composeTestRule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun pendingRestoreShowsRestoreAsUnfinishedWhileCameraIsMuted() {
        showHome(MainUiState(
            isDeveloperOptionsEnabled = true,
            isWirelessDebuggingEnabled = true,
            hasCscPermission = true,
            isCscMuted = true,
            pendingPairingTarget = false
        ))
        composeTestRule.onNodeWithText(targetString(R.string.settings_restore)).assertExists()
        composeTestRule.onNodeWithText(targetString(R.string.setup_apply_camera)).assertDoesNotExist()
        composeTestRule.onNodeWithText(targetString(R.string.setup_apply_automatic)).assertExists()
    }

    @Test fun unreadableWirelessStateDoesNotAskToTurnItOnAgain() {
        showHome(MainUiState(
            isCscMuted = false,
            hasCscPermission = true,
            isWirelessDebuggingEnabled = null
        ))
        composeTestRule.onNodeWithText(targetString(R.string.main_reenable_wireless_debugging)).assertDoesNotExist()
        composeTestRule.onNodeWithText(targetString(R.string.main_open_wireless_debugging_settings)).assertHasClickAction()
        composeTestRule.onNodeWithText(
            targetString(R.string.wireless_debugging_state_unknown_guidance)
        ).assertExists()
    }

    @Test fun pendingCameraChangeNeverShowsCompletionOrAllowsAnotherAction() {
        showHome(MainUiState(
            isCscMuted = true,
            hasCscPermission = true,
            isCscChangeInProgress = true
        ))
        composeTestRule.onNodeWithText(targetString(R.string.home_applied)).assertDoesNotExist()
        composeTestRule.onNodeWithText(targetString(R.string.home_open_camera)).assertDoesNotExist()
        composeTestRule.onNode(hasText(targetString(R.string.camera_settings_applying)) and hasClickAction()).assertIsNotEnabled()
    }


    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun showHome(uiState: MainUiState) {
        composeTestRule.setContent {
            HomeContent(uiState = uiState)
        }
    }

    @Test
    fun setupRequiredHome_focusesOnCurrentAction() {
        showHome(MainUiState(isWirelessDebuggingEnabled = false))

        composeTestRule.onNodeWithText(targetString(R.string.app_name)).fetchSemanticsNode()
        composeTestRule.onNodeWithText(targetString(R.string.home_first_setup)).fetchSemanticsNode()
        composeTestRule.onNodeWithText(targetString(R.string.home_start)).fetchSemanticsNode()
        composeTestRule.onNodeWithText(targetString(R.string.pairing_title_setup_in_progress)).fetchSemanticsNode()
        composeTestRule.onNodeWithText(targetString(R.string.setup_enable_developer)).fetchSemanticsNode()
        composeTestRule.onNodeWithText(targetString(R.string.setup_open_device_info)).assertHasClickAction()
        composeTestRule.onNodeWithText(targetString(R.string.wireless_enable)).fetchSemanticsNode()
        composeTestRule.onNodeWithText(targetString(R.string.pairing_title_code_entry)).fetchSemanticsNode()
        composeTestRule.onNodeWithText(targetString(R.string.setup_apply_camera)).fetchSemanticsNode()
        assertTrue(
            composeTestRule.onAllNodesWithText(targetString(R.string.setup_open_popup)).fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun developerOptionsStep_opensDeviceInfo() {
        var opened = false
        composeTestRule.setContent {
            HomeContent(
                uiState = MainUiState(),
                onOpenSoftwareInfo = { opened = true }
            )
        }

        composeTestRule.onNodeWithText(targetString(R.string.setup_open_device_info)).performScrollTo().performClick()
        assertTrue(opened)
    }

    @Test
    fun developerOptionsStep_isMarkedCompleteWhenEnabled() {
        showHome(MainUiState(isDeveloperOptionsEnabled = true))

        composeTestRule.onNodeWithText(targetString(R.string.setup_enable_developer)).fetchSemanticsNode()
        assertTrue(
            composeTestRule.onAllNodesWithText(targetString(R.string.setup_open_device_info)).fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun readyHome_keepsCameraCheckAsSecondaryAction() {
        showHome(
            MainUiState(
                isCscMuted = true,
                hasCscPermission = true
            )
        )

        composeTestRule.onNodeWithText(targetString(R.string.home_verified)).fetchSemanticsNode()
        composeTestRule.onNodeWithText(targetString(R.string.home_applied)).fetchSemanticsNode()
        composeTestRule.onNodeWithText(targetString(R.string.home_open_camera)).fetchSemanticsNode()
        composeTestRule.onNodeWithText(targetString(R.string.home_ready_description)).assertExists()
        assertTrue(
            composeTestRule.onAllNodesWithText(targetString(R.string.home_reapply)).fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            composeTestRule.onAllNodesWithText(targetString(R.string.home_start)).fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            composeTestRule.onAllNodesWithText(targetString(R.string.main_reenable_wireless_debugging)).fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            composeTestRule.onAllNodesWithText(targetString(R.string.setup_open_popup)).fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun reapplyHomeOpensWirelessDebuggingWithoutStartingPairingAgain() {
        var opened = false
        var pairingStarted = false
        composeTestRule.setContent {
            HomeContent(
                uiState = MainUiState(isCscMuted = false, hasCscPermission = true, isWirelessDebuggingEnabled = false),
                onSetup = { pairingStarted = true },
                onOpenWirelessDebugging = { opened = true }
            )
        }

        composeTestRule.onNodeWithText(targetString(R.string.home_action_needed)).fetchSemanticsNode()
        composeTestRule.onNodeWithText(targetString(R.string.home_reapply_needed)).fetchSemanticsNode()
        composeTestRule.onNodeWithText(targetString(R.string.main_reenable_wireless_debugging)).performScrollTo().performClick()
        assertTrue(opened)
        assertTrue(!pairingStarted)
        assertEquals(
            1,
            composeTestRule.onAllNodesWithText(targetString(R.string.home_reapply)).fetchSemanticsNodes().size
        )
        assertTrue(
            composeTestRule.onAllNodesWithText("복구 안내").fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun pairingFailure_isShownInlineAtCodeStep() {
        showHome(
            MainUiState(
                isDeveloperOptionsEnabled = true,
                isWirelessDebuggingEnabled = true,
                setupIssue = SetupIssue.PAIRING_CODE
            )
        )

        composeTestRule.onNodeWithText(targetString(R.string.home_check_needed)).fetchSemanticsNode()
        composeTestRule.onNode(hasText(targetString(R.string.home_restart_setup)) and !hasClickAction()).assertExists()
        composeTestRule.onNode(hasText(targetString(R.string.home_restart)) and hasClickAction()).assertIsEnabled()
        composeTestRule.onNodeWithText(
            targetString(R.string.step_code_invalid)
        ).fetchSemanticsNode()
        composeTestRule.onNodeWithText(targetString(R.string.setup_open_popup)).assertHasClickAction()
    }

    @Test
    fun readyHomeHidesWirelessDebuggingSettingsActionWhenEnabled() {
        showHome(
            MainUiState(
                isCscMuted = true,
                hasCscPermission = true,
                isWirelessDebuggingEnabled = true
            )
        )

        assertTrue(
            composeTestRule.onAllNodesWithText(targetString(R.string.main_open_wireless_debugging_settings)).fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun unreadableCscStateShowsRetryWithoutClaimingSuccessOrReset() {
        var retried = false
        composeTestRule.setContent {
            HomeContent(
                uiState = MainUiState(hasCscPermission = true, isCscMuted = null),
                onRefreshCscState = { retried = true }
            )
        }

        composeTestRule.onNodeWithText(targetString(R.string.csc_state_unknown_title)).fetchSemanticsNode()
        composeTestRule.onNodeWithText(targetString(R.string.csc_state_retry)).performClick()
        assertTrue(retried)
        for (claim in listOf(targetString(R.string.home_verified), targetString(R.string.home_applied), targetString(R.string.home_reapply_needed), targetString(R.string.home_reapply))) {
            assertTrue(composeTestRule.onAllNodesWithText(claim).fetchSemanticsNodes().isEmpty())
        }
    }

    @Test
    fun reapplyInProgressDoesNotShowPrematureSuccessOrAllowRepeatedRequests() {
        showHome(MainUiState(hasCscPermission = true, isCscChangeInProgress = true, isWirelessDebuggingEnabled = false))
        composeTestRule.onNode(hasText(targetString(R.string.camera_settings_applying)) and hasClickAction()).assertIsNotEnabled()
        composeTestRule.onNodeWithText(targetString(R.string.main_reenable_wireless_debugging)).assertIsNotEnabled()
        assertTrue(
            composeTestRule.onAllNodesWithText(targetString(R.string.home_applied)).fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun discoveryFailure_showsWirelessDebuggingRecovery() {
        showHome(MainUiState(
            isDeveloperOptionsEnabled = true,
            setupIssue = SetupIssue.PAIRING_DISCOVERY
        ))

        composeTestRule.onNodeWithText(
            targetString(R.string.step_discovery_failed)
        ).fetchSemanticsNode()
    }

    @Test
    fun connectionFailure_explainsChangedPairingPort() {
        showHome(MainUiState(
            isDeveloperOptionsEnabled = true,
            setupIssue = SetupIssue.PAIRING_CONNECTION
        ))

        composeTestRule.onNodeWithText(
            targetString(R.string.step_connection_failed)
        ).fetchSemanticsNode()
    }

    @Test
    fun localNetworkPermissionFailure_opensAppSettings() {
        var opened = false
        composeTestRule.setContent {
            HomeContent(
                uiState = MainUiState(setupIssue = SetupIssue.LOCAL_NETWORK_PERMISSION),
                onOpenAppSettings = { opened = true }
            )
        }

        composeTestRule.onNodeWithText(
            targetString(R.string.setup_allow_network)
        ).fetchSemanticsNode()
        composeTestRule.onNodeWithText(targetString(R.string.setup_permissions_open)).performScrollTo().performClick()
        assertTrue(opened)
    }

    @Test
    fun cameraApplyFailure_isShownInlineAtApplyStep() {
        showHome(
            MainUiState(
                isDeveloperOptionsEnabled = true,
                setupIssue = SetupIssue.CAMERA_APPLY
            )
        )

        composeTestRule.onNodeWithText(
            targetString(R.string.setup_code_accepted)
        ).fetchSemanticsNode()
        assertTrue(
            composeTestRule.onAllNodesWithText(targetString(R.string.setup_open_popup)).fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun home_doesNotShowDetailedLegalNotice() {
        showHome(MainUiState())

        assertTrue(
            composeTestRule
                .onAllNodesWithText("주의사항 및 법적 고지")
                .fetchSemanticsNodes()
                .isEmpty()
        )
    }

    @Test
    fun ringerMutedVolumeExplainsRestrictionAndOpensPhoneSettings() {
        var opened = false
        composeTestRule.setContent {
            HomeContent(
                uiState = MainUiState(systemVolume = SystemVolumeUiState(current = 0, max = 15, isRingerMuted = true)),
                onOpenSoundSettings = { opened = true }
            )
        }

        composeTestRule.onNodeWithContentDescription(targetString(R.string.volume_slider)).performScrollTo().assertIsNotEnabled()
        composeTestRule.onNodeWithText(
            targetString(R.string.system_volume_ringer_muted)
        ).fetchSemanticsNode()
        composeTestRule.onNodeWithText(targetString(R.string.system_volume_open_settings)).performScrollTo().performClick()
        assertTrue(opened)
    }

    @Test
    fun zeroVolumeInSoundModeCanStillBeRaised() {
        val requests = mutableListOf<Int>()
        composeTestRule.setContent {
            HomeContent(
                uiState = MainUiState(systemVolume = SystemVolumeUiState(current = 0, max = 15, isStreamMuted = true)),
                onSystemVolumeChange = { index ->
                    requests.add(index)
                    index
                }
            )
        }
        composeTestRule.onNodeWithContentDescription(targetString(R.string.volume_slider)).performScrollTo().assertIsEnabled()
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(4f) }
        composeTestRule.runOnIdle { assertEquals(listOf(4), requests) }
    }

    @Test
    fun pendingVolumeChangeKeepsRequestedValueWithoutReportingCompletion() {
        showHome(MainUiState(systemVolume = SystemVolumeUiState(current = 2, max = 15, requested = 5)))

        composeTestRule.onNodeWithContentDescription(targetString(R.string.volume_slider)).performScrollTo().assertIsNotEnabled()
        composeTestRule.onNodeWithText("5 / 15").fetchSemanticsNode()
        composeTestRule.onNodeWithText(targetString(R.string.system_volume_applying)).fetchSemanticsNode()
    }

    @Test
    fun rejectedVolumeChangeOffersNativeSettings() {
        showHome(MainUiState(systemVolume = SystemVolumeUiState(
            current = 2,
            max = 15,
            error = R.string.system_volume_not_applied
        )))

        composeTestRule.onNodeWithText(targetString(R.string.system_volume_open_settings)).performScrollTo().assertHasClickAction()
        composeTestRule.onNodeWithText("2 / 15").fetchSemanticsNode()
    }
}
