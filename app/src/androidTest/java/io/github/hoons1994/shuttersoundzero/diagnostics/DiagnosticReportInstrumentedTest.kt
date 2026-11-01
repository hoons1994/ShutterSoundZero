package io.github.hoons1994.shuttersoundzero.diagnostics

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.github.hoons1994.shuttersoundzero.logging.DiagnosticLogger
import java.io.File
import java.io.IOException
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Builds real reports and intents without launching an email app or touching the real log. */
class DiagnosticReportInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val time = ZonedDateTime.parse("2026-11-01T21:30:15+09:00[Asia/Seoul]")

    @Test fun actualBuilderIncludesFieldsTimestampRangeAndReadFailureWithoutExceptionText() = runBlocking {
        val report = DiagnosticReportBuilder.build(context, { Result.failure(IOException("private failure details")) }, { time })
        for (field in listOf("앱 버전:", "기기:", "Android:", "One UI:", "보안 패치:", "소프트웨어 빌드:",
            "WRITE_SECURE_SETTINGS:", "CSC 카메라 무음 설정값:", "실제 촬영음: 앱에서 자동 확인하지 않음",
            "개발자 옵션:", "무선 디버깅:", "로컬 네트워크 접근:", "마지막 ADB 연결 포트:",
            "시스템 음량:", "소리 모드:", "방해 금지:", "최근 최대 200개 보관 / 마지막 최대 100개 포함",
            "2026-11-01T21:30:15+09:00 [Asia/Seoul]", "진단 기록을 읽지 못했습니다.")) {
            assertTrue("Missing report field: $field", report.diagnosticText.contains(field))
        }
        assertFalse(report.diagnosticText.contains("기록된 진단 이벤트가 없습니다"))
        assertFalse(report.diagnosticText.contains("private failure details"))
    }

    @Test fun actualLogReadDistinguishesMissingAndUnreadableFilesAndKeepsFifoEvents() = runBlocking {
        val root = File(context.cacheDir, "diagnostic-report-${System.nanoTime()}")
        assertTrue(root.mkdir())
        val isolated = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = root
        }
        try {
            val empty = DiagnosticReportBuilder.build(isolated)
            assertTrue(empty.diagnosticText.contains("기록된 진단 이벤트가 없습니다"))
            val blocked = File(root, "diagnostics/events.log")
            assertTrue(blocked.mkdirs())
            val failed = DiagnosticReportBuilder.build(isolated)
            assertTrue(failed.diagnosticText.contains("진단 기록을 읽지 못했습니다"))
            assertTrue(blocked.delete())
            for (index in 0..249) {
                DiagnosticLogger.recordSystemVolume(isolated, index, index, DiagnosticLogger.Outcome.SUCCESS,
                    DiagnosticLogger.VolumeReason.INDEX_APPLIED)
            }
            val populated = DiagnosticReportBuilder.build(isolated)
            assertTrue(populated.diagnosticText.contains("보관된 200개 중 마지막 100개"))
            assertTrue(populated.diagnosticText.contains("requested=150 |"))
            assertTrue(populated.diagnosticText.contains("requested=249 |"))
            assertFalse(populated.diagnosticText.contains("requested=149 |"))
        } finally {
            // All accepted writes precede this FIFO read, then only our unique fixture is removed.
            DiagnosticLogger.read(isolated)
            root.deleteRecursively()
        }
    }

    @Test fun largestReportAndUnicodeDescriptionSurviveMailtoEncodingWithoutChangingSnapshot() = runBlocking {
        val events = (0..249).map { "$it | " + "가&+?#%=😀".repeat(100) }
        val report = DiagnosticReportBuilder.build(context, { Result.success(events) }, { time })
        assertTrue(report.diagnosticText.length <= DiagnosticReportBuilder.MAX_DIAGNOSTIC_CHARS)
        val description = "  한글 & + ? # % = 😀\n다음 줄  "
        val intent = DiagnosticEmail.intent(report, description)
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("mailto", intent.data!!.scheme)
        val parts = intent.data!!.encodedSchemeSpecificPart.substringAfter('?').split('&').associate {
            val pair = it.split('=', limit = 2)
            Uri.decode(pair[0]) to Uri.decode(pair[1])
        }
        assertEquals(setOf("subject", "body"), parts.keys)
        assertEquals(report.subject, parts["subject"])
        assertEquals(DiagnosticEmail.body(report, description), parts["body"])
        assertFalse(intent.hasExtra(Intent.EXTRA_STREAM))
        assertTrue(parts.getValue("body").endsWith(report.diagnosticText + "\n"))
    }
}
