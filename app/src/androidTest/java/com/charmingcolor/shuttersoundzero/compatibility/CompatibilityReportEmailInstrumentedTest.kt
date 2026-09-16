package com.charmingcolor.shuttersoundzero.compatibility

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.MailTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** No real email client or network is used by these tests. */
@RunWith(AndroidJUnit4::class)
class CompatibilityReportEmailInstrumentedTest {
    private fun report() = CompatibilityReportBuilder.Report(
        manufacturer = "samsung",
        model = "TEST-MODEL",
        androidVersion = "16",
        sdkInt = 36,
        oneUiVersion = "알 수 없음",
        securityPatch = "2026-08-05",
        appVersion = "1.5.3",
        setupPermissionGranted = true,
        cameraMuteApplied = false
    )

    @Test
    fun draft_containsOnlyTheProvidedSnapshot() {
        val snapshot = report()
        val draft = CompatibilityReportEmail.prepare(snapshot)
        assertEquals(CompatibilityReportBuilder.buildEmailSubject(snapshot), draft.subject)
        assertEquals(
            "안녕하세요. ShutterSoundZero 호환성 정보를 제보합니다.\n\n" +
                "[호환성 정보]\n" +
                "제조사: samsung\n모델 번호: TEST-MODEL\n" +
                "Android 버전: 16 (SDK 36)\nOne UI 버전: 알 수 없음\n" +
                "보안 패치: 2026-08-05\n앱 버전: 1.5.3\n" +
                "1회 설정 권한: 있음\n현재 CSC 카메라 무음 설정: 미적용\n\n" +
                "위 정보는 앱의 호환성 데이터 제출 화면에서 확인 후 전송한 내용입니다.",
            draft.body
        )
        assertFalse(draft.body.contains("github.com"))
    }

    @Test
    fun intent_containsOnlyMailtoAndTheExactApprovedDraft() {
        val draft = CompatibilityReportEmail.prepare(report())
        val intent = CompatibilityReportEmail.createIntent(draft)
        val mail = MailTo.parse(requireNotNull(intent.data).toString())
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("mailto", intent.data?.scheme)
        assertEquals(CompatibilityReportEmail.RECIPIENT, mail.to)
        assertEquals(draft.subject, mail.subject)
        assertEquals(draft.body, mail.body)
        assertEquals(setOf("to", "subject", "body"), mail.headers.keys)
        assertArrayEquals(arrayOf(CompatibilityReportEmail.RECIPIENT), intent.getStringArrayExtra(Intent.EXTRA_EMAIL))
        assertEquals(draft.subject, intent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals(draft.body, intent.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals(setOf(Intent.EXTRA_EMAIL, Intent.EXTRA_SUBJECT, Intent.EXTRA_TEXT), intent.extras?.keySet())
        assertNull(intent.clipData)
        assertNull(intent.type)
        assertNull(intent.component)
        assertNull(intent.`package`)
        assertFalse(intent.hasExtra(Intent.EXTRA_STREAM))
    }

    @Test
    fun specialCharacters_cannotAddMailHeadersOrChangeRecipient() {
        val snapshot = report().copy(model = "테스트 + &bcc=other@example.invalid?#%\r\nBcc: nobody")
        val draft = CompatibilityReportEmail.prepare(snapshot)
        val mail = MailTo.parse(CompatibilityReportEmail.createIntent(draft).data.toString())
        assertEquals(CompatibilityReportEmail.RECIPIENT, mail.to)
        assertEquals(draft.subject, mail.subject)
        assertEquals(draft.body, mail.body)
        assertEquals(setOf("to", "subject", "body"), mail.headers.keys)
        assertFalse(draft.subject.contains('\r'))
        assertFalse(draft.subject.contains('\n'))
        assertTrue(draft.body.contains(snapshot.model))
    }

    @Test
    fun open_usesDirectEmailIntentAndSupportsApplicationContext() {
        val context = RecordingContext()
        val draft = CompatibilityReportEmail.prepare(report())
        assertEquals(CompatibilityReportEmail.OpenResult.OPENED, CompatibilityReportEmail.open(context, draft))
        assertEquals(1, context.intents.size)
        assertEquals(Intent.ACTION_SENDTO, context.intents.single().action)
        assertTrue(context.intents.single().flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun noEmailApp_returnsFailureWithoutOpeningBrowserOrShareSheet() {
        val context = RecordingContext(ActivityNotFoundException())
        val result = CompatibilityReportEmail.open(context, CompatibilityReportEmail.prepare(report()))
        assertEquals(CompatibilityReportEmail.OpenResult.NO_EMAIL_APP, result)
        assertEquals(1, context.intents.size)
        assertEquals(Intent.ACTION_SENDTO, context.intents.single().action)
    }

    @Test
    fun blockedEmailApp_returnsFailureWithoutAnotherLaunch() {
        val context = RecordingContext(SecurityException("blocked"))
        val result = CompatibilityReportEmail.open(context, CompatibilityReportEmail.prepare(report()))
        assertEquals(CompatibilityReportEmail.OpenResult.BLOCKED, result)
        assertEquals(1, context.intents.size)
    }

    private class RecordingContext(private val failure: RuntimeException? = null) :
        ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        val intents = mutableListOf<Intent>()

        override fun startActivity(intent: Intent) {
            intents += Intent(intent)
            failure?.let { throw it }
        }
    }
}
