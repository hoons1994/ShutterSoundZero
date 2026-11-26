package io.github.hoons1994.shuttersoundzero.ui.settings

import io.github.hoons1994.shuttersoundzero.testing.targetString
import io.github.hoons1994.shuttersoundzero.R
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class IdentityRecoveryRowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun resetRequiresConfirmationAndCancellationDoesNotRequestRecovery() {
        var requests = 0
        compose.setContent { IdentityRecoveryRow(enabled = true) { requests++ } }
        compose.onNodeWithText(targetString(R.string.recovery_reset)).performClick()
        compose.runOnIdle { assertEquals(0, requests) }
        compose.onNodeWithText(targetString(R.string.action_cancel)).performClick()
        compose.runOnIdle { assertEquals(0, requests) }
        compose.onNodeWithText(targetString(R.string.recovery_reset)).performClick()
        compose.onNodeWithText(targetString(R.string.recovery_reconnect_after_reset)).performClick()
        compose.runOnIdle { assertEquals(1, requests) }
        compose.onNodeWithText(targetString(R.string.recovery_confirm)).assertDoesNotExist()
    }
}
