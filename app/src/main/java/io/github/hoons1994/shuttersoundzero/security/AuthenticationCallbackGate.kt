package io.github.hoons1994.shuttersoundzero.security

/** Delivers one result, and defers a credential success until its owner is foreground again. */
internal class AuthenticationCallbackGate {
    private var active = true
    private var pendingSuccess = false

    fun succeed(isResumed: Boolean): Boolean {
        if (!active || pendingSuccess) return false
        if (!isResumed) {
            pendingSuccess = true
            return false
        }
        active = false
        return true
    }

    fun resume(): Boolean {
        if (!active || !pendingSuccess) return false
        pendingSuccess = false
        active = false
        return true
    }

    fun fail(): Boolean {
        if (!active || pendingSuccess) return false
        active = false
        return true
    }

    fun cancel() {
        active = false
        pendingSuccess = false
    }
}
