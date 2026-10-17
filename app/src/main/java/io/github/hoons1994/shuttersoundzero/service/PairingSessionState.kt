package io.github.hoons1994.shuttersoundzero.service

import java.util.UUID

/** Main-thread lifecycle state shared by command admission and notification recovery. */
internal class PairingSessionState {
    var id: String? = null
        private set
    val isActive: Boolean get() = id != null
    var hasPaired = false
        private set

    fun begin(paired: Boolean = false): String {
        id = UUID.randomUUID().toString()
        hasPaired = paired
        return requireNotNull(id)
    }

    fun end() {
        id = null
        hasPaired = false
    }

    fun markPaired() {
        if (isActive) hasPaired = true
    }

    /** Delayed notification actions cannot control a replacement or recreated service session. */
    fun dispatchCommand(sessionId: String?, onAccepted: () -> Unit): Boolean {
        if (sessionId == null || sessionId != id) return false
        onAccepted()
        return true
    }

    fun dispatchCode(sessionId: String?, onPair: () -> Unit, onRetryApply: () -> Unit): Boolean =
        dispatchCommand(sessionId) {
            if (hasPaired) onRetryApply() else onPair()
        }
}
