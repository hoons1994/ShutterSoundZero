package io.github.hoons1994.shuttersoundzero.core.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingDiscoveryPortStateTest {
    @Test
    fun lateCallbacksFromPreviousSessionCannotOverwriteNewPortsOrNotify() {
        val ports = PairingDiscoveryPortState()
        val oldSession = ports.begin()
        ports.onPairingPort(oldSession, 37123) {}
        ports.onConnectPort(oldSession, 37124) {}

        ports.stop()
        val newSession = ports.begin()
        var notifications = 0
        assertTrue(ports.onPairingPort(newSession, 48123) { notifications++ })
        assertTrue(ports.onConnectPort(newSession, 48124) { notifications++ })

        assertFalse(ports.onPairingPort(oldSession, 37123) { notifications++ })
        assertFalse(ports.onConnectPort(oldSession, 37124) { notifications++ })
        assertEquals(48123, ports.pairingPort)
        assertEquals(48124, ports.connectPort)
        assertEquals(2, notifications)
    }

    @Test
    fun stoppedSessionRejectsLateCallbacksButKeepsConnectPortForFollowup() {
        val ports = PairingDiscoveryPortState()
        val session = ports.begin()
        ports.onConnectPort(session, 37124) {}

        ports.stop()

        assertFalse(ports.onConnectPort(session, 48124) {})
        assertEquals(37124, ports.connectPort)
        ports.rememberConnectedPort(48124)
        assertEquals(48124, ports.connectPort)

        ports.clearPorts()
        assertNull(ports.pairingPort)
        assertNull(ports.connectPort)
    }
}
