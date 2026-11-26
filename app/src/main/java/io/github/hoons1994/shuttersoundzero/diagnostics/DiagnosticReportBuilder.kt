package io.github.hoons1994.shuttersoundzero.diagnostics

import android.content.res.Configuration
import io.github.hoons1994.shuttersoundzero.data.withAppLanguage
import io.github.hoons1994.shuttersoundzero.R
import io.github.hoons1994.shuttersoundzero.logging.DiagnosticLogger

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.hoons1994.shuttersoundzero.core.CscMuteManager
import io.github.hoons1994.shuttersoundzero.core.DeveloperOptionsManager
import io.github.hoons1994.shuttersoundzero.core.adb.LocalNetworkAccess
import io.github.hoons1994.shuttersoundzero.data.PreferencesRepository
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DiagnosticReportBuilder {
    const val SUPPORT_EMAIL = "hoons1994@naver.com"
    internal const val MAX_EMAIL_EVENTS = 100
    const val MAX_DIAGNOSTIC_CHARS = 54_000

    data class Report(
        val subject: String,
        val diagnosticText: String,
        val languageTags: String = ""
    ) {
        init {
            require(diagnosticText.length <= MAX_DIAGNOSTIC_CHARS) { "Diagnostic report exceeds text limit" }
        }
    }

    suspend fun build(context: Context): Report = build(context, DiagnosticLogger::read)

    internal suspend fun build(
        context: Context,
        readEvents: suspend (Context) -> Result<List<String>>,
        now: () -> ZonedDateTime = { ZonedDateTime.now() }
    ): Report = withContext(Dispatchers.IO) {
        val locales = context.withAppLanguage().resources.configuration.locales
        val strings = context.createConfigurationContext(Configuration().apply { setLocales(locales) }).resources
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        val versionName = (packageInfo.versionName ?: strings.getString(R.string.diag_unknown)).take(128)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        val prefs = PreferencesRepository.getInstance(context)
        val eventResult = readEvents(context).map(DiagnosticLogger::trimForStorage)
        val retainedEvents = eventResult.getOrNull()
        val events = retainedEvents?.takeLast(MAX_EMAIL_EVENTS).orEmpty()
        val generatedAt = now()
        val model = Build.MODEL.take(128)

        fun yesNo(value: Boolean?) = strings.getString(diagnosticBooleanResource(value))

        val diagnosticText = buildString {
            appendLine(strings.getString(R.string.diag_title))
            appendLine()
            appendLine(strings.getString(R.string.diag_generated, generatedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), generatedAt.zone.id))
            appendLine(strings.getString(R.string.diag_current_hint))
            appendLine(strings.getString(R.string.diag_history_hint))
            appendLine()
            appendLine(strings.getString(R.string.diag_device_section))
            appendLine(strings.getString(R.string.diag_version, versionName, versionCode))
            appendLine(strings.getString(R.string.diag_device, Build.MANUFACTURER.take(128), model))
            appendLine("Android: ${Build.VERSION.RELEASE.take(128)} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("One UI: ${detectOneUiVersion() ?: strings.getString(R.string.diag_unknown)}")
            appendLine(strings.getString(R.string.diag_patch, Build.VERSION.SECURITY_PATCH.ifBlank { strings.getString(R.string.diag_unknown) }.take(128)))
            appendLine(strings.getString(R.string.diag_build, Build.DISPLAY.take(128)))
            appendLine()
            appendLine(strings.getString(R.string.diag_state_section))
            appendLine("WRITE_SECURE_SETTINGS: ${yesNo(CscMuteManager.hasWritePermission(context))}")
            appendLine(strings.getString(R.string.diag_csc, yesNo(CscMuteManager.readCscMutedState(context))))
            appendLine(strings.getString(R.string.diag_shutter))
            appendLine(strings.getString(R.string.diag_developer, yesNo(DeveloperOptionsManager.isDeveloperOptionsEnabled(context))))
            appendLine(strings.getString(R.string.diag_wireless, yesNo(DeveloperOptionsManager.readWirelessDebuggingEnabled(context))))
            appendLine(strings.getString(R.string.diag_network, yesNo(LocalNetworkAccess.isGranted(context))))
            appendLine(strings.getString(R.string.diag_port, strings.getString(if (prefs.lastConnectPort in 1..65535) R.string.diag_saved else R.string.diag_not_saved)))
            appendLine()
            appendLine(AudioDiagnosticSnapshot.read(context).format(strings::getString))
            appendLine()
            appendLine(strings.getString(R.string.diag_events))
            appendLine(strings.getString(R.string.diag_capacity, DiagnosticLogger.MAX_EVENTS, MAX_EMAIL_EVENTS))
            if (retainedEvents == null) {
                appendLine(strings.getString(R.string.diag_read_failed))
            } else if (events.isEmpty()) {
                appendLine(strings.getString(R.string.diag_empty))
            } else {
                appendLine(strings.getString(R.string.diag_included, retainedEvents.size, events.size))
                events.forEach(::appendLine)
            }
            appendLine()
            appendLine(strings.getString(R.string.diag_privacy))
            appendLine(strings.getString(R.string.diag_privacy_detail))
        }.trimEnd()

        Report(
            subject = strings.getString(R.string.diag_subject, versionName, model),
            diagnosticText = diagnosticText,
            languageTags = locales.toLanguageTags()
        )
    }

    /**
     * 삼성의 SEM_PLATFORM_INT는 One UI 세대별 플랫폼 번호가 10000씩 증가한다.
     * 값을 읽을 수 없는 기기에서는 추측하지 않고 '알 수 없음'으로 둔다.
     */
    private fun detectOneUiVersion(): String? {
        val semPlatformInt = runCatching {
            Build.VERSION::class.java.getField("SEM_PLATFORM_INT").getInt(null)
        }.getOrNull() ?: return null

        val major = semPlatformInt / 10_000 - 9
        val minor = (semPlatformInt % 10_000) / 100
        return if (major in 1..20 && minor in 0..9) "$major.$minor" else null
    }
}
