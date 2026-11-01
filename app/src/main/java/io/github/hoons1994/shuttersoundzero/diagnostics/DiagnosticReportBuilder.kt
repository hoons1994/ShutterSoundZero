package io.github.hoons1994.shuttersoundzero.diagnostics

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
        val diagnosticText: String
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
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        val versionName = (packageInfo.versionName ?: "알 수 없음").take(128)
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

        val diagnosticText = buildString {
            appendLine("ShutterSoundZero 오류 진단 정보")
            appendLine()
            appendLine("생성 시각: ${generatedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)} [${generatedAt.zone.id}]")
            appendLine("현재 상태는 보고서 생성 시점의 조회값이며, 오류 발생 당시 상태를 재현한 값이 아닙니다.")
            appendLine("진단 이벤트는 과거 작업 기록이며, 보내기 직전에 자동 갱신하지 않습니다.")
            appendLine()
            appendLine("[앱 / 기기]")
            appendLine("앱 버전: $versionName ($versionCode)")
            appendLine("기기: ${Build.MANUFACTURER.take(128)} $model")
            appendLine("Android: ${Build.VERSION.RELEASE.take(128)} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("One UI: ${detectOneUiVersion()}")
            appendLine("보안 패치: ${Build.VERSION.SECURITY_PATCH.ifBlank { "알 수 없음" }.take(128)}")
            appendLine("소프트웨어 빌드: ${Build.DISPLAY.take(128)}")
            appendLine()
            appendLine("[현재 상태]")
            appendLine("WRITE_SECURE_SETTINGS: ${yesNo(CscMuteManager.hasWritePermission(context))}")
            appendLine("CSC 카메라 무음 설정값: ${yesNo(CscMuteManager.readCscMutedState(context))}")
            appendLine("실제 촬영음: 앱에서 자동 확인하지 않음")
            appendLine("개발자 옵션: ${yesNo(DeveloperOptionsManager.isDeveloperOptionsEnabled(context))}")
            appendLine("무선 디버깅: ${yesNo(DeveloperOptionsManager.readWirelessDebuggingEnabled(context))}")
            appendLine("로컬 네트워크 접근: ${yesNo(LocalNetworkAccess.isGranted(context))}")
            appendLine("마지막 ADB 연결 포트: ${if (prefs.lastConnectPort in 1..65535) "저장됨" else "저장 안 됨"}")
            appendLine()
            appendLine(AudioDiagnosticSnapshot.read(context).format())
            appendLine()
            appendLine("[최근 진단 이벤트]")
            appendLine("기록 범위: 최근 최대 ${DiagnosticLogger.MAX_EVENTS}개 보관 / 마지막 최대 ${MAX_EMAIL_EVENTS}개 포함")
            if (retainedEvents == null) {
                appendLine("진단 기록을 읽지 못했습니다. 기록 유무와 포함 범위를 확인할 수 없습니다.")
            } else if (events.isEmpty()) {
                appendLine("기록된 진단 이벤트가 없습니다.")
            } else {
                appendLine("포함 범위: 보관된 ${retainedEvents.size}개 중 마지막 ${events.size}개 (이벤트 시각은 UTC)")
                events.forEach(::appendLine)
            }
            appendLine()
            appendLine("[개인정보 안내]")
            appendLine("이 진단 정보에는 페어링 코드, IP 주소, Wi-Fi SSID/BSSID, IMEI, 일련번호, ADB 개인키/인증서를 기록하지 않습니다.")
        }.trimEnd()

        Report(
            subject = "ShutterSoundZero 오류 신고 - v$versionName / ${model}",
            diagnosticText = diagnosticText
        )
    }

    private fun yesNo(value: Boolean?): String = when (value) {
        true -> "예"
        false -> "아니요"
        null -> "알 수 없음"
    }

    /**
     * 삼성의 SEM_PLATFORM_INT는 One UI 세대별 플랫폼 번호가 10000씩 증가한다.
     * 값을 읽을 수 없는 기기에서는 추측하지 않고 '알 수 없음'으로 둔다.
     */
    private fun detectOneUiVersion(): String {
        val semPlatformInt = runCatching {
            Build.VERSION::class.java.getField("SEM_PLATFORM_INT").getInt(null)
        }.getOrNull() ?: return "알 수 없음"

        val major = semPlatformInt / 10_000 - 9
        val minor = (semPlatformInt % 10_000) / 100
        return if (major in 1..20 && minor in 0..9) "$major.$minor" else "알 수 없음"
    }
}
