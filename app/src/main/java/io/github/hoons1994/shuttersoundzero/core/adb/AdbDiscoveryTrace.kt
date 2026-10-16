package io.github.hoons1994.shuttersoundzero.core.adb

internal enum class AdbDiscoveryReason(val priority: Int) {
    UNKNOWN(0), NO_SERVICE(0), RESOLVE_TIMEOUT(0), NO_LOCAL_ENDPOINT(1), INVALID_ENDPOINT(2),
    RESOLVE_FAILED(3), START_FAILED(4)
}

/** Stores fixed categories only; no service names, addresses or ports enter diagnostics. */
internal class AdbDiscoveryTrace {
    private var serviceSeen = false
    private var reason: AdbDiscoveryReason? = null
    private var errorCode: Int? = null

    @Synchronized fun serviceFound() { serviceSeen = true }

    @Synchronized fun failed(failure: AdbDiscoveryReason, code: Int? = null) {
        if (failure.priority >= (reason?.priority ?: -1)) {
            reason = failure
            errorCode = code
        }
    }

    @Synchronized fun failure(): AdbServiceNotFoundException = AdbServiceNotFoundException(
        reason = reason ?: if (serviceSeen) AdbDiscoveryReason.RESOLVE_TIMEOUT else AdbDiscoveryReason.NO_SERVICE,
        nsdErrorCode = errorCode
    )
}
