package com.charmingcolor.shuttersoundzero.receiver

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.core.app.RemoteInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.charmingcolor.shuttersoundzero.service.PairingForegroundService
import com.charmingcolor.shuttersoundzero.ui.notification.PairingNotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PairingNotificationReceiverInstrumentedTest {

    private val baseContext: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun remoteInput_trimsCodeAndForwardsSubmitCommand() {
        val context = RecordingContext(baseContext)
        val intent = Intent(PairingNotificationHelper.ACTION_SUBMIT_PAIRING_CODE)
        val remoteInput = RemoteInput.Builder(PairingNotificationHelper.KEY_PAIRING_CODE).build()
        val results = Bundle().apply {
            putCharSequence(PairingNotificationHelper.KEY_PAIRING_CODE, " 123456 ")
        }
        RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, results)

        PairingNotificationReceiver().onReceive(context, intent)

        val forwarded = context.lastForegroundServiceIntent
        requireNotNull(forwarded)
        assertEquals(
            "com.charmingcolor.shuttersoundzero.action.SUBMIT_PAIRING_CODE",
            forwarded.action
        )
        assertEquals("123456", forwarded.getStringExtra("pairing_code"))
        assertEquals(PairingForegroundService::class.java.name, forwarded.component?.className)
        assertNull(context.lastStartedServiceIntent)
    }

    @Test
    fun cancelAction_forwardsStopCommandWithoutStartingPairing() {
        val context = RecordingContext(baseContext)

        PairingNotificationReceiver().onReceive(
            context,
            Intent(PairingNotificationHelper.ACTION_CANCEL_PAIRING)
        )

        val forwarded = context.lastStartedServiceIntent
        requireNotNull(forwarded)
        assertEquals(
            "com.charmingcolor.shuttersoundzero.action.STOP_PAIRING",
            forwarded.action
        )
        assertEquals(PairingForegroundService::class.java.name, forwarded.component?.className)
        assertNull(context.lastForegroundServiceIntent)
    }

    @Test
    fun serviceCompanion_buildsStartAndCompleteTransitions() {
        val context = RecordingContext(baseContext)

        PairingForegroundService.start(context, isDevOptionsOff = true)
        val startIntent = context.lastForegroundServiceIntent
        requireNotNull(startIntent)
        assertEquals(
            "com.charmingcolor.shuttersoundzero.action.START_PAIRING",
            startIntent.action
        )
        assertTrue(startIntent.getBooleanExtra("dev_options_off", false))

        context.reset()
        PairingForegroundService.complete(context, wirelessDebuggingDisabled = true)
        val completeIntent = context.lastStartedServiceIntent
        requireNotNull(completeIntent)
        assertEquals(
            "com.charmingcolor.shuttersoundzero.action.COMPLETE_PAIRING",
            completeIntent.action
        )
        assertTrue(completeIntent.getBooleanExtra("wireless_debugging_disabled", false))
    }

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        var lastStartedServiceIntent: Intent? = null
            private set
        var lastForegroundServiceIntent: Intent? = null
            private set
        var lastStoppedServiceIntent: Intent? = null
            private set

        override fun startService(service: Intent): ComponentName? {
            lastStartedServiceIntent = Intent(service)
            return service.component
        }

        override fun startForegroundService(service: Intent): ComponentName? {
            lastForegroundServiceIntent = Intent(service)
            return service.component
        }

        override fun stopService(name: Intent): Boolean {
            lastStoppedServiceIntent = Intent(name)
            return true
        }

        fun reset() {
            lastStartedServiceIntent = null
            lastForegroundServiceIntent = null
            lastStoppedServiceIntent = null
        }
    }
}
