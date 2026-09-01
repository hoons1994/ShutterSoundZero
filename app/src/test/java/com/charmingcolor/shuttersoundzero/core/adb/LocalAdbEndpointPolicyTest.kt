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
    fun `accepts loopback even when interface enumeration is unavailable`() {
        val loopback = InetAddress.getByName("127.0.0.1")

        assertTrue(LocalAdbEndpointPolicy.isLocalDeviceAddress(loopback, emptyList()))
    }

    @Test
    fun `rejects a different network device`() {
        val candidate = InetAddress.getByName("192.0.2.20")
        val localAddress = InetAddress.getByName("192.0.2.10")

        assertFalse(LocalAdbEndpointPolicy.isLocalDeviceAddress(candidate, listOf(localAddress)))
    }

    @Test
    fun `matches IPv6 addresses by bytes rather than display form`() {
        val candidate = InetAddress.getByName("2001:db8::1")
        val localAddress = InetAddress.getByAddress(candidate.address)

        assertTrue(LocalAdbEndpointPolicy.isLocalDeviceAddress(candidate, listOf(localAddress)))
    }
}
