package com.charmingcolor.shuttersoundzero.debug

import android.net.Uri
import com.charmingcolor.shuttersoundzero.update.AppUpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Debug-build only helper for exercising the real in-app update pipeline.
 *
 * A dedicated prerelease is created by update-flow-device-test.yml. The base and
 * candidate APKs are built in the same workflow job with the same temporary debug
 * keystore, so AppUpdateManager can verify the candidate signer exactly as it would
 * for two production releases signed by the stable release certificate.
 */
object DebugUpdateTestManager {
    private const val TEST_TAG = "update-flow-test-v1.4.1"
    private const val TEST_VERSION = "1.4.1"
    private const val TEST_RELEASE_API =
        "https://api.github.com/repos/hoons1994/ShutterSoundZero/releases/tags/$TEST_TAG"
    private const val USER_AGENT = "ShutterSoundZero-UpdateFlowTest"

    suspend fun fetchTestUpdate(): AppUpdateManager.UpdateInfo = withContext(Dispatchers.IO) {
        val connection = (URL(TEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }

        val response = try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                error("테스트 릴리즈를 불러오지 못했습니다. (${connection.responseCode})")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }

        val json = JSONObject(response)
        if (json.optString("tag_name") != TEST_TAG) {
            error("예상한 테스트 릴리즈가 아닙니다.")
        }

        val expectedApkName = "ShutterSoundZero-v$TEST_VERSION-test.apk"
        val expectedShaName = "$expectedApkName.sha256"
        val assets = json.optJSONArray("assets") ?: error("테스트 릴리즈 파일이 없습니다.")

        var apkUrl: String? = null
        var shaUrl: String? = null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            when (asset.optString("name")) {
                expectedApkName -> apkUrl = asset.optString("browser_download_url")
                expectedShaName -> shaUrl = asset.optString("browser_download_url")
            }
        }

        fun requireTrusted(url: String?, expectedName: String): String {
            val value = url?.trim().orEmpty()
            if (value.isBlank()) error("$expectedName 파일을 찾지 못했습니다.")
            val parsed = Uri.parse(value)
            val expectedPrefix = "/hoons1994/ShutterSoundZero/releases/download/$TEST_TAG/"
            if (
                parsed.scheme != "https" ||
                parsed.host != "github.com" ||
                !parsed.path.orEmpty().startsWith(expectedPrefix)
            ) {
                error("테스트 릴리즈 다운로드 주소가 올바르지 않습니다.")
            }
            return value
        }

        AppUpdateManager.UpdateInfo(
            tagName = TEST_TAG,
            versionName = TEST_VERSION,
            releaseNotes = json.optString("body").trim().ifBlank {
                "앱 내부 다운로드 → 무결성/서명 검증 → Android 패키지 설치 화면 연결을 확인하는 테스트 빌드입니다."
            },
            apkUrl = requireTrusted(apkUrl, expectedApkName),
            sha256Url = requireTrusted(shaUrl, expectedShaName)
        )
    }
}
