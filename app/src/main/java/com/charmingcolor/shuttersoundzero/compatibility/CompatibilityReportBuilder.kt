package com.charmingcolor.shuttersoundzero.compatibility

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.charmingcolor.shuttersoundzero.core.CscMuteManager
import org.json.JSONObject

object CompatibilityReportBuilder {
    private const val REPORT_SCHEMA_VERSION = 1
    private const val ISSUE_BASE_URL =
        "https://github.com/hoons1994/ShutterSoundZero/issues/new"

    data class Report(
        val schemaVersion: Int,
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
            schemaVersion = REPORT_SCHEMA_VERSION,
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

    fun buildIssueUri(report: Report): Uri {
        val body = buildString {
            appendLine("<!-- shuttersoundzero-compatibility-report:v1 -->")
            appendLine("ShutterSoundZero 설정 화면에서 사용자가 공개 제출에 동의한 호환성 정보입니다.")
            appendLine()
            appendLine("## 제출 정보")
            appendLine()
            appendLine(report.displayText())
            appendLine()
            appendLine("## 자동 처리 데이터")
            appendLine()
            appendLine("```json")
            appendLine(report.toJson().toString())
            appendLine("```")
            appendLine()
            appendLine(
                "> 이 보고서에는 이름, 계정, 전화번호, 사진·미디어, 위치, IMEI, Android ID, " +
                    "일련번호, Wi-Fi 정보, ADB 페어링 코드·키가 포함되지 않습니다."
            )
        }.trimEnd()

        val title = "[호환성] ${report.model} / Android ${report.androidVersion}"
        return Uri.parse(ISSUE_BASE_URL)
            .buildUpon()
            .appendQueryParameter("title", title)
            .appendQueryParameter("body", body)
            .build()
    }

    private fun Report.toJson(): JSONObject {
        return JSONObject()
            .put("schemaVersion", schemaVersion)
            .put("manufacturer", manufacturer)
            .put("model", model)
            .put("androidVersion", androidVersion)
            .put("sdkInt", sdkInt)
            .put("oneUiVersion", oneUiVersion)
            .put("securityPatch", securityPatch)
            .put("appVersion", appVersion)
            .put("setupPermissionGranted", setupPermissionGranted)
            .put("cameraMuteApplied", cameraMuteApplied)
    }

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
