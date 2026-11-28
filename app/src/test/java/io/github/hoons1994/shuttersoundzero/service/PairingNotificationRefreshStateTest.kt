package io.github.hoons1994.shuttersoundzero.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingNotificationRefreshStateTest {
    @Test fun oldDiscoveryFailureCannotChangeReplacementRecovery() {
        val generation = OperationGeneration()
        val old = generation.next()
        val ports = io.github.hoons1994.shuttersoundzero.core.adb.PairingDiscoveryPortState()
        val oldPorts = ports.begin()
        generation.next()
        val currentPorts = ports.begin()
        refresh.reset(enabled)
        org.junit.Assert.assertFalse(generation.runIfCurrent(old) { refresh.discoveryFailed(3) })
        org.junit.Assert.assertFalse(ports.onPairingPort(oldPorts, 12345) { refresh.portDiscovered() })
        ports.onPairingPort(currentPorts, 37123) { refresh.portDiscovered() }
        assertEquals(PairingNotificationRefreshState.Update.PortDiscovered, refresh.takeUpdate(false))
        assertEquals(37123, ports.pairingPort)
    }

    @Test fun optionalConnectStartFailureBeforeOrAfterPairingPortKeepsCodeEntry() {
        for (failFirst in listOf(true, false)) {
            val ports = io.github.hoons1994.shuttersoundzero.core.adb.PairingDiscoveryPortState()
            val token = ports.begin()
            val refresh = PairingNotificationRefreshState().apply { reset(PairingSetupState(true, true)) }
            fun failConnect() {
                val listener = io.github.hoons1994.shuttersoundzero.core.adb.startOptionalConnectDiscovery(
                    start = { throw IllegalStateException("fake connect start failure") },
                    onFailure = { refresh.discoveryFailed(0, io.github.hoons1994.shuttersoundzero.core.adb.PairingDiscoverySource.CONNECT) })
                assertNull(listener)
            }
            if (failFirst) failConnect()
            ports.onPairingPort(token, 37123) { refresh.portDiscovered() }
            if (!failFirst) failConnect()
            assertEquals(37123, ports.pairingPort)
            assertEquals(PairingNotificationRefreshState.Update.PortDiscovered, refresh.takeUpdate(false))
            assertNull(refresh.takeUpdate(false))
        }
    }

    @Test fun connectFailureDoesNotErasePendingPairingFailure() {
        refresh.discoveryFailed(3)
        refresh.discoveryFailed(0, io.github.hoons1994.shuttersoundzero.core.adb.PairingDiscoverySource.CONNECT)
        refresh.portDiscovered()
        assertEquals(PairingNotificationRefreshState.Update.DiscoveryFailed(3), refresh.takeUpdate(false))
    }

    @Test fun lostPortRefreshIsDeferredDuringPairingAndCannotHideDiscoveryFailure() {
        refresh.portLost()
        assertNull(refresh.takeUpdate(blocked = true))
        assertEquals(PairingNotificationRefreshState.Update.PortDiscovered, refresh.takeUpdate(blocked = false))
        refresh.discoveryFailed(3)
        refresh.portLost()
        assertEquals(PairingNotificationRefreshState.Update.DiscoveryFailed(3), refresh.takeUpdate(blocked = false))
    }
    @Test
    fun readFailureInvalidatesStaleDiscoveryAndRecoveryRestartsTheSetupState() {
        val unknown = PairingSetupState(true, null)
        refresh.portDiscovered()
        refresh.observeSetup(unknown)
        assertEquals(PairingNotificationRefreshState.Update.Setup(unknown), refresh.takeUpdate(false))
        assertNull(refresh.takeUpdate(false))
        refresh.observeSetup(enabled)
        assertEquals(PairingNotificationRefreshState.Update.Setup(enabled), refresh.takeUpdate(false))
    }

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
