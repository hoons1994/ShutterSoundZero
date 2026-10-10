package io.github.hoons1994.shuttersoundzero.update

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.json.JSONObject

/** Public release metadata only. No APK transfer, credentials or device identifiers. */
internal class GitHubReleaseClient(
    private val openConnection: () -> HttpURLConnection = {
        URL("https://api.github.com/repos/hoons1994/ShutterSoundZero/releases/latest")
            .openConnection() as HttpURLConnection
    }
) {
    suspend fun fetchLatest(): LatestRelease = runInterruptible(Dispatchers.IO) {
        try {
            val connection = openConnection()
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
                connection.setRequestProperty("User-Agent", "ShutterSoundZero-update-check")
                when (connection.responseCode) {
                    200 -> connection.inputStream.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        val deadline = System.nanoTime() + 15_000_000_000L
                        while (true) {
                            if (Thread.currentThread().isInterrupted) throw InterruptedIOException()
                            if (System.nanoTime() > deadline) throw IOException("Release response timed out")
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (output.size() + count > MAX_RESPONSE_BYTES) {
                                throw UpdateCheckException(UpdateCheckFailure.INVALID_RESPONSE)
                            }
                            output.write(buffer, 0, count)
                        }
                        parseLatest(output.toString(Charsets.UTF_8.name()))
                    }
                    403, 429 -> throw UpdateCheckException(UpdateCheckFailure.RATE_LIMITED)
                    404 -> throw UpdateCheckException(UpdateCheckFailure.NO_RELEASE)
                    else -> throw UpdateCheckException(UpdateCheckFailure.SERVER)
                }
            } finally {
                connection.disconnect()
            }
        } catch (error: IOException) {
            throw UpdateCheckException(UpdateCheckFailure.NETWORK, error)
        }
    }

    companion object {
        internal const val MAX_RESPONSE_BYTES = 256 * 1024

        internal fun parseLatest(body: String): LatestRelease {
            try {
                val json = JSONObject(body)
                if (json.get("draft") != false || json.get("prerelease") != false) {
                    throw UpdateCheckException(UpdateCheckFailure.INVALID_RESPONSE)
                }
                val tag = json.getString("tag_name")
                val version = AppVersion.parse(tag)
                    ?: throw UpdateCheckException(UpdateCheckFailure.INVALID_RESPONSE)
                return LatestRelease(tag, version)
            } catch (error: UpdateCheckException) {
                throw error
            } catch (error: Exception) {
                throw UpdateCheckException(UpdateCheckFailure.INVALID_RESPONSE, error)
            }
        }
    }
}
