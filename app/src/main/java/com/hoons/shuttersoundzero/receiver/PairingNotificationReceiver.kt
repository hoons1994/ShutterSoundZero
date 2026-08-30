package com.hoons.shuttersoundzero.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import com.hoons.shuttersoundzero.core.adb.StandaloneAdbManager
import com.hoons.shuttersoundzero.data.PreferencesRepository
import com.hoons.shuttersoundzero.ui.notification.PairingNotificationHelper
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

                if (code.isNullOrBlank() || !code.all { it.isDigit() } || code.length != 6) {
                    PairingNotificationHelper.showPairingNotification(
                        context,
                        intent.getIntExtra("pairing_port", -1).takeIf { it > 0 },
                        "⚠️ 숫자 6자리 페어링 코드를 정확히 입력해 주세요."
                    )
                    return
                }

                // 입력 직후 알림이 사라지지 않도록 즉시 '적용 중' 고정 상태로 업데이트
                PairingNotificationHelper.showProgressNotification(context)

                val adbManager = StandaloneAdbManager.getInstance(context)
                val pendingResult = goAsync()

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        kotlinx.coroutines.withTimeoutOrNull(25_000) {
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
                                PairingNotificationHelper.showPairingNotification(
                                    context,
                                    null,
                                    "⏳ 포트 탐색 대기 중: 화면의 6자리 코드를 다시 입력해 주세요."
                                )
                                return@withTimeoutOrNull
                            }

                            Log.i(TAG, "Attempting pairing via notification with port $port and code $code")
                            val pairResult = adbManager.pairLocal(port, code)

                            if (pairResult.isSuccess) {
                                Log.i(TAG, "Pairing successful! Applying camera mute & permissions via ADB...")
                                delay(300)

                                val muteResult = adbManager.applyCameraMuteViaAdb()

                                if (muteResult.isSuccess) {
                                    Log.i(TAG, "All completed successfully!")
                                    // 1. 상단바 완료 알림
                                    PairingNotificationHelper.showSuccessNotification(context)

                                    // 2. 시스템 토스트 알림
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        android.widget.Toast.makeText(
                                            context,
                                            "✨ 셔터음 제로: 셔터음 무음화 연동이 완료되었습니다!",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    }

                                    // 3. 메인 앱 화면 전면으로 복귀 시도 (BAL 제한 방어)
                                    try {
                                        val launchIntent = Intent(context, com.hoons.shuttersoundzero.MainActivity::class.java).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                        }
                                        context.startActivity(launchIntent)
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Background activity launch restricted: ${e.message}")
                                    }
                                } else {
                                    val errorMsg = muteResult.exceptionOrNull()?.message ?: "무음 설정 적용 실패"
                                    Log.w(TAG, "Mute apply failed after pairing: $errorMsg")
                                    PairingNotificationHelper.showPairingNotification(
                                        context,
                                        null,
                                        "⚠️ 페어링 완료됨: 무선 디버깅 연결 실패 ($errorMsg)"
                                    )
                                }
                            } else {
                                val errorMsg = pairResult.exceptionOrNull()?.message ?: "페어링 실패"
                                PairingNotificationHelper.showPairingNotification(
                                    context,
                                    port,
                                    "❌ $errorMsg: 코드를 다시 입력해 주세요."
                                )
                            }
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
