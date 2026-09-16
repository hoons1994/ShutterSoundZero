package io.github.hoons1994.shuttersoundzero.compatibility

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.hoons1994.shuttersoundzero.core.CscMuteManager

object CompatibilityReportBuilder {
    data class Report(
        val manufacturer: String,
        val model: String,
        val androidVersion: String,
        val sdkInt: Int,
        val oneUiVersion: String,
        val securityPatch: String,
        val appVersion: String,
        val setupPermissionGranted: Boolean,
        val cameraMuteApplied: Boolean
    ) {
        fun displayText(): String = buildString {
            appendLine("제조사: $manufacturer")
            appendLine("모델 번호: $model")
            appendLine("Android 버전: $androidVersion (SDK $sdkInt)")
            appendLine("One UI 버전: $oneUiVersion")
            appendLine("보안 패치: $securityPatch")
            appendLine("앱 버전: $appVersion")
            appendLine("1회 설정 권한: ${if (setupPermissionGranted) "있음" else "없음"}")
            append("현재 CSC 카메라 무음 설정: ${if (cameraMuteApplied) "적용" else "미적용"}")
        }
    }

    fun build(context: Context): Report {
        return Report(
            manufacturer = Build.MANUFACTURER.ifBlank { "알 수 없음" },
            model = Build.MODEL.ifBlank { "알 수 없음" },
            androidVersion = Build.VERSION.RELEASE.ifBlank { "알 수 없음" },
            sdkInt = Build.VERSION.SDK_INT,
            oneUiVersion = detectOneUiVersion(),
            securityPatch = Build.VERSION.SECURITY_PATCH.ifBlank { "알 수 없음" },
            appVersion = currentVersionName(context),
            setupPermissionGranted = CscMuteManager.hasWritePermission(context),
            cameraMuteApplied = CscMuteManager.isCscShutterSoundMuted(context)
        )
    }

    fun buildEmailSubject(report: Report): String =
        "[ShutterSoundZero 호환성] ${report.model} / Android ${report.androidVersion}"

    /** Formats only the supplied snapshot; it does not read logs or recollect device data. */
    fun buildEmailBody(report: Report): String = buildString {
        appendLine("안녕하세요. ShutterSoundZero 호환성 정보를 제보합니다.")
        appendLine()
        appendLine("[호환성 정보]")
        appendLine(report.displayText())
        appendLine()
        appendLine("위 정보는 앱의 호환성 데이터 제출 화면에서 확인 후 전송한 내용입니다.")
    }.trimEnd()

    @Suppress("DEPRECATION")
    private fun currentVersionName(context: Context): String {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        return packageInfo.versionName ?: "알 수 없음"
    }

    private fun detectOneUiVersion(): String {
        val semPlatformInt = runCatching {
            Build.VERSION::class.java.getField("SEM_PLATFORM_INT").getInt(null)
        }.getOrNull() ?: return "알 수 없음"

        val major = semPlatformInt / 10_000 - 9
        val minor = (semPlatformInt % 10_000) / 100
        return if (major in 1..20 && minor in 0..9) "$major.$minor" else "알 수 없음"
    }
}
