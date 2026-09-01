package com.charmingcolor.shuttersoundzero.core.adb

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
            candidate.address.contentEquals(address.address)
        }
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
