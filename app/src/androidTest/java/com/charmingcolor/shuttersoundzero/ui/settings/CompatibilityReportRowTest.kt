package com.charmingcolor.shuttersoundzero.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.MailTo
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso.pressBack
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Intercept email intents so neither consent tests nor failures send real reports. */
class CompatibilityReportRowTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var recordingContext: RecordingContext

    @Before
    fun setup() {
        recordingContext = RecordingContext(composeTestRule.activity)
        composeTestRule.setContent {
            CompositionLocalProvider(LocalContext provides recordingContext) {
                MaterialTheme { CompatibilityReportRow() }
            }
        }
    }

    @Test
    fun no_dismissesPreviewWithoutLaunchingAnExternalApp() {
        showPreview()
        composeTestRule.onNodeWithText("받는 사람: hoons1994@naver.com").fetchSemanticsNode()
        composeTestRule.runOnIdle { assertTrue(recordingContext.intents.isEmpty()) }
        composeTestRule.onNodeWithText("아니오").performClick()
        composeTestRule.onNodeWithText("호환성 데이터를 제출하시겠습니까?").assertDoesNotExist()
        composeTestRule.runOnIdle { assertTrue(recordingContext.intents.isEmpty()) }
    }

    @Test
    fun back_dismissesPreviewWithoutLaunchingAnExternalApp() {
        showPreview()
        pressBack()
        composeTestRule.onNodeWithText("호환성 데이터를 제출하시겠습니까?").assertDoesNotExist()
        composeTestRule.runOnIdle { assertTrue(recordingContext.intents.isEmpty()) }
    }

    @Test
    fun yes_opensExactlyThePreviewedDraftWithoutClaimingItWasSent() {
        showPreview()
        val shownSubject = nodeText("제목: [ShutterSoundZero").removePrefix("제목: ")
        val shownBody = nodeText("[호환성 정보]")
        composeTestRule.onNodeWithText("예").performClick()
        composeTestRule.runOnIdle {
            assertEquals(1, recordingContext.intents.size)
            val intent = recordingContext.intents.single()
            val email = MailTo.parse(requireNotNull(intent.data).toString())
            assertEquals(Intent.ACTION_SENDTO, intent.action)
            assertEquals(0, intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
            assertEquals("hoons1994@naver.com", email.to)
            assertEquals(setOf("to"), email.headers.keys)
            assertArrayEquals(arrayOf("hoons1994@naver.com"), intent.getStringArrayExtra(Intent.EXTRA_EMAIL))
            assertEquals(shownSubject, intent.getStringExtra(Intent.EXTRA_SUBJECT))
            assertEquals(shownBody, intent.getStringExtra(Intent.EXTRA_TEXT))
        }
        composeTestRule.onNodeWithText("호환성 데이터를 제출하시겠습니까?").assertDoesNotExist()
        composeTestRule.onNodeWithText("제출되었습니다", substring = true).assertDoesNotExist()
    }

    @Test
    fun missingEmailApp_keepsDraftAndAllowsCancellation() {
        composeTestRule.runOnIdle { recordingContext.failure = ActivityNotFoundException() }
        showPreview()
        composeTestRule.onNodeWithText("예").performClick()
        composeTestRule.onNodeWithText("이메일을 보낼 수 있는 앱을 찾지 못했습니다.", substring = true)
            .performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("호환성 데이터를 제출하시겠습니까?").assertIsDisplayed()
        composeTestRule.onNodeWithText("아니오").performClick()
        composeTestRule.runOnIdle { assertEquals(1, recordingContext.intents.size) }
    }

    @Test
    fun blockedEmailApp_keepsTheSameDraftForAnExplicitRetry() {
        composeTestRule.runOnIdle { recordingContext.failure = SecurityException("blocked") }
        showPreview()
        composeTestRule.onNodeWithText("예").performClick()
        composeTestRule.onNodeWithText("기기 정책으로 이메일 작성 화면을 열 수 없습니다.", substring = true)
            .performScrollTo().assertIsDisplayed()
        composeTestRule.runOnIdle { recordingContext.failure = null }
        composeTestRule.onNodeWithText("예").performClick()
        composeTestRule.runOnIdle {
            assertEquals(2, recordingContext.intents.size)
            val first = recordingContext.intents[0]
            val retry = recordingContext.intents[1]
            assertEquals(first.data, retry.data)
            assertEquals(first.getStringExtra(Intent.EXTRA_SUBJECT), retry.getStringExtra(Intent.EXTRA_SUBJECT))
            assertEquals(first.getStringExtra(Intent.EXTRA_TEXT), retry.getStringExtra(Intent.EXTRA_TEXT))
            assertArrayEquals(first.getStringArrayExtra(Intent.EXTRA_EMAIL), retry.getStringArrayExtra(Intent.EXTRA_EMAIL))
        }
        composeTestRule.onNodeWithText("호환성 데이터를 제출하시겠습니까?").assertDoesNotExist()
    }

    private fun showPreview() {
        composeTestRule.onNodeWithText("호환성 데이터 제출").performClick()
        composeTestRule.onNodeWithText("호환성 데이터를 제출하시겠습니까?").assertIsDisplayed()
    }

    private fun nodeText(part: String): String = composeTestRule
        .onNodeWithText(part, substring = true)
        .fetchSemanticsNode().config[SemanticsProperties.Text].single().text

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        val intents = mutableListOf<Intent>()
        var failure: RuntimeException? = null

        override fun startActivity(intent: Intent) {
            intents += Intent(intent)
            failure?.let { throw it }
        }
    }
}
