package io.github.hoons1994.shuttersoundzero.core.adb

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 30, maxSdkVersion = 33)
class LegacyNsdResolveServiceInstrumentedTest {
    @Test
    fun workerDeathDisconnectsClientAndNextBindingStarts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val connected = CountDownLatch(1)
        val disconnected = CountDownLatch(1)
        var worker: Messenger? = null
        val first = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                worker = Messenger(service)
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) {
                disconnected.countDown()
            }
        }

        assertTrue(context.bindService(
            Intent(context, LegacyNsdResolveService::class.java),
            first,
            Context.BIND_AUTO_CREATE
        ))
        try {
            assertTrue("Worker did not bind", connected.await(10, TimeUnit.SECONDS))
            val boundWorker = requireNotNull(worker)
            val replied = CountDownLatch(1)
            val replyType = AtomicInteger()
            val receiver = Messenger(Handler(Looper.getMainLooper()) { response ->
                replyType.set(response.what)
                replied.countDown()
                true
            })
            boundWorker.send(Message.obtain(null, LegacyNsdProtocol.RESOLVE).apply {
                arg1 = 1
                obj = NsdServiceInfo() // Invalid input avoids depending on a live mDNS service.
                replyTo = receiver
            })
            assertTrue("Worker did not answer IPC request", replied.await(10, TimeUnit.SECONDS))
            assertEquals(LegacyNsdProtocol.FAILED, replyType.get())

            boundWorker.send(Message.obtain(null, LegacyNsdProtocol.SHUTDOWN))
            assertTrue("Worker death was not observed", disconnected.await(10, TimeUnit.SECONDS))
        } finally {
            context.unbindService(first)
        }

        val rebound = CountDownLatch(1)
        val second = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                rebound.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        assertTrue(context.bindService(
            Intent(context, LegacyNsdResolveService::class.java),
            second,
            Context.BIND_AUTO_CREATE
        ))
        try {
            assertTrue("Replacement worker did not bind", rebound.await(10, TimeUnit.SECONDS))
        } finally {
            context.unbindService(second)
        }
    }
}
