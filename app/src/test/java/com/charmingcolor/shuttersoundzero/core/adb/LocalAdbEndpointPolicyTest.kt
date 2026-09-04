package com.charmingcolor.shuttersoundzero.core.adb

import java.net.InetAddress
import kotlin.random.Random
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

    @Test
    fun `for many IPv4 addresses exact byte matches are accepted and mutations rejected`() {
        val random = Random(0x1A2B3C4D)

        repeat(512) { index ->
            val bytes = ByteArray(4)
            random.nextBytes(bytes)
            bytes[0] = 10
            val candidate = InetAddress.getByAddress(bytes)
            val byteEquivalent = InetAddress.getByAddress(bytes.copyOf())

            assertTrue("exact IPv4 iteration=$index", LocalAdbEndpointPolicy.isLocalDeviceAddress(candidate, listOf(byteEquivalent)))

            val mutated = bytes.copyOf()
            mutated[3] = (mutated[3].toInt() xor 0x01).toByte()
            val differentDevice = InetAddress.getByAddress(mutated)

            assertFalse("mutated IPv4 iteration=$index", LocalAdbEndpointPolicy.isLocalDeviceAddress(candidate, listOf(differentDevice)))
        }
    }

    @Test
    fun `for many IPv6 addresses exact byte matches are accepted and mutations rejected`() {
        val random = Random(0x6A09E667)

        repeat(512) { index ->
            val bytes = ByteArray(16)
            random.nextBytes(bytes)
            bytes[0] = 0x20
            bytes[1] = 0x01
            val candidate = InetAddress.getByAddress(bytes)
            val byteEquivalent = InetAddress.getByAddress(bytes.copyOf())

            assertTrue("exact IPv6 iteration=$index", LocalAdbEndpointPolicy.isLocalDeviceAddress(candidate, listOf(byteEquivalent)))

            val mutated = bytes.copyOf()
            mutated[15] = (mutated[15].toInt() xor 0x01).toByte()
            val differentDevice = InetAddress.getByAddress(mutated)

            assertFalse("mutated IPv6 iteration=$index", LocalAdbEndpointPolicy.isLocalDeviceAddress(candidate, listOf(differentDevice)))
        }
    }

    @Test
    fun `for many address lists only a byte-identical member grants access`() {
        val random = Random(0xC0FFEE)

        repeat(256) { index ->
            val candidateBytes = byteArrayOf(
                10,
                random.nextInt(0, 256).toByte(),
                random.nextInt(0, 256).toByte(),
                random.nextInt(0, 256).toByte()
            )
            val candidate = InetAddress.getByAddress(candidateBytes)
            val unrelated = List(8) {
                val bytes = candidateBytes.copyOf()
                bytes[3] = (bytes[3].toInt() xor (it + 1)).toByte()
                InetAddress.getByAddress(bytes)
            }

            assertFalse("without match iteration=$index", LocalAdbEndpointPolicy.isLocalDeviceAddress(candidate, unrelated))

            val withMatch = unrelated.toMutableList().apply {
                add(random.nextInt(size + 1), InetAddress.getByAddress(candidateBytes.copyOf()))
            }
            assertTrue("with match iteration=$index", LocalAdbEndpointPolicy.isLocalDeviceAddress(candidate, withMatch))
        }
    }
}
