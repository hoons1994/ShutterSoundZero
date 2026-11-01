package io.github.hoons1994.shuttersoundzero.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import io.github.hoons1994.shuttersoundzero.diagnostics.DiagnosticReportBuilder
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Slow and failed I/O are simulated; these tests never delete a device's diagnostic log. */
class SettingsHelpSectionInstrumentedTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun openReport() {
        composeTestRule.onNodeWithText("문제가 해결되지 않나요?").performScrollTo().performClick()
    }

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun slowReportLoadingLeavesTheSettingsScreenResponsive() {
        val report = CompletableDeferred<DiagnosticReportBuilder.Report>()
        var builds = 0
        composeTestRule.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SettingsHelpSection(
                    buildDiagnosticReport = { builds++; report.await() },
                    clearDiagnosticLog = { Result.success(false) }
                )
            }
        }
        openReport()
        composeTestRule.onNodeWithText("진단 정보를 불러오는 중입니다.").assertExists()
        composeTestRule.onNodeWithText("사용 문의").performScrollTo().assertIsEnabled()
        composeTestRule.runOnIdle { assertEquals(1, builds) }
        report.complete(DiagnosticReportBuilder.Report("제목", "합성 진단 정보"))
        waitForText("합성 진단 정보")
        composeTestRule.onNodeWithText("이메일로 보내기").assertIsEnabled()
    }

    @Test fun slowClearDisablesSendingUntilRefreshedAndPreservesUserInput() {
        val clearStarted = CompletableDeferred<Unit>()
        val clearMayFinish = CompletableDeferred<Unit>()
        var builds = 0
        composeTestRule.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SettingsHelpSection(
                    buildDiagnosticReport = {
                        builds++
                        DiagnosticReportBuilder.Report("제목", if (builds == 1) "이전 진단 정보" else "새 진단 정보")
                    },
                    clearDiagnosticLog = {
                        clearStarted.complete(Unit)
                        clearMayFinish.await()
                        Result.success(true)
                    }
                )
            }
        }
        openReport()
        waitForText("이전 진단 정보")
        composeTestRule.onNode(hasSetTextAction()).performTextInput("설명을 보존합니다")
        composeTestRule.onNodeWithText("진단 로그 지우기").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 10000) { clearStarted.isCompleted }
        composeTestRule.onNodeWithText("진단 로그를 지우는 중...").assertIsNotEnabled()
        composeTestRule.onNodeWithText("이메일로 보내기").assertIsNotEnabled()
        composeTestRule.onNodeWithText("닫기").assertIsEnabled()

        clearMayFinish.complete(Unit)
        waitForText("새 진단 정보")
        composeTestRule.onNode(hasSetTextAction()).assertTextContains("설명을 보존합니다")
        composeTestRule.onNodeWithText("이메일로 보내기").assertIsEnabled()
        composeTestRule.runOnIdle { assertEquals(2, builds) }
    }

    @Test fun closingDuringClearDoesNotReopenAnOldReport() {
        val clearStarted = CompletableDeferred<Unit>()
        val clearMayFinish = CompletableDeferred<Unit>()
        composeTestRule.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SettingsHelpSection(
                    buildDiagnosticReport = { DiagnosticReportBuilder.Report("제목", "이전 진단 정보") },
                    clearDiagnosticLog = {
                        clearStarted.complete(Unit)
                        clearMayFinish.await()
                        Result.success(true)
                    }
                )
            }
        }
        openReport()
        waitForText("이전 진단 정보")
        composeTestRule.onNodeWithText("진단 로그 지우기").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 10000) { clearStarted.isCompleted }
        composeTestRule.onNodeWithText("닫기").performClick()
        composeTestRule.onNodeWithText("오류 신고").assertDoesNotExist()
        clearMayFinish.complete(Unit)
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithText("문제가 해결되지 않나요?").fetchSemanticsNodes()
                .any { !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) }
        }
        composeTestRule.onNodeWithText("오류 신고").assertDoesNotExist()
    }

    @Test fun refreshFailureAfterClearCannotLeaveOldLogAvailableForSending() {
        var builds = 0
        composeTestRule.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SettingsHelpSection(
                    buildDiagnosticReport = {
                        if (++builds == 1) DiagnosticReportBuilder.Report("제목", "이전 진단 정보")
                        else throw IOException("injected read failure")
                    },
                    clearDiagnosticLog = { Result.success(true) }
                )
            }
        }
        openReport()
        waitForText("이전 진단 정보")
        composeTestRule.onNodeWithText("진단 로그 지우기").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithText("오류 신고").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithText("이메일로 보내기").assertDoesNotExist()
    }
}
