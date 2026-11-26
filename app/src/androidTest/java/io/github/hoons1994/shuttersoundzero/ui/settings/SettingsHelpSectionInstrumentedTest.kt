package io.github.hoons1994.shuttersoundzero.ui.settings

import io.github.hoons1994.shuttersoundzero.testing.targetString
import io.github.hoons1994.shuttersoundzero.R
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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

    @Test fun actualReportAndPrivacyNoticeAreReviewedBeforeOpeningUnchangedEmailDraft() {
        val opened = mutableListOf<android.content.Intent>()
        var builds = 0
        val capturingContext = object : android.content.ContextWrapper(composeTestRule.activity) {
            override fun startActivity(intent: android.content.Intent) { opened += intent }
        }
        composeTestRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalContext provides capturingContext
            ) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SettingsHelpSection(buildDiagnosticReport = {
                        builds++
                        DiagnosticReportBuilder.build(it, { Result.failure(IOException("private error")) }, {
                            java.time.ZonedDateTime.parse("2026-11-01T21:30:15+09:00[Asia/Seoul]")
                        })
                    })
                }
            }
        }
        openReport()
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithText(targetString(R.string.diag_read_failed), substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(targetString(R.string.report_privacy)).assertExists()
        composeTestRule.onNode(hasSetTextAction()).performTextInput("한글 & + ? # % =\n다음 줄")
        composeTestRule.runOnIdle { assertEquals(0, opened.size) }
        composeTestRule.onNodeWithText(targetString(R.string.report_send)).performClick()
        composeTestRule.runOnIdle {
            assertEquals(1, builds)
            assertEquals(1, opened.size)
            assertEquals(android.content.Intent.ACTION_CHOOSER, opened.single().action)
            @Suppress("DEPRECATION")
            val draft = opened.single().getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)!!
            val query = draft.data!!.encodedSchemeSpecificPart.substringAfter('?').split('&').associate {
                val pair = it.split('=', limit = 2)
                android.net.Uri.decode(pair[0]) to android.net.Uri.decode(pair[1])
            }
            org.junit.Assert.assertTrue(query.getValue("body").contains("한글 & + ? # % =\n다음 줄"))
            org.junit.Assert.assertTrue(query.getValue("body").contains("2026-11-01T21:30:15+09:00 [Asia/Seoul]"))
            org.junit.Assert.assertTrue(query.getValue("body").contains(targetString(R.string.diag_read_failed)))
        }
    }

    private fun openReport() {
        composeTestRule.onNodeWithText(targetString(R.string.support_unresolved)).performScrollTo().performClick()
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
        composeTestRule.onNodeWithText(targetString(R.string.report_loading)).assertExists()
        composeTestRule.onNodeWithText(targetString(R.string.support_contact)).performScrollTo().assertIsEnabled()
        composeTestRule.runOnIdle { assertEquals(1, builds) }
        composeTestRule.runOnIdle {
            report.complete(DiagnosticReportBuilder.Report("제목", "합성 진단 정보"))
        }
        waitForText("합성 진단 정보")
        composeTestRule.onNodeWithText(targetString(R.string.report_send)).assertIsEnabled()
    }

    @Test fun slowClearDisablesSendingUntilRefreshedAndPreservesUserInput() {
        val clearStarted = CompletableDeferred<Unit>()
        val clearMayFinish = CompletableDeferred<Unit>()
        var builds = 0
        val restoration = StateRestorationTester(composeTestRule)
        restoration.setContent {
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
        composeTestRule.onNodeWithText(targetString(R.string.report_delete)).performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 10000) { clearStarted.isCompleted }
        restoration.emulateSavedInstanceStateRestore()
        composeTestRule.onNodeWithText(targetString(R.string.report_deleting)).assertIsNotEnabled()
        composeTestRule.onNodeWithText(targetString(R.string.report_send)).assertIsNotEnabled()
        composeTestRule.onNodeWithText(targetString(R.string.camera_routine_close)).assertIsEnabled()

        composeTestRule.runOnIdle { clearMayFinish.complete(Unit) }
        waitForText("새 진단 정보")
        composeTestRule.onNode(hasSetTextAction()).assertTextContains("설명을 보존합니다")
        composeTestRule.onNodeWithText(targetString(R.string.report_send)).assertIsEnabled()
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
        composeTestRule.onNodeWithText(targetString(R.string.report_delete)).performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 10000) { clearStarted.isCompleted }
        composeTestRule.onNodeWithText(targetString(R.string.camera_routine_close)).performClick()
        composeTestRule.onNodeWithText(targetString(R.string.report_error)).assertDoesNotExist()
        composeTestRule.runOnIdle { clearMayFinish.complete(Unit) }
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithText(targetString(R.string.support_unresolved)).fetchSemanticsNodes()
                .any { !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) }
        }
        composeTestRule.onNodeWithText(targetString(R.string.report_error)).assertDoesNotExist()
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
        composeTestRule.onNodeWithText(targetString(R.string.report_delete)).performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithText(targetString(R.string.report_error)).fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithText(targetString(R.string.report_send)).assertDoesNotExist()
    }
}
