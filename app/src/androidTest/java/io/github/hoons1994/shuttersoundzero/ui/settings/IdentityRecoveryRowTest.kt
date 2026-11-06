package io.github.hoons1994.shuttersoundzero.ui.settings

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
        compose.onNodeWithText("연결 정보 재설정").performClick()
        compose.runOnIdle { assertEquals(0, requests) }
        compose.onNodeWithText("취소").performClick()
        compose.runOnIdle { assertEquals(0, requests) }
        compose.onNodeWithText("연결 정보 재설정").performClick()
        compose.onNodeWithText("재설정 후 다시 연결").performClick()
        compose.runOnIdle { assertEquals(1, requests) }
        compose.onNodeWithText("연결 정보를 새로 만들까요?").assertDoesNotExist()
    }
}
