package com.charmingcolor.shuttersoundzero.security

/**
 * Keeps only the in-process unlock state.
 *
 * It survives Activity recreation such as rotation/fold posture changes, but resets when
 * the app process is killed. The Activity explicitly locks this session when the app
 * actually leaves the foreground.
 */
object AppLockSession {
    @Volatile
    var isUnlocked: Boolean = false
        private set

    fun unlock() {
        isUnlocked = true
    }

    fun lock() {
        isUnlocked = false
    }
}
