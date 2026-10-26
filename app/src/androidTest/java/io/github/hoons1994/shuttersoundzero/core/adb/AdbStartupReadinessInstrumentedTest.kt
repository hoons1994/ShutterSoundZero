package io.github.hoons1994.shuttersoundzero.core.adb

import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Advertises a closed real loopback port, then starts its listener six seconds later. */
class AdbStartupReadinessInstrumentedTest {
    @Test fun fastConnectionRefusalsWaitForTheActualListenerWithoutPairingRecovery() = runBlocking<Unit> {
        assumeTrue("Only a disposable emulator may run this network fixture",
            Build.FINGERPRINT.startsWith("generic") || Build.FINGERPRINT.startsWith("google/sdk_gphone") ||
                Build.MODEL.contains("sdk_gphone"))
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // This loopback fixture must leave the fresh-install runtime permission test untouched.
        val permissionBefore = LocalNetworkAccess.isGranted(context)
        val loopback = InetAddress.getByName("127.0.0.1")
        val port = ServerSocket(0, 1, loopback).use { it.localPort }
        val server = AtomicReference<ServerSocket?>()
        val stopping = AtomicBoolean(false)
        val serverFailure = AtomicReference<Throwable?>()
        val listener = Thread({
            try {
                Thread.sleep(6_000L)
                ServerSocket(port, 1, loopback).use {
                    server.set(it)
                    it.soTimeout = 10_000
                    it.accept().use { }
                }
            } catch (error: Throwable) {
                if (!stopping.get()) serverFailure.set(error)
            }
        }, "SSZ-StartupFixture").apply { isDaemon = true }
        var attempts = 0
        val started = SystemClock.elapsedRealtime()
        listener.start()
        try {
            val resolved = AdbConnectionResolver.resolve(
                candidatePorts = emptyList(),
                connect = { candidate ->
                    attempts++
                    Socket().use {
                        it.bind(InetSocketAddress(loopback, 0))
                        if (it.localPort == candidate) throw java.net.ConnectException("Fixture port collision")
                        it.connect(InetSocketAddress(loopback, candidate), 1_000)
                    }
                    true
                },
                discover = { port }
            )
            assertEquals(port, resolved)
            assertTrue("Three fast failures must not end the operation", attempts > 3)
            assertTrue("Must wait for the real listener", SystemClock.elapsedRealtime() - started >= 6_000L)
            android.util.Log.i("SSZ-StartupFixture", "ready_after_ms=${SystemClock.elapsedRealtime() - started}; attempts=$attempts")
        } finally {
            stopping.set(true)
            listener.interrupt()
            server.get()?.close()
            listener.join(1_500L)
            assertFalse("Fixture listener did not stop", listener.isAlive)
            serverFailure.get()?.let { throw AssertionError("Fixture listener failed", it) }
            assertEquals("Network fixture must not change runtime permission grants",
                permissionBefore, LocalNetworkAccess.isGranted(context))
        }
    }
}
