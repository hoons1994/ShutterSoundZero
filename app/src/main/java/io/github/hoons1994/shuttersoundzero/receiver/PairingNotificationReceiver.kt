package io.github.hoons1994.shuttersoundzero.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import io.github.hoons1994.shuttersoundzero.service.PairingForegroundService
import io.github.hoons1994.shuttersoundzero.ui.notification.PairingNotificationHelper

class PairingNotificationReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "PairingNotificationReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            PairingNotificationHelper.ACTION_SUBMIT_PAIRING_CODE -> {
                val sessionId = intent.identifier?.takeIf(String::isNotBlank) ?: return
                val results = RemoteInput.getResultsFromIntent(intent)
                val code = results
                    ?.getCharSequence(PairingNotificationHelper.KEY_PAIRING_CODE)
                    ?.toString()
                    ?.trim()
                    .orEmpty()

                PairingForegroundService.submitCode(context, code, sessionId)
            }

            PairingNotificationHelper.ACTION_CANCEL_PAIRING -> {
                val sessionId = intent.identifier?.takeIf(String::isNotBlank) ?: return
                Log.i(TAG, "Pairing cancelled by user from notification")
                PairingForegroundService.stop(context, sessionId)
            }
        }
    }
}
