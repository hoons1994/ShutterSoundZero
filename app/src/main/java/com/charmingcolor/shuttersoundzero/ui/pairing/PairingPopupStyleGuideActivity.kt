package com.charmingcolor.shuttersoundzero.ui.pairing

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import com.charmingcolor.shuttersoundzero.core.CscMuteManager
import com.charmingcolor.shuttersoundzero.data.PreferencesRepository
import com.charmingcolor.shuttersoundzero.service.PairingForegroundService

/**
 * 최초 무선 페어링 전에 One UI의 앱별 알림 팝업 스타일을 한 번 안내한다.
 * 실제 삼성 페어링 코드 화면을 열기 전에만 표시되므로 페어링 모드에는 영향을 주지 않는다.
 */
class PairingPopupStyleGuideActivity : Activity() {
    private val prefs by lazy { PreferencesRepository.getInstance(this) }
    private var openedNotificationSettings = false
    private var pairingStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (prefs.hasShownPairingPopupStyleGuide) {
            startPairingAndFinish()
            return
        }

        showGuide()
    }

    override fun onResume() {
        super.onResume()
        if (openedNotificationSettings && !pairingStarted) {
            startPairingAndFinish()
        }
    }

    private fun showGuide() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("페어링 알림을 더 편하게")
            .setMessage(
                "ShutterSoundZero만 [자세한 팝업]으로 설정하면 페어링 알림에서 " +
                    "[코드 입력] 버튼을 바로 사용할 수 있어 더 편리합니다.\n\n" +
                    "알림 설정에서 [알림 팝업 스타일] → [자세한 팝업]을 선택해 주세요.\n\n" +
                    "선택 사항이며, 변경하지 않아도 간략한 팝업의 ▼를 펼쳐 페어링할 수 있습니다."
            )
            .setPositiveButton("알림 설정 열기") { _, _ ->
                prefs.hasShownPairingPopupStyleGuide = true
                openedNotificationSettings = true

                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
                try {
                    startActivity(intent)
                } catch (_: Exception) {
                    startPairingAndFinish()
                }
            }
            .setNegativeButton("그대로 사용") { _, _ ->
                prefs.hasShownPairingPopupStyleGuide = true
                startPairingAndFinish()
            }
            .create()

        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnCancelListener {
            prefs.hasShownPairingPopupStyleGuide = true
            startPairingAndFinish()
        }
        dialog.show()
    }

    private fun startPairingAndFinish() {
        if (pairingStarted) return
        pairingStarted = true

        val devOptionsOff = !CscMuteManager.isDeveloperOptionsEnabled(this)
        PairingForegroundService.start(this, devOptionsOff)
        CscMuteManager.openPairingSetupScreen(this)
        finish()
    }
}
