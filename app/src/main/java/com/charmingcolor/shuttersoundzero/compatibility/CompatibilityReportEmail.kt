package com.charmingcolor.shuttersoundzero.compatibility

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import com.charmingcolor.shuttersoundzero.diagnostics.DiagnosticReportBuilder

/** Prepares a local draft; this object never sends email or makes network requests. */
internal object CompatibilityReportEmail {
    const val RECIPIENT = DiagnosticReportBuilder.SUPPORT_EMAIL

    /** The exact subject/body shown for consent and then handed to the email app. */
    data class Draft(val subject: String, val body: String)

    enum class OpenResult { OPENED, NO_EMAIL_APP, BLOCKED }

    fun prepare(report: CompatibilityReportBuilder.Report): Draft = Draft(
        subject = CompatibilityReportBuilder.buildEmailSubject(report)
            .replace('\r', ' ').replace('\n', ' '),
        body = CompatibilityReportBuilder.buildEmailBody(report)
    )

    fun createIntent(draft: Draft): Intent {
        val uri = Uri.parse(
            "mailto:$RECIPIENT" +
                "?subject=${Uri.encode(draft.subject)}" +
                "&body=${Uri.encode(draft.body)}"
        )
        return Intent(Intent.ACTION_SENDTO, uri).apply {
            // Some mail clients read extras rather than mailto query parameters.
            // Both representations contain exactly the same approved draft.
            putExtra(Intent.EXTRA_EMAIL, arrayOf(RECIPIENT))
            putExtra(Intent.EXTRA_SUBJECT, draft.subject)
            putExtra(Intent.EXTRA_TEXT, draft.body)
        }
    }

    /** OPENED only means the launch request was accepted, not that email was sent. */
    fun open(context: Context, draft: Draft): OpenResult {
        val intent = createIntent(draft)
        if (!context.hasActivity()) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            // Honor the default email app. Android offers a resolver when needed.
            // Do not wrap in an always-resolvable chooser or query installed apps.
            context.startActivity(intent)
            OpenResult.OPENED
        } catch (_: ActivityNotFoundException) {
            OpenResult.NO_EMAIL_APP
        } catch (_: SecurityException) {
            OpenResult.BLOCKED
        }
    }

    private fun Context.hasActivity(): Boolean {
        var current: Context = this
        while (current is ContextWrapper) {
            if (current is Activity) return true
            val base = current.baseContext
            if (base === current) break
            current = base
        }
        return current is Activity
    }
}
