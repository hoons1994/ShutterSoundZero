package com.charmingcolor.shuttersoundzero.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

object AppUpdateManager {
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/hoons1994/ShutterSoundZero/releases/latest"
    private const val RELEASE_DOWNLOAD_PREFIX =
        "/hoons1994/ShutterSoundZero/releases/download/"
    private const val USER_AGENT = "ShutterSoundZero-UpdateChecker"
    private const val MAX_RELEASE_METADATA_BYTES = 512 * 1024
    private const val MAX_SHA256_FILE_BYTES = 16 * 1024
    private const val MAX_APK_BYTES = 200L * 1024L * 1024L
    internal const val AUTOMATIC_CHECK_INTERVAL_MILLIS = 24L * 60L * 60L * 1000L

    sealed interface UpdateCheckResult {
        data class UpToDate(val latestVersion: String) : UpdateCheckResult
        data class Available(val update: UpdateInfo) : UpdateCheckResult
    }

    data class UpdateInfo(
        val tagName: String,
        val versionName: String,
        val releaseNotes: String,
        val apkUrl: String,
        val sha256Url: String
    )

    data class VerifiedUpdate(
        val file: File,
        val versionName: String,
        val versionCode: Long,
        val sha256: String
    )

    suspend fun checkForUpdate(context: Context): UpdateCheckResult = withContext(Dispatchers.IO) {
        val response = readUrl(
            LATEST_RELEASE_API,
            acceptJson = true,
            maxBytes = MAX_RELEASE_METADATA_BYTES
        )
        val json = JSONObject(response)

        if (json.optBoolean("draft", false) || json.optBoolean("prerelease", false)) {
            error("정식 릴리즈 정보를 확인할 수 없습니다.")
        }

        val tagName = json.optString("tag_name").trim()
        val latestVersion = normalizeVersion(tagName)
            ?: error("릴리즈 버전 형식을 확인할 수 없습니다.")
        val currentVersion = currentVersionName(context)

        if (!isNewerVersion(latestVersion, currentVersion)) {
            return@withContext UpdateCheckResult.UpToDate(latestVersion)
        }

        val assets = json.optJSONArray("assets")
            ?: error("릴리즈 파일 정보를 찾을 수 없습니다.")
        val expectedApkName = "ShutterSoundZero-v$latestVersion.apk"
        val expectedShaName = "$expectedApkName.sha256"

        var apkUrl: String? = null
        var shaUrl: String? = null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            when (asset.optString("name")) {
                expectedApkName -> apkUrl = asset.optString("browser_download_url")
                expectedShaName -> shaUrl = asset.optString("browser_download_url")
            }
        }

        val trustedApkUrl = requireTrustedReleaseAssetUrl(apkUrl, tagName)
        val trustedShaUrl = requireTrustedReleaseAssetUrl(shaUrl, tagName)

