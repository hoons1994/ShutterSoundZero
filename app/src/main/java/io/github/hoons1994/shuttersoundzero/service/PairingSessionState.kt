package io.github.hoons1994.shuttersoundzero.service

/** Main-thread lifecycle state shared by command admission and notification recovery. */
internal class PairingSessionState {
    var isActive = false
        private set
    var hasPaired = false
        private set

    fun begin(paired: Boolean = false) {
        isActive = true
        hasPaired = paired
    }

    fun end() {
        isActive = false
        hasPaired = false
    }

    fun markPaired() {
        if (isActive) hasPaired = true
    }

    /** A delayed RemoteInput must not resurrect a cancelled or destroyed service session. */
    fun dispatchCode(onPair: () -> Unit, onRetryApply: () -> Unit): Boolean {
        if (!isActive) return false
        if (hasPaired) onRetryApply() else onPair()
        return true
    }
}
