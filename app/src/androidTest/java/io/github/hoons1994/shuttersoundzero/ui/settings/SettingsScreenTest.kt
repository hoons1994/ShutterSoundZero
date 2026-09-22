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
    fun compatibilityReport_requiresExplicitConfirmation() {
        composeTestRule.onNodeWithText("호환성 데이터 제출")
            .performScrollTo()
            .assertHasClickAction()
            .performClick()

        composeTestRule.onNodeWithText("호환성 데이터를 제출하시겠습니까?")
            .fetchSemanticsNode()
        composeTestRule.onNodeWithText("제출될 정보")
            .fetchSemanticsNode()
        composeTestRule.onNodeWithText("앱이 메일에 넣지 않는 정보")
            .fetchSemanticsNode()
        composeTestRule.onNodeWithText("받는 사람: hoons1994@naver.com")
            .fetchSemanticsNode()
        composeTestRule.onNodeWithText("이메일 작성 화면이 열리며", substring = true)
            .fetchSemanticsNode()
        composeTestRule.onNodeWithText("이메일 주소는 수신자에게 전달되며", substring = true)
            .fetchSemanticsNode()
        composeTestRule.onNodeWithText("예")
            .assertHasClickAction()
        composeTestRule.onNodeWithText("아니오")
            .assertHasClickAction()
    }
}
