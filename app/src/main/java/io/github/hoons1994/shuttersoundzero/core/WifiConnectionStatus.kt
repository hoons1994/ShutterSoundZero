package io.github.hoons1994.shuttersoundzero.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log

object WifiConnectionStatus {
    private const val TAG = "WifiConnectionStatus"

    /** 기본 데이터망과 별개로 연결된 Wi-Fi를 확인한다. 확인할 수 없는 상태는 연결로 간주하지 않는다. */
    @Suppress("DEPRECATION") // One-shot prerequisite check; do not request or retain a network.
    fun isWifiConnected(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            isWifiConnected(
                readNetworks = { cm.allNetworks.asIterable() },
                readWifiTransport = { network ->
                    cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                },
                onReadFailure = ::logReadFailure
            )
        } catch (e: Exception) {
            logReadFailure(e)
            false
        }
    }

    /** Shared enumeration/lookup boundary so tests exercise the same path used by Android. */
    internal fun <T> isWifiConnected(
        readNetworks: () -> Iterable<T>,
        readWifiTransport: (T) -> Boolean?,
        onReadFailure: (Exception) -> Unit = {}
    ): Boolean = try {
        readNetworks().any { network -> readWifiTransport(network) == true }
    } catch (e: Exception) {
        onReadFailure(e)
        false
    }

    private fun logReadFailure(error: Exception) {
        Log.w(TAG, "Unable to determine Wi-Fi connectivity (${error.javaClass.simpleName})")
    }
}
