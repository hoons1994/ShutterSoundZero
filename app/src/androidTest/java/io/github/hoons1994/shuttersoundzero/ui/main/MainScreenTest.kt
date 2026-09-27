package io.github.hoons1994.shuttersoundzero.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.hoons1994.shuttersoundzero.data.SetupIssue
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

        composeTestRule.onNodeWithText("셔터사운드 제로").fetchSemanticsNode()
        composeTestRule.onNodeWithText("처음 한 번만 설정해 주세요").fetchSemanticsNode()
        composeTestRule.onNodeWithText("1회 설정 시작").fetchSemanticsNode()
        composeTestRule.onNodeWithText("1회 설정 진행").fetchSemanticsNode()
        composeTestRule.onNodeWithText("개발자 옵션 켜기").fetchSemanticsNode()
        composeTestRule.onNodeWithText("휴대전화 정보 열기").assertHasClickAction()
        composeTestRule.onNodeWithText("무선 디버깅 켜기").fetchSemanticsNode()
        composeTestRule.onNodeWithText("6자리 코드 입력").fetchSemanticsNode()
        composeTestRule.onNodeWithText("카메라 무음 적용").fetchSemanticsNode()
        assertTrue(
            composeTestRule.onAllNodesWithText("알림 팝업 설정 열기").fetchSemanticsNodes().isEmpty()
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

        composeTestRule.onNodeWithText("휴대전화 정보 열기").performScrollTo().performClick()
        assertTrue(opened)
    }

    @Test
    fun developerOptionsStep_isMarkedCompleteWhenEnabled() {
        showHome(MainUiState(isDeveloperOptionsEnabled = true))

        composeTestRule.onNodeWithText("개발자 옵션 켜기").fetchSemanticsNode()
        assertTrue(
            composeTestRule.onAllNodesWithText("휴대전화 정보 열기").fetchSemanticsNodes().isEmpty()
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

        composeTestRule.onNodeWithText("정상").fetchSemanticsNode()
        composeTestRule.onNodeWithText("카메라 무음 설정 완료").fetchSemanticsNode()
        composeTestRule.onNodeWithText("카메라 열어보기").fetchSemanticsNode()
        assertTrue(
            composeTestRule.onAllNodesWithText("다시 적용하기").fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            composeTestRule.onAllNodesWithText("1회 설정 시작").fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            composeTestRule.onAllNodesWithText("알림 팝업 설정 열기").fetchSemanticsNodes().isEmpty()
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
                isDeveloperOptionsEnabled = true,
                isWirelessDebuggingEnabled = true,
                setupIssue = SetupIssue.PAIRING_CODE
            )
        )

        composeTestRule.onNodeWithText("확인 필요").fetchSemanticsNode()
        composeTestRule.onNodeWithText("1회 설정을 다시 진행해 주세요").fetchSemanticsNode()
        composeTestRule.onNodeWithText("1회 설정 다시 시작").fetchSemanticsNode()
        composeTestRule.onNodeWithText(
            "숫자 6자리를 정확히 입력해 주세요. 연결 화면의 코드가 바뀌었다면 새 코드를 사용해 주세요."
        ).fetchSemanticsNode()
        composeTestRule.onNodeWithText("알림 팝업 설정 열기").assertHasClickAction()
    }

    @Test
    fun discoveryFailure_showsWirelessDebuggingRecovery() {
        showHome(MainUiState(
            isDeveloperOptionsEnabled = true,
            setupIssue = SetupIssue.PAIRING_DISCOVERY
        ))

        composeTestRule.onNodeWithText(
            "페어링 연결 화면을 찾지 못했습니다. Wi-Fi와 무선 디버깅을 확인하고 [페어링 코드로 기기 페어링] 화면을 다시 열어 둔 채 시도해 주세요."
        ).fetchSemanticsNode()
    }

    @Test
    fun connectionFailure_explainsChangedPairingPort() {
        showHome(MainUiState(
            isDeveloperOptionsEnabled = true,
            setupIssue = SetupIssue.PAIRING_CONNECTION
        ))

        composeTestRule.onNodeWithText(
            "기기에 연결하지 못했습니다. 페어링 창이 닫혔거나 연결 정보가 바뀌었을 수 있습니다. 창을 다시 열고 새 6자리 코드로 시도해 주세요."
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
            "Android 17에서 기기를 찾으려면 로컬 네트워크 권한이 필요합니다. 앱 설정에서 권한을 허용한 뒤 1회 설정을 다시 시작해 주세요."
        ).fetchSemanticsNode()
        composeTestRule.onNodeWithText("앱 권한 설정 열기").performScrollTo().performClick()
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
            "기기 연결은 됐지만 카메라 설정을 적용하지 못했습니다. 무선 디버깅을 켠 상태에서 다시 시도해 주세요."
        ).fetchSemanticsNode()
        assertTrue(
            composeTestRule.onAllNodesWithText("알림 팝업 설정 열기").fetchSemanticsNodes().isEmpty()
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
}
