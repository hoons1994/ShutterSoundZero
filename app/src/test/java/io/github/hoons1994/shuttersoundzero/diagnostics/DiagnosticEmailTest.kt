package io.github.hoons1994.shuttersoundzero.diagnostics

import org.junit.Assert.*
import org.junit.Test

class DiagnosticEmailTest {
    @Test fun bodyPreservesDescriptionAndReviewedSnapshotExactly() {
        val description = "  한글 설명\n둘째 줄 & + ? # % = 😀  "
        val report = DiagnosticReportBuilder.Report("제목", "고정된 생성 시각\n진단 값 & + ? # % = 😀")
        val body = DiagnosticEmail.body(report, description)
        assertTrue(body.contains("[사용자 설명]\n$description\n\n"))
        assertTrue(body.endsWith(report.diagnosticText + "\n"))
        assertEquals(body, DiagnosticEmail.body(report, description))
    }

    @Test fun blankDescriptionAndMaximumLengthsAreExplicit() {
        val report = DiagnosticReportBuilder.Report("제목", "가".repeat(DiagnosticReportBuilder.MAX_DIAGNOSTIC_CHARS))
        assertTrue(DiagnosticEmail.body(report, "").contains("작성하지 않음"))
        val body = DiagnosticEmail.body(report, "나".repeat(DiagnosticEmail.MAX_DESCRIPTION_CHARS))
        assertTrue(body.endsWith(report.diagnosticText + "\n"))
        assertTrue(body.length < 57_000)
        assertThrows(IllegalArgumentException::class.java) {
            DiagnosticReportBuilder.Report("제목", "x".repeat(DiagnosticReportBuilder.MAX_DIAGNOSTIC_CHARS + 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            DiagnosticEmail.body(report, "x".repeat(DiagnosticEmail.MAX_DESCRIPTION_CHARS + 1))
        }
    }
}
