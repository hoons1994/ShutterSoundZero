package io.github.hoons1994.shuttersoundzero.diagnostics

import io.github.hoons1994.shuttersoundzero.logging.DiagnosticLogger

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.hoons1994.shuttersoundzero.core.CscMuteManager
import io.github.hoons1994.shuttersoundzero.core.DeveloperOptionsManager
import io.github.hoons1994.shuttersoundzero.core.adb.LocalNetworkAccess
import io.github.hoons1994.shuttersoundzero.data.PreferencesRepository

object DiagnosticReportBuilder {
    const val SUPPORT_EMAIL = "hoons1994@naver.com"
    private const val MAX_EMAIL_EVENTS = 100

    data class Report(
        val subject: String,
        val diagnosticText: String
    )

    fun build(context: Context): Report {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        val versionName = packageInfo.versionName ?: "알 수 없음"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        val prefs = PreferencesRepository.getInstance(context)
        val events = DiagnosticLogger.read(context).takeLast(MAX_EMAIL_EVENTS)

        val diagnosticText = buildString {
            appendLine("ShutterSoundZero 오류 진단 정보")
            appendLine()
            appendLine("[앱 / 기기]")
            appendLine("앱 버전: $versionName ($versionCode)")
            appendLine("기기: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("One UI: ${detectOneUiVersion()}")
            appendLine("보안 패치: ${Build.VERSION.SECURITY_PATCH.ifBlank { "알 수 없음" }}")
            appendLine("소프트웨어 빌드: ${Build.DISPLAY}")
            appendLine()
            appendLine("[현재 상태]")
            appendLine("WRITE_SECURE_SETTINGS: ${yesNo(CscMuteManager.hasWritePermission(context))}")
            appendLine("CSC 카메라 무음: ${yesNo(CscMuteManager.readCscMutedState(context))}")
            appendLine("개발자 옵션: ${yesNo(DeveloperOptionsManager.isDeveloperOptionsEnabled(context))}")
            appendLine("무선 디버깅: ${yesNo(DeveloperOptionsManager.readWirelessDebuggingEnabled(context))}")
            appendLine("로컬 네트워크 접근: ${yesNo(LocalNetworkAccess.isGranted(context))}")
            appendLine("저장된 ADB 연결 정보: ${if (prefs.lastConnectPort in 1..65535) "있음" else "없음"}")
            appendLine()
            appendLine("[최근 진단 이벤트]")
            if (events.isEmpty()) {
                appendLine("기록된 진단 이벤트가 없습니다.")
            } else {
                events.forEach(::appendLine)
            }
            appendLine()
            appendLine("[개인정보 안내]")
            appendLine("이 진단 정보에는 페어링 코드, IP 주소, Wi-Fi SSID/BSSID, IMEI, 일련번호, ADB 개인키/인증서를 기록하지 않습니다.")
        }.trimEnd()

        return Report(
            subject = "ShutterSoundZero 오류 신고 - v$versionName / ${Build.MODEL}",
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
