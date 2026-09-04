package com.charmingcolor.shuttersoundzero.core.adb

import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

internal object LocalAdbEndpointPolicy {
    fun isLocalDeviceAddress(candidate: InetAddress): Boolean {
        return isLocalDeviceAddress(candidate, currentDeviceAddresses())
    }

    internal fun isLocalDeviceAddress(
        candidate: InetAddress,
        deviceAddresses: Iterable<InetAddress>
    ): Boolean {
        if (candidate.isLoopbackAddress) return true
        return deviceAddresses.any { address ->
            addressesMatch(candidate, address)
        }
    }

    private fun addressesMatch(candidate: InetAddress, deviceAddress: InetAddress): Boolean {
        if (!candidate.address.contentEquals(deviceAddress.address)) return false

        if (
            candidate is Inet6Address &&
            deviceAddress is Inet6Address &&
            candidate.isLinkLocalAddress
        ) {
            return candidate.scopeId == deviceAddress.scopeId
        }

        return true
    }

    private fun currentDeviceAddresses(): List<InetAddress> {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            Collections.list(interfaces).flatMap { networkInterface ->
                Collections.list(networkInterface.inetAddresses)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
