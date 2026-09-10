package com.charmingcolor.shuttersoundzero.core

/**
 * Resolves the persisted mute preference only after the requested CSC state has been verified.
 * Failed attempts preserve the user's previously confirmed boot preference.
 */
internal object CscTogglePersistencePolicy {
    fun resolve(
        previousDesiredMute: Boolean,
        targetMuted: Boolean,
        stateApplied: Boolean
    ): Boolean = if (stateApplied) targetMuted else previousDesiredMute
}
