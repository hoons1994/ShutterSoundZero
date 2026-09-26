package io.github.hoons1994.shuttersoundzero.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI smoke tests for the purpose-based settings structure. */
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setup() {
        composeTestRule.setContent {
            SettingsScreen(onBackClick = {})
        }
    }

    @Test
    fun settings_areGroupedByUserGoal() {
        composeTestRule.onNodeWithText("카메라 설정").fetchSemanticsNode()
        composeTestRule.onNodeWithText("앱 동작").fetchSemanticsNode()
        composeTestRule.onNodeWithText("업데이트").fetchSemanticsNode()
        composeTestRule.onNodeWithText("도움말 및 지원").fetchSemanticsNode()
        composeTestRule.onNodeWithText("정보").fetchSemanticsNode()
    }

    @Test
    fun detailedPopupSetting_isAvailableFromHelpSection() {
        composeTestRule.onNodeWithText("알림 팝업 자세히 보기")
            .assertHasClickAction()
    }

    @Test
    fun developerLinks_areAvailableFromHelpSection() {
        composeTestRule.onNodeWithText("사용 문의")
            .performScrollTo()
            .assertHasClickAction()
        composeTestRule.onNodeWithText("카카오톡 오픈채팅으로 개발자에게 문의하기")
            .fetchSemanticsNode()
        composeTestRule.onNodeWithText("개발자 후원")
            .performScrollTo()
            .assertHasClickAction()
        composeTestRule.onNodeWithText("앱이 도움이 되었다면 개발과 유지보수를 응원할 수 있습니다.")
            .fetchSemanticsNode()
    }

    @Test
    fun deviceReportEntry_isNotAvailable() {
        composeTestRule.onNodeWithText("호환성 데이터 제출")
            .assertDoesNotExist()
        composeTestRule.onNodeWithText("호환성 데이터를 제출하시겠습니까?")
            .assertDoesNotExist()
    }

    @Test
    fun errorReporting_isStillAvailable() {
        composeTestRule.onNodeWithText("문제가 해결되지 않나요?")
            .performScrollTo()
            .assertHasClickAction()
            .performClick()

        composeTestRule.onNodeWithText("오류 신고").assertExists()
        composeTestRule.onNodeWithText("전송될 진단 정보").assertExists()
        composeTestRule.onNodeWithText("이메일로 보내기").assertHasClickAction()
        composeTestRule.onNodeWithText("닫기").performClick()
        composeTestRule.onNodeWithText("오류 신고").assertDoesNotExist()
    }
}
