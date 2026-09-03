package com.charmingcolor.shuttersoundzero.core.adb

import java.net.InetAddress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAdbEndpointPolicyTest {
    @Test
    fun `accepts an IPv4 address assigned to this device`() {
        val address = InetAddress.getByName("192.0.2.10")

        assertTrue(LocalAdbEndpointPolicy.isLocalDeviceAddress(address, listOf(address)))
    }

    @Test
    fun `accepts IPv4 loopback even when interface enumeration is unavailable`() {
        val loopback = InetAddress.getByName("127.0.0.1")

        assertTrue(LocalAdbEndpointPolicy.isLocalDeviceAddress(loopback, emptyList()))
    }

    @Test
    fun `accepts IPv6 loopback even when interface enumeration is unavailable`() {
        val loopback = InetAddress.getByName("::1")

        assertTrue(LocalAdbEndpointPolicy.isLocalDeviceAddress(loopback, emptyList()))
    }

    @Test
    fun `rejects a different network device`() {
        val candidate = InetAddress.getByName("192.0.2.20")
        val localAddress = InetAddress.getByName("192.0.2.10")

        assertFalse(LocalAdbEndpointPolicy.isLocalDeviceAddress(candidate, listOf(localAddress)))
    }

    @Test
    fun `matches candidate against any address assigned to this device`() {
        val candidate = InetAddress.getByName("192.0.2.30")
        val deviceAddresses = listOf(
            InetAddress.getByName("192.0.2.10"),
            InetAddress.getByName("192.0.2.20"),
            InetAddress.getByName("192.0.2.30")
        )

        assertTrue(LocalAdbEndpointPolicy.isLocalDeviceAddress(candidate, deviceAddresses))
    }

    @Test
    fun `matches IPv6 addresses by bytes rather than display form`() {
        val candidate = InetAddress.getByName("2001:db8::1")
        val localAddress = InetAddress.getByAddress(candidate.address)

        assertTrue(LocalAdbEndpointPolicy.isLocalDeviceAddress(candidate, listOf(localAddress)))
    }

    @Test
    fun `does not confuse IPv4 and IPv6 addresses`() {
        val candidate = InetAddress.getByName("192.0.2.10")
        val localAddress = InetAddress.getByName("2001:db8::10")

        assertFalse(LocalAdbEndpointPolicy.isLocalDeviceAddress(candidate, listOf(localAddress)))
    }

    @Test
    fun `rejects non-loopback candidate when no device addresses are available`() {
        val candidate = InetAddress.getByName("192.0.2.10")

        assertFalse(LocalAdbEndpointPolicy.isLocalDeviceAddress(candidate, emptyList()))
    }
}
