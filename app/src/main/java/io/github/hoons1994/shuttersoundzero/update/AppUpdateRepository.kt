package io.github.hoons1994.shuttersoundzero.update

internal const val RELEASES_PAGE = "https://github.com/hoons1994/ShutterSoundZero/releases/latest"

internal data class LatestRelease(val tag: String, val version: AppVersion) {
    // Derive the destination from the validated tag, never from an arbitrary response URL.
    val pageUrl: String get() = "https://github.com/hoons1994/ShutterSoundZero/releases/tag/$tag"
}

internal enum class UpdateCheckFailure {
    NETWORK, RATE_LIMITED, NO_RELEASE, SERVER, INVALID_RESPONSE, INSTALLED_VERSION
}

internal class UpdateCheckException(val reason: UpdateCheckFailure, cause: Throwable? = null) :
    Exception(reason.name, cause)

internal enum class UpdateStatus { AVAILABLE, UP_TO_DATE, INSTALLED_NEWER }

internal data class UpdateCheckResult(val status: UpdateStatus, val release: LatestRelease)

internal class AppUpdateRepository(private val fetchLatest: suspend () -> LatestRelease) {
    suspend fun check(currentVersion: String): UpdateCheckResult {
        val installed = AppVersion.parse(currentVersion, allowDebugSuffix = true)
            ?: throw UpdateCheckException(UpdateCheckFailure.INSTALLED_VERSION)
        val latest = fetchLatest()
        val status = when {
            latest.version > installed -> UpdateStatus.AVAILABLE
            latest.version == installed -> UpdateStatus.UP_TO_DATE
            else -> UpdateStatus.INSTALLED_NEWER
        }
        return UpdateCheckResult(status, latest)
    }
}
