package io.github.hoons1994.shuttersoundzero.service

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import io.github.hoons1994.shuttersoundzero.core.CscMuteManager

/** Non-active tiles observe CSC only while System UI is listening. */
internal class TileCscObserver(
    private val resolver: ContentResolver,
    private val refresh: () -> Unit
) {
    private var listening = false
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (listening) refresh()
        }
    }

    fun start() {
        if (listening) return
        resolver.registerContentObserver(Settings.System.getUriFor(CscMuteManager.CSC_KEY), false, observer)
        listening = true
        // Re-read after registration to close the read/register race.
        refresh()
    }

    fun stop() {
        if (!listening) return
        listening = false
        resolver.unregisterContentObserver(observer)
    }
}
