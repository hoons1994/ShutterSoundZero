package com.charmingcolor.shuttersoundzero.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.charmingcolor.shuttersoundzero.data.SetupIssue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** UI smoke tests for the state-focused home flow. */
class MainScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun showHome(uiState: MainUiState) {
        composeTestRule.setContent {
            HomeContent(uiState = uiState)
        }
    }

    @Test
    fun setupRequiredHome_focusesOnCurrentAction() {
        showHome(MainUiState())

        composeTestRule.onNodeWithText("셔터음 제로").fetchSemanticsNode()
        composeTestRule.onNodeWithText("처음 한 번만 설정해 주세요").fetchSemanticsNode()
        composeTestRule.onNodeWithText("1회 설정 시작").fetchSemanticsNode()
        composeTestRule.onNodeWithText("1회 설정 진행").fetchSemanticsNode()
        composeTestRule.onNodeWithText("무선 디버깅 켜기").fetchSemanticsNode()
        composeTestRule.onNodeWithText("6자리 코드 입력").fetchSemanticsNode()
        composeTestRule.onNodeWithText("카메라 무음 적용").fetchSemanticsNode()
    }

    @Test
    fun readyHome_keepsCameraCheckAsSecondaryAction() {
        showHome(
            MainUiState(
                isCscMuted = true,
                hasCscPermission = true
            )
        )

        composeTestRule.onNodeWithText("정상").fetchSemanticsNode()
        composeTestRule.onNodeWithText("카메라 무음 설정 완료").fetchSemanticsNode()
        composeTestRule.onNodeWithText("카메라 열어보기").fetchSemanticsNode()
        assertTrue(
            composeTestRule.onAllNodesWithText("다시 적용하기").fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            composeTestRule.onAllNodesWithText("1회 설정 시작").fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun reapplyHome_showsOnlyOneRecoveryAction() {
        showHome(
            MainUiState(
                isCscMuted = false,
                hasCscPermission = true
            )
        )

        composeTestRule.onNodeWithText("조치 필요").fetchSemanticsNode()
        composeTestRule.onNodeWithText("카메라 무음 다시 적용 필요").fetchSemanticsNode()
        assertEquals(
            1,
            composeTestRule.onAllNodesWithText("다시 적용하기").fetchSemanticsNodes().size
        )
        assertTrue(
            composeTestRule.onAllNodesWithText("복구 안내").fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun pairingFailure_isShownInlineAtCodeStep() {
        showHome(
            MainUiState(
                isWirelessDebuggingEnabled = true,
                setupIssue = SetupIssue.PAIRING_CODE
            )
        )

        composeTestRule.onNodeWithText("확인 필요").fetchSemanticsNode()
        composeTestRule.onNodeWithText("1회 설정을 다시 진행해 주세요").fetchSemanticsNode()
        composeTestRule.onNodeWithText("1회 설정 다시 시작").fetchSemanticsNode()
        composeTestRule.onNodeWithText(
            "코드가 만료되었거나 일치하지 않았습니다. 새 6자리 코드를 확인해 다시 입력해 주세요."
        ).fetchSemanticsNode()
    }

    @Test
    fun cameraApplyFailure_isShownInlineAtApplyStep() {
        showHome(
            MainUiState(
                setupIssue = SetupIssue.CAMERA_APPLY
            )
        )

        composeTestRule.onNodeWithText(
            "기기 연결은 됐지만 카메라 설정을 적용하지 못했습니다. 무선 디버깅을 켠 상태에서 다시 시도해 주세요."
        ).fetchSemanticsNode()
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
}
