package io.github.hoons1994.shuttersoundzero.diagnostics

import android.os.LocaleList
import android.content.res.Configuration
import io.github.hoons1994.shuttersoundzero.data.withAppLanguage
import io.github.hoons1994.shuttersoundzero.R
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Builds a draft only. The caller opens the email application after explicit user action. */
internal object DiagnosticEmail {
    const val MAX_DESCRIPTION_CHARS = 2000

    fun body(report: DiagnosticReportBuilder.Report, description: String, text: (Int) -> String): String {
        require(description.length <= MAX_DESCRIPTION_CHARS)
        return buildString {
            appendLine(text(R.string.diag_intro))
            appendLine()
            appendLine(text(R.string.diag_description))
            appendLine(description.ifBlank { text(R.string.diag_none) })
            appendLine()
            appendLine(report.diagnosticText)
        }
    }

    fun intent(context: Context, report: DiagnosticReportBuilder.Report, description: String): Intent {
        val strings = if (report.languageTags.isEmpty()) {
            context.withAppLanguage().resources
        } else {
            val config = Configuration().apply { setLocales(LocaleList.forLanguageTags(report.languageTags)) }
            context.createConfigurationContext(config).resources
        }
        return Intent(
            Intent.ACTION_SENDTO,
            Uri.parse(
                "mailto:${Uri.encode(DiagnosticReportBuilder.SUPPORT_EMAIL)}" +
                    "?subject=${Uri.encode(report.subject)}&body=${Uri.encode(body(report, description, strings::getString))}"
            )
        )
    }
}
