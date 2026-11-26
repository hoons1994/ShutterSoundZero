package io.github.hoons1994.shuttersoundzero.ui.main

import io.github.hoons1994.shuttersoundzero.testing.targetString
import io.github.hoons1994.shuttersoundzero.R
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.hoons1994.shuttersoundzero.core.adb.CameraMuteFailure
import io.github.hoons1994.shuttersoundzero.ui.components.CameraMuteFailureDialog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CameraMuteFailureDialogTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun connectionFailureStartsPairingRecovery() {
        var dismissed = false
        var reconnected = false
        var openedSettings = false
        composeTestRule.setContent {
            CameraMuteFailureDialog(
                CameraMuteFailure.CONNECTION,
                onDismiss = { dismissed = true },
                onReconnect = { reconnected = true },
                onOpenSettings = { openedSettings = true }
            )
        }
        composeTestRule.onNodeWithText(targetString(R.string.recovery_reconnect)).performClick()
        assertTrue(dismissed)
        assertTrue(reconnected)
        assertFalse(openedSettings)
    }

    @Test fun discoveryFailureCanCheckSettingsBeforeRePairing() {
        var reconnected = false
        var openedSettings = false
        composeTestRule.setContent {
            CameraMuteFailureDialog(
                CameraMuteFailure.DISCOVERY,
                onDismiss = {},
                onReconnect = { reconnected = true },
                onOpenSettings = { openedSettings = true }
            )
        }
        composeTestRule.onNodeWithText(targetString(R.string.action_wireless_settings)).performClick()
        assertTrue(openedSettings)
        assertFalse(reconnected)
    }

    @Test fun settingFailureDoesNotRequestAnotherPairing() {
        composeTestRule.setContent {
            CameraMuteFailureDialog(CameraMuteFailure.APPLY, {}, {}, {})
        }
        assertTrue(composeTestRule.onAllNodesWithText(targetString(R.string.recovery_reconnect)).fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithText(targetString(R.string.action_wireless_settings)).fetchSemanticsNodes().isEmpty())
        composeTestRule.onNodeWithText(targetString(R.string.action_ok)).performClick()
    }
}
