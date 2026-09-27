package io.github.hoons1994.shuttersoundzero.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log

object WifiConnectionStatus {
    private const val TAG = "WifiConnectionStatus"

    /** 네트워크 상태를 읽지 못하면 무선 ADB 전제조건을 확인할 수 없으므로 false를 반환한다. */
    fun isWifiConnected(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to determine Wi-Fi connectivity (${e.javaClass.simpleName})")
            false
        }
    }
}
