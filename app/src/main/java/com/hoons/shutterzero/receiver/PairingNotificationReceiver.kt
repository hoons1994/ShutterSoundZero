package com.hoons.shutterzero.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import com.hoons.shutterzero.core.adb.StandaloneAdbManager
import com.hoons.shutterzero.data.PreferencesRepository
import com.hoons.shutterzero.ui.notification.PairingNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PairingNotificationReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "PairingNotificationReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        when (action) {
            PairingNotificationHelper.ACTION_SUBMIT_PAIRING_CODE -> {
                val results = RemoteInput.getResultsFromIntent(intent)
                val code = results?.getCharSequence(PairingNotificationHelper.KEY_PAIRING_CODE)?.toString()?.trim()

                if (code.isNullOrBlank()) {
                    PairingNotificationHelper.showFailureNotification(context, "페어링 코드가 입력되지 않았습니다.")
                    return
                }

                val adbManager = StandaloneAdbManager.getInstance(context)
                val port = intent.getIntExtra("pairing_port", -1).takeIf { it > 0 }
                    ?: adbManager.lastDiscoveredPairingPort

                if (port == null || port <= 0) {
                    PairingNotificationHelper.showFailureNotification(
                        context,
                        "페어링 포트를 아직 탐색 중입니다. 잠시 후 상단바에서 다시 코드를 입력해 주세요."
                    )
                    return
                }

                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        Log.i(TAG, "Attempting pairing via notification with port $port and code $code")
                        val pairResult = adbManager.pairLocal(port, code)

                        if (pairResult.isSuccess) {
                            val connectPort = adbManager.lastDiscoveredConnectPort ?: port
                            val muteResult = adbManager.applyCameraMuteViaAdb(connectPort)

                            PreferencesRepository.getInstance(context).shouldMuteOnBoot = true
                            if (muteResult.isSuccess) {
                                PairingNotificationHelper.showSuccessNotification(context)
                            } else {
                                PairingNotificationHelper.showSuccessNotification(context)
                            }
                        } else {
                            val errorMsg = pairResult.exceptionOrNull()?.message ?: "페어링에 실패했습니다."
                            PairingNotificationHelper.showFailureNotification(context, errorMsg)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Notification pairing error: ${e.message}", e)
                        PairingNotificationHelper.showFailureNotification(context, "오류 발생: ${e.message}")
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            PairingNotificationHelper.ACTION_CANCEL_PAIRING -> {
                Log.i(TAG, "Pairing cancelled by user from notification")
                PairingNotificationHelper.cancelNotification(context)
            }
        }
    }
}
