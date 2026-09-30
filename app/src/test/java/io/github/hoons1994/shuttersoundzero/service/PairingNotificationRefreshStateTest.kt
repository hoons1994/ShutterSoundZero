package io.github.hoons1994.shuttersoundzero.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingNotificationRefreshStateTest {
    private val enabled = PairingSetupState(true, true)
    private val wirelessOff = PairingSetupState(true, false)
    private val refresh = PairingNotificationRefreshState().apply { reset(enabled) }

    @Test
    fun settingsChangedDuringPairingAreAppliedAfterTheJobClears() {
        refresh.observeSetup(wirelessOff)
        assertNull(refresh.takeUpdate(blocked = true))
        // Re-reading the same settings must not discard the deferred change.
        refresh.observeSetup(wirelessOff)
        assertEquals(
            PairingNotificationRefreshState.Update.Setup(wirelessOff),
            refresh.takeUpdate(blocked = false)
        )
        assertNull(refresh.takeUpdate(blocked = false))
    }

    @Test
    fun offOnCycleDuringPairingStillRequiresFreshDiscovery() {
        refresh.observeSetup(wirelessOff)
        assertNull(refresh.takeUpdate(blocked = true))
        refresh.observeSetup(enabled)
        assertNull(refresh.takeUpdate(blocked = true))
        assertEquals(
            PairingNotificationRefreshState.Update.Setup(enabled),
            refresh.takeUpdate(blocked = false)
        )
    }

    @Test
    fun portFoundBeforeTheJobClearsRestoresCodeEntryAfterwards() {
        refresh.portDiscovered()
        assertNull(refresh.takeUpdate(blocked = true))
        assertEquals(
            PairingNotificationRefreshState.Update.PortDiscovered,
            refresh.takeUpdate(blocked = false)
        )
        assertNull(refresh.takeUpdate(blocked = false))
    }

    @Test
    fun portFoundAfterTheJobClearsRestoresCodeEntryImmediately() {
        assertNull(refresh.takeUpdate(blocked = false))
        refresh.portDiscovered()
        assertEquals(
            PairingNotificationRefreshState.Update.PortDiscovered,
            refresh.takeUpdate(blocked = false)
        )
    }

    @Test
    fun asyncFailureDoesNotReplaceProgressButIsShownAfterTheJobClears() {
        refresh.discoveryFailed(3)
        assertNull(refresh.takeUpdate(blocked = true))
        assertEquals(
            PairingNotificationRefreshState.Update.DiscoveryFailed(3),
            refresh.takeUpdate(blocked = false)
        )
    }

    @Test
    fun asyncFailureWithoutAnActiveJobIsShownImmediately() {
        refresh.discoveryFailed(0)
        assertEquals(
            PairingNotificationRefreshState.Update.DiscoveryFailed(0),
            refresh.takeUpdate(blocked = false)
        )
    }

    @Test
    fun asyncFailureInvalidatesAPortFoundEarlierDuringPairing() {
        refresh.portDiscovered()
        refresh.discoveryFailed(3)
        assertNull(refresh.takeUpdate(blocked = true))
        assertEquals(
            PairingNotificationRefreshState.Update.DiscoveryFailed(3),
            refresh.takeUpdate(blocked = false)
        )
    }

    @Test
    fun aParallelListenerCannotHideAPendingFailureWithALaterPort() {
        refresh.discoveryFailed(3)
        refresh.portDiscovered()
        assertNull(refresh.takeUpdate(blocked = true))
        assertEquals(
            PairingNotificationRefreshState.Update.DiscoveryFailed(3),
            refresh.takeUpdate(blocked = false)
        )
    }

    @Test
    fun restartingDiscoveryAllowsAFreshPortAfterAPreviousFailure() {
        refresh.discoveryFailed(3)
        refresh.clearDiscovery()
        refresh.portDiscovered()
        assertEquals(
            PairingNotificationRefreshState.Update.PortDiscovered,
            refresh.takeUpdate(blocked = false)
        )
    }

    @Test
    fun settingsChangeTakesPriorityOverAnOldEndpoint() {
        refresh.portDiscovered()
        refresh.observeSetup(wirelessOff)
        assertEquals(
            PairingNotificationRefreshState.Update.Setup(wirelessOff),
            refresh.takeUpdate(blocked = false)
        )
        assertNull(refresh.takeUpdate(blocked = false))
    }

    @Test
    fun settingsChangeTakesPriorityOverAnOldDiscoveryFailure() {
        refresh.discoveryFailed(3)
        refresh.observeSetup(wirelessOff)
        refresh.observeSetup(enabled)
        assertEquals(
            PairingNotificationRefreshState.Update.Setup(enabled),
            refresh.takeUpdate(blocked = false)
        )
        assertNull(refresh.takeUpdate(blocked = false))
    }

    @Test
    fun stoppingDiscoveryDiscardsItsPendingCallbackButKeepsSettingsChanges() {
        refresh.observeSetup(wirelessOff)
        refresh.portDiscovered()
        refresh.clearDiscovery()
        assertEquals(
            PairingNotificationRefreshState.Update.Setup(wirelessOff),
            refresh.takeUpdate(blocked = false)
        )
        assertNull(refresh.takeUpdate(blocked = false))
    }

    @Test
    fun newSetupDiscardsDeferredEventsFromThePreviousAttempt() {
        refresh.observeSetup(wirelessOff)
        refresh.discoveryFailed(3)
        refresh.reset(enabled)
        assertNull(refresh.takeUpdate(blocked = false))
    }
}
