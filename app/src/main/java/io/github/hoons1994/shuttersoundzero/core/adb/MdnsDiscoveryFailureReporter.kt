package io.github.hoons1994.shuttersoundzero.core.adb

/** A single unresolved service must not invalidate other services found by the same discovery. */
internal class MdnsDiscoveryFailureReporter(
    private val diagnostic: (AdbDiscoveryReason, Int?) -> Unit,
    private val fatal: (Int) -> Unit
) {
    fun report(reason: AdbDiscoveryReason, errorCode: Int?) {
        diagnostic(reason, errorCode)
        if (reason == AdbDiscoveryReason.START_FAILED && errorCode != null) fatal(errorCode)
    }
}