        UpdateCheckResult.Available(
            UpdateInfo(
                tagName = tagName,
                versionName = latestVersion,
                releaseNotes = json.optString("body").trim().ifBlank {
                    "이번 버전의 변경사항은 GitHub Releases에서 확인할 수 있습니다."
                },
                apkUrl = trustedApkUrl,
                sha256Url = trustedShaUrl
            )
        )
    }

    suspend fun downloadAndVerify(
        context: Context,
        update: UpdateInfo,
        onProgress: suspend (Int) -> Unit = {}
    ): VerifiedUpdate = withContext(Dispatchers.IO) {
        val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
        updateDir.listFiles()?.forEach { it.delete() }

        val apkFile = File(updateDir, "ShutterSoundZero-v${update.versionName}.apk")
        try {
            downloadFile(update.apkUrl, apkFile) { progress ->
                withContext(Dispatchers.Main.immediate) {
                    onProgress(progress)
                }
            }
            currentCoroutineContext().ensureActive()

            val expectedSha = parseSha256(
                readUrl(
                    update.sha256Url,
                    acceptJson = false,
                    maxBytes = MAX_SHA256_FILE_BYTES
                )
            ) ?: error("SHA-256 검증값을 읽을 수 없습니다.")
            currentCoroutineContext().ensureActive()

            val actualSha = sha256(apkFile)
            if (!actualSha.equals(expectedSha, ignoreCase = true)) {
                error("다운로드한 APK의 SHA-256 값이 릴리즈 정보와 일치하지 않습니다.")
            }

            currentCoroutineContext().ensureActive()
            val archiveInfo = packageArchiveInfo(context, apkFile)
                ?: error("다운로드한 APK 정보를 읽을 수 없습니다.")
            if (archiveInfo.packageName != context.packageName) {
                error("다운로드한 APK의 패키지 이름이 현재 앱과 일치하지 않습니다.")
            }

            val installedInfo = installedPackageInfo(context)
            if (archiveInfo.longVersionCode <= installedInfo.longVersionCode) {
                error("다운로드한 APK가 현재 설치된 버전보다 최신 버전이 아닙니다.")
            }

            val installedSigners = signerDigests(installedInfo)
            val archiveSigners = signerDigests(archiveInfo)
            if (installedSigners.isEmpty() || archiveSigners.isEmpty() || installedSigners != archiveSigners) {
                error("다운로드한 APK의 서명 인증서가 현재 설치된 앱과 일치하지 않습니다.")
            }

            val archiveVersion = archiveInfo.versionName.orEmpty()
            if (archiveVersion != update.versionName) {
                error("다운로드한 APK의 버전 정보가 GitHub 릴리즈와 일치하지 않습니다.")
            }

            currentCoroutineContext().ensureActive()
            withContext(Dispatchers.Main.immediate) {
                onProgress(100)
            }
            VerifiedUpdate(
                file = apkFile,
                versionName = archiveVersion,
                versionCode = archiveInfo.longVersionCode,
                sha256 = actualSha
            )
        } catch (error: Throwable) {
            apkFile.delete()
            throw error
        }
    }

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun launchInstaller(context: Context, verifiedUpdate: VerifiedUpdate): Boolean {
        if (!verifiedUpdate.file.exists() || !canRequestPackageInstalls(context)) return false

        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                verifiedUpdate.file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    internal fun isAutomaticCheckDue(
        enabled: Boolean,
        lastCheckAtMillis: Long,
        nowMillis: Long
    ): Boolean {
        if (!enabled) return false
        if (lastCheckAtMillis <= 0L) return true
        if (nowMillis < lastCheckAtMillis) return true
        return nowMillis - lastCheckAtMillis >= AUTOMATIC_CHECK_INTERVAL_MILLIS
    }

    internal fun installedVersionName(context: Context): String = currentVersionName(context)

    internal fun isNewerVersion(candidate: String, current: String): Boolean {
        val candidateParts = semanticVersionParts(candidate) ?: return false
        val currentParts = semanticVersionParts(current) ?: return false
        for (index in candidateParts.indices) {
            if (candidateParts[index] != currentParts[index]) {
                return candidateParts[index] > currentParts[index]
            }
        }
        return false
    }

    internal fun parseSha256(value: String): String? {
        return Regex("(?i)\\b[0-9a-f]{64}\\b").find(value)?.value?.lowercase()
    }

    internal fun readBoundedText(
        input: InputStream,
        maxBytes: Int,
        checkActive: () -> Unit = {}
    ): String {
        require(maxBytes > 0) { "maxBytes must be positive" }

        val output = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE * 2))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytes = 0

        while (true) {
            checkActive()
            val read = input.read(buffer)
            if (read < 0) break

            totalBytes += read
            if (totalBytes > maxBytes) {
                throw IOException("업데이트 서버 응답 크기가 허용 범위를 초과했습니다.")
            }
            output.write(buffer, 0, read)
        }

        return output.toString(Charsets.UTF_8.name())
    }

    internal suspend fun <T> withCancellationCleanup(
        cleanup: () -> Unit,
        block: suspend () -> T
    ): T = coroutineScope {
        val cleaned = AtomicBoolean(false)
        fun cleanupOnce() {
            if (cleaned.compareAndSet(false, true)) {
                runCatching(cleanup)
            }
        }

        // Start undispatched so the cancellation hook is installed before any blocking I/O begins.
        // If the parent coroutine is cancelled while connect/read is blocked, this child resumes on
        // another IO worker and disconnects the underlying connection to unblock the operation.
        val cancellationWatcher = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                cleanupOnce()
            }
        }

        try {
            block()
        } finally {
            cancellationWatcher.cancel()
            cleanupOnce()
        }
    }

    private fun normalizeVersion(value: String): String? {
        val normalized = value.removePrefix("v")
        return if (semanticVersionParts(normalized) != null) normalized else null
    }

    private fun semanticVersionParts(value: String): List<Int>? {
        val core = value.trim().removePrefix("v").substringBefore('-')
        val parts = core.split('.')
        if (parts.size != 3) return null
        return parts.map { part -> part.toIntOrNull() ?: return null }
    }

    private fun requireTrustedReleaseAssetUrl(url: String?, tagName: String): String {
        val value = url?.trim().orEmpty()
        if (value.isBlank()) error("필요한 릴리즈 파일을 찾을 수 없습니다.")
        val parsed = Uri.parse(value)
        val expectedPrefix = "$RELEASE_DOWNLOAD_PREFIX$tagName/"
        if (parsed.scheme != "https" || parsed.host != "github.com" || !parsed.path.orEmpty().startsWith(expectedPrefix)) {
            error("신뢰할 수 없는 릴리즈 다운로드 주소입니다.")
        }
        return value
    }

    private fun currentVersionName(context: Context): String {
        return installedPackageInfo(context).versionName ?: "0.0.0"
    }

    private fun installedPackageInfo(context: Context): PackageInfo {
        val packageManager = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }
    }

    private fun packageArchiveInfo(context: Context, apkFile: File): PackageInfo? {
        val packageManager = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
        }
    }

    private fun signerDigests(packageInfo: PackageInfo): Set<String> {
        val signingInfo = packageInfo.signingInfo ?: return emptySet()
        return signingInfo.apkContentsSigners
            .map { signature -> sha256(signature.toByteArray()) }
            .toSet()
    }

    private suspend fun downloadFile(
        url: String,
        target: File,
        onProgress: suspend (Int) -> Unit
    ) {
        withOpenConnection(url, acceptJson = false) { connection ->
            val totalBytes = connection.contentLengthLong
            if (totalBytes > MAX_APK_BYTES) {
                error("업데이트 APK 크기가 허용 범위를 초과했습니다.")
            }

            var downloadedBytes = 0L
            var lastProgress = -1
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        downloadedBytes += read
                        if (downloadedBytes > MAX_APK_BYTES) {
                            throw IOException("업데이트 APK 크기가 허용 범위를 초과했습니다.")
                        }
                        output.write(buffer, 0, read)
                        if (totalBytes > 0L) {
                            val progress = ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 99)
                            if (progress != lastProgress) {
                                lastProgress = progress
                                onProgress(progress)
                            }
                        }
                    }
                    output.flush()
                }
            }
        }
    }

    private suspend fun readUrl(url: String, acceptJson: Boolean, maxBytes: Int): String {
        currentCoroutineContext().ensureActive()
        return withOpenConnection(url, acceptJson) { connection ->
            val declaredLength = connection.contentLengthLong
            if (declaredLength > maxBytes) {
                error("업데이트 서버 응답 크기가 허용 범위를 초과했습니다.")
            }
            val coroutineContext = currentCoroutineContext()
            connection.inputStream.use { input ->
                readBoundedText(input, maxBytes) {
                    coroutineContext.ensureActive()
                }
            }
        }
    }

    private suspend fun <T> withOpenConnection(
        url: String,
        acceptJson: Boolean,
        block: suspend (HttpURLConnection) -> T
    ): T {
        val connection = createConnection(url, acceptJson)
        return withCancellationCleanup(connection::disconnect) {
            currentCoroutineContext().ensureActive()
            connection.connect()
            currentCoroutineContext().ensureActive()

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                connection.errorStream?.close()
                error("업데이트 서버 응답 오류 ($responseCode). 잠시 후 다시 시도해 주세요.")
            }
            if (!connection.url.protocol.equals("https", ignoreCase = true)) {
                error("업데이트 다운로드가 안전하지 않은 연결로 전환되었습니다.")
            }

            block(connection)
        }
    }

    private fun createConnection(url: String, acceptJson: Boolean): HttpURLConnection {
        val parsedUrl = URL(url)
        if (!parsedUrl.protocol.equals("https", ignoreCase = true)) {
            error("보안 연결(HTTPS)이 아닌 업데이트 주소는 사용할 수 없습니다.")
        }

        return (parsedUrl.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            if (acceptJson) {
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            }
        }
    }

    private suspend fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}
