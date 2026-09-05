package com.charmingcolor.shuttersoundzero.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import com.charmingcolor.shuttersoundzero.service.PairingForegroundService
import com.charmingcolor.shuttersoundzero.ui.notification.PairingNotificationHelper

class PairingNotificationReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "PairingNotificationReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            PairingNotificationHelper.ACTION_SUBMIT_PAIRING_CODE -> {
                val results = RemoteInput.getResultsFromIntent(intent)
                val code = results
                    ?.getCharSequence(PairingNotificationHelper.KEY_PAIRING_CODE)
                    ?.toString()
                    ?.trim()
                    .orEmpty()

                PairingForegroundService.submitCode(context, code)
            }

            PairingNotificationHelper.ACTION_CANCEL_PAIRING -> {
                Log.i(TAG, "Pairing cancelled by user from notification")
                PairingForegroundService.stop(context)
            }
        }
    }
}
