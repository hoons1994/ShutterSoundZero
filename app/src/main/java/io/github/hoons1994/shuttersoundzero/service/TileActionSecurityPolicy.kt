package io.github.hoons1994.shuttersoundzero.service

internal enum class TileActionSecurityDecision {
    REQUEST_DEVICE_UNLOCK,
    REQUEST_APP_AUTHENTICATION,
    EXECUTE
}

internal object TileActionSecurityPolicy {
    fun decide(
        isDeviceLocked: Boolean,
        isAppLockEnabled: Boolean
    ): TileActionSecurityDecision = when {
        isDeviceLocked -> TileActionSecurityDecision.REQUEST_DEVICE_UNLOCK
        isAppLockEnabled -> TileActionSecurityDecision.REQUEST_APP_AUTHENTICATION
        else -> TileActionSecurityDecision.EXECUTE
    }
}
