package io.github.hoons1994.shuttersoundzero.service

/** Main-thread state: retain callbacks while progress owns the notification. */
internal class PairingNotificationRefreshState {
    sealed interface Update {
        data class Setup(val state: PairingSetupState) : Update
        data object PortDiscovered : Update
        data class DiscoveryFailed(val errorCode: Int) : Update
    }

    private var setupState: PairingSetupState? = null
    private var setupChanged = false
    private var discoveryUpdate: Update? = null

    fun reset(state: PairingSetupState) {
        setupState = state
        setupChanged = false
        clearDiscovery()
    }

    fun observeSetup(state: PairingSetupState) {
        if (state != setupState) {
            setupState = state
            // Keep this set even if wireless debugging is switched off and back on.
            setupChanged = true
        }
    }

    fun portDiscovered() {
        // One listener can find a port after the other listener has already failed.
        if (discoveryUpdate !is Update.DiscoveryFailed) {
            discoveryUpdate = Update.PortDiscovered
        }
    }

    fun discoveryFailed(errorCode: Int) {
        discoveryUpdate = Update.DiscoveryFailed(errorCode)
    }

    fun clearDiscovery() {
        discoveryUpdate = null
    }

    fun takeUpdate(blocked: Boolean): Update? {
        if (blocked) return null
        if (setupChanged) {
            setupChanged = false
            // A prerequisite change invalidates endpoints and errors from the old session.
            clearDiscovery()
            return Update.Setup(requireNotNull(setupState))
        }
        return discoveryUpdate.also { discoveryUpdate = null }
    }
}
