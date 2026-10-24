package io.github.hoons1994.shuttersoundzero.service

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hoons1994.shuttersoundzero.core.CscMuteManager
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class TileCscObserverInstrumentedTest {
    @Test fun lateCscChangeRefreshesVisibleTileAndStopsAfterListeningEnds() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val refreshes = AtomicInteger()
        val changed = CountDownLatch(1)
        val observer = TileCscObserver(context.contentResolver) {
            if (refreshes.incrementAndGet() > 1) changed.countDown()
        }
        try {
            instrumentation.runOnMainSync { observer.start(); observer.start() }
            assertEquals(1, refreshes.get())
            val uri = Settings.System.getUriFor(CscMuteManager.CSC_KEY)
            context.contentResolver.notifyChange(uri, null)
            assertTrue("Visible tile must receive a late CSC change", changed.await(3, TimeUnit.SECONDS))
            instrumentation.runOnMainSync { observer.stop(); observer.stop() }
            val stoppedAt = refreshes.get()
            context.contentResolver.notifyChange(uri, null)
            instrumentation.waitForIdleSync()
            assertEquals(stoppedAt, refreshes.get())
            instrumentation.runOnMainSync { observer.start() }
            assertEquals(stoppedAt + 1, refreshes.get())
        } finally {
            instrumentation.runOnMainSync { observer.stop() }
        }
    }
}
