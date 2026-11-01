package io.github.hoons1994.shuttersoundzero.diagnostics

import android.content.Intent
import android.net.Uri

/** Builds a draft only. The caller opens the email application after explicit user action. */
internal object DiagnosticEmail {
    const val MAX_DESCRIPTION_CHARS = 2000

    fun body(report: DiagnosticReportBuilder.Report, description: String): String {
        require(description.length <= MAX_DESCRIPTION_CHARS)
        return buildString {
            appendLine("안녕하세요. ShutterSoundZero 사용 중 오류가 발생해 진단 정보를 보냅니다.")
            appendLine()
            appendLine("[사용자 설명]")
            appendLine(description.ifBlank { "작성하지 않음" })
            appendLine()
            appendLine(report.diagnosticText)
        }
    }

    fun intent(report: DiagnosticReportBuilder.Report, description: String): Intent = Intent(
        Intent.ACTION_SENDTO,
        Uri.parse(
            "mailto:${Uri.encode(DiagnosticReportBuilder.SUPPORT_EMAIL)}" +
                "?subject=${Uri.encode(report.subject)}&body=${Uri.encode(body(report, description))}"
        )
    )
}
