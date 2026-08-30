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
import kotlinx.coroutines.delay
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
                    PairingNotificationHelper.showPairingNotification(
                        context,
                        null,
                        "⚠️ 6자리 코드가 입력되지 않았습니다."
                    )
                    return
                }

                // 입력 직후 알림이 사라지지 않도록 즉시 '적용 중' 고정 상태로 업데이트
                PairingNotificationHelper.showProgressNotification(context)

                val adbManager = StandaloneAdbManager.getInstance(context)
                val pendingResult = goAsync()

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        var port = intent.getIntExtra("pairing_port", -1).takeIf { it > 0 }
                            ?: adbManager.lastDiscoveredPairingPort

                        // mDNS가 아직 포트를 해석 중인 경우 최대 3초간 대기
                        if (port == null || port <= 0) {
                            for (i in 0 until 15) {
                                delay(200)
                                port = adbManager.lastDiscoveredPairingPort
                                if (port != null && port > 0) break
                            }
                        }

                        if (port == null || port <= 0) {
                            // 포트를 못 찾더라도 알림창을 끄지 않고 안내와 함께 재입력 유지
                            PairingNotificationHelper.showPairingNotification(
                                context,
                                null,
                                "⏳ 포트 탐색 대기 중: 화면의 6자리 코드를 다시 입력해 주세요."
                            )
                            return@launch
                        }

                        Log.i(TAG, "Attempting pairing via notification with port $port and code $code")
                        val pairResult = adbManager.pairLocal(port, code)

                        if (pairResult.isSuccess) {
                            val connectPort = adbManager.lastDiscoveredConnectPort ?: port
                            adbManager.applyCameraMuteViaAdb(connectPort)

                            PreferencesRepository.getInstance(context).shouldMuteOnBoot = true
                            // 페어링 및 무음화가 최종 완료되었을 때만 완료 알림으로 전환
                            PairingNotificationHelper.showSuccessNotification(context)
                        } else {
                            val errorMsg = pairResult.exceptionOrNull()?.message ?: "페어링 실패"
                            // 실패하더라도 알림을 끄지 않고 코드 입력창 유지
                            PairingNotificationHelper.showPairingNotification(
                                context,
                                port,
                                "❌ $errorMsg: 코드를 다시 입력해 주세요."
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Notification pairing error: ${e.message}", e)
                        PairingNotificationHelper.showPairingNotification(
                            context,
                            null,
                            "❌ 오류 발생: ${e.message}"
                        )
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
