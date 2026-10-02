package io.github.hoons1994.shuttersoundzero.core

import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WifiConnectionStatusInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    @Suppress("DEPRECATION")
    fun connectedWifiIsAcceptedWhenItIsNotTheDefaultDataNetwork() {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val hasWifiNetwork = cm.allNetworks.any { network ->
            cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        val defaultUsesWifi = cm.activeNetwork?.let { network ->
            cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } == true
        // Run this framework regression on a device with Wi-Fi connected and cellular (or another
        // non-Wi-Fi transport) as its default network. The test never changes network settings.
        assumeTrue("Requires a connected non-default Wi-Fi network", hasWifiNetwork && !defaultUsesWifi)

        assertTrue(WifiConnectionStatus.isWifiConnected(context))
    }

    @Test
    fun missingConnectivityServiceFailsClosed() {
        val unavailableContext = object : ContextWrapper(context) {
            override fun getSystemService(name: String): Any? =
                if (name == Context.CONNECTIVITY_SERVICE) null else super.getSystemService(name)
        }

        assertFalse(WifiConnectionStatus.isWifiConnected(unavailableContext))
    }

    @Test
    fun blockedConnectivityServiceFailsClosed() {
        val blockedContext = object : ContextWrapper(context) {
            override fun getSystemService(name: String): Any? {
                if (name == Context.CONNECTIVITY_SERVICE) throw SecurityException("Service access blocked")
                return super.getSystemService(name)
            }
        }

        assertFalse(WifiConnectionStatus.isWifiConnected(blockedContext))
    }
}
