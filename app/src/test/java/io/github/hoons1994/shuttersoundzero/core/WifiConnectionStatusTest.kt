package io.github.hoons1994.shuttersoundzero.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiConnectionStatusTest {
    @Test
    fun defaultCellularNetworkDoesNotHideConnectedWifi() {
        val inspected = mutableListOf<String>()

        val connected = WifiConnectionStatus.isWifiConnected(
            readNetworks = { listOf("default-cellular", "connected-wifi") },
            readWifiTransport = { network ->
                inspected += network
                network == "connected-wifi"
            }
        )

        assertTrue(connected)
        assertEquals(listOf("default-cellular", "connected-wifi"), inspected)
    }

    @Test
    fun nonWifiNetworksDoNotSatisfyThePairingPrerequisite() {
        assertFalse(WifiConnectionStatus.isWifiConnected(
            readNetworks = { listOf("cellular", "ethernet") },
            readWifiTransport = { false }
        ))
    }

    @Test
    fun noVisibleNetworksCannotSatisfyThePairingPrerequisite() {
        assertFalse(WifiConnectionStatus.isWifiConnected(
            readNetworks = { emptyList<String>() },
            readWifiTransport = { error("No capability lookup should occur") }
        ))
    }

    @Test
    fun disappearedNetworkDoesNotHideAnotherConfirmedWifiNetwork() {
        assertTrue(WifiConnectionStatus.isWifiConnected(
            readNetworks = { listOf("disappeared", "wifi") },
            readWifiTransport = { network -> if (network == "disappeared") null else true }
        ))
    }

    @Test
    fun unknownCapabilitiesDoNotCountAsAWifiConnection() {
        assertFalse(WifiConnectionStatus.isWifiConnected(
            readNetworks = { listOf("unknown", "cellular") },
            readWifiTransport = { network -> if (network == "unknown") null else false }
        ))
    }

    @Test
    fun networkEnumerationFailureFailsClosedAndIsReported() {
        val failure = SecurityException("Network enumeration blocked")
        var reported: Exception? = null

        assertFalse(WifiConnectionStatus.isWifiConnected<String>(
            readNetworks = { throw failure },
            readWifiTransport = { error("Enumeration failed before lookup") },
            onReadFailure = { reported = it }
        ))
        assertSame(failure, reported)
    }

    @Test
    fun capabilityLookupFailureFailsClosedAndIsReported() {
        val failure = SecurityException("Capability lookup blocked")
        var reported: Exception? = null
        val inspected = mutableListOf<String>()

        assertFalse(WifiConnectionStatus.isWifiConnected(
            readNetworks = { listOf("blocked", "wifi") },
            readWifiTransport = { network ->
                inspected += network
                if (network == "blocked") throw failure
                true
            },
            onReadFailure = { reported = it }
        ))
        assertSame(failure, reported)
        assertEquals(listOf("blocked"), inspected)
    }

    @Test
    fun confirmedWifiDoesNotRequireInspectingLaterNetworks() {
        assertTrue(WifiConnectionStatus.isWifiConnected(
            readNetworks = { listOf("wifi", "later-network") },
            readWifiTransport = { network ->
                check(network == "wifi") { "A confirmed Wi-Fi result should finish the check" }
                true
            }
        ))
    }
}
