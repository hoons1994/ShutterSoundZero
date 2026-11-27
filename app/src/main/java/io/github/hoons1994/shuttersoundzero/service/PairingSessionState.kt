package io.github.hoons1994.shuttersoundzero.service

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow

internal enum class NotificationRecovery { IDLE, SEARCHING, CODE_AVAILABLE }

internal data class PairingCodePrompt(
    val sessionId: String,
    val revision: Long,
    val submitting: Boolean = false,
    val recovery: NotificationRecovery = NotificationRecovery.IDLE
)

/** Main-thread lifecycle state shared by command admission and notification recovery. */
internal class PairingSessionState(
    private val codePrompt: MutableStateFlow<PairingCodePrompt?> = MutableStateFlow(null)
) {
    var id: String? = null
        private set
    val isActive: Boolean get() = id != null
    var hasPaired = false
        private set

    var targetMuted: Boolean = true
        private set

    var notificationDismissed = false
        private set
    private var recoveryConfirmationShown = false

    fun begin(paired: Boolean = false, targetMuted: Boolean = true, revision: Long = 0, recovering: Boolean = false): String {
        recoveryConfirmationShown = false
        id = UUID.randomUUID().toString()
        hasPaired = paired
        this.targetMuted = targetMuted
        notificationDismissed = false
        codePrompt.value = if (paired) null else PairingCodePrompt(
            requireNotNull(id), revision,
            recovery = if (recovering) NotificationRecovery.SEARCHING else NotificationRecovery.IDLE
        )
        return requireNotNull(id)
    }

    fun end() {
        id = null
        hasPaired = false
        codePrompt.value = null
    }

    fun dismissNotification(sessionId: String?) {
        if (sessionId != null && sessionId == id && !hasPaired) {
            notificationDismissed = true
            finishNotificationRecovery(sessionId)
        }
    }

    fun finishNotificationRecovery(sessionId: String) {
        val prompt = codePrompt.value ?: return
        if (prompt.sessionId == sessionId) codePrompt.value = prompt.copy(recovery = NotificationRecovery.IDLE)
    }

    fun requestDismissalConfirmation(
        sessionId: String?,
        revision: Long,
        notificationsEnabled: () -> Boolean,
        showConfirmation: () -> Unit
    ) {
        val prompt = codePrompt.value ?: return
        if (prompt.sessionId != sessionId || prompt.revision != revision || prompt.submitting ||
            hasPaired || !notificationDismissed || recoveryConfirmationShown) return
        recoveryConfirmationShown = true
        if (notificationsEnabled()) showConfirmation()
    }

    fun updateRecoveryPort(available: Boolean) {
        val prompt = codePrompt.value ?: return
        if (prompt.recovery != NotificationRecovery.IDLE) {
            codePrompt.value = prompt.copy(recovery = if (available) NotificationRecovery.CODE_AVAILABLE else NotificationRecovery.SEARCHING)
        }
    }

    fun setSubmitting(submitting: Boolean) {
        codePrompt.value = if (hasPaired && !submitting) null else codePrompt.value?.copy(submitting = submitting)
    }

    fun recoverNotification(sessionId: String?, revision: Long, notificationsEnabled: () -> Boolean,
                            onBlocked: () -> Unit, rediscover: (Boolean) -> Unit) {
        val prompt = codePrompt.value ?: return
        if (prompt.sessionId != sessionId || prompt.revision != revision || prompt.submitting || prompt.recovery != NotificationRecovery.IDLE || hasPaired) return
        if (notificationsEnabled()) rediscover(targetMuted) else onBlocked()
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
