package io.github.hoons1994.shuttersoundzero.core

import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WifiConnectionStatusInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun connectedWifiIsAcceptedWhenItIsNotTheDefaultDataNetwork() {
        // Real Android transport fixtures, controlled ordering. CI does not promise a
        // physical secondary Wi-Fi connection. Exercise the shared production lookup path
        // without changing the emulator's routes or skipping the positive regression.
        val defaultCellular = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR).build()
        val secondaryWifi = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        assertTrue(WifiConnectionStatus.isWifiConnected(
            readNetworks = { listOf(defaultCellular, secondaryWifi) },
            readWifiTransport = { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }
        ))
    }

    @Test
    @Suppress("DEPRECATION")
    fun actualSystemQueryMatchesStableFrameworkCapabilities() {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        fun snapshot() = cm.allNetworks.associate { network ->
            network.toString() to cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            val before = snapshot()
            val actual = WifiConnectionStatus.isWifiConnected(context)
            if (before == snapshot()) {
                assertEquals(before.values.any { it == true }, actual)
                return
            }
            Thread.sleep(50)
        }
        fail("Framework networks did not stabilize for the system-query comparison")
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
