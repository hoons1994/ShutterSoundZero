package com.charmingcolor.shuttersoundzero.ui.pairing

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import com.charmingcolor.shuttersoundzero.core.CscMuteManager
import com.charmingcolor.shuttersoundzero.data.PreferencesRepository
import com.charmingcolor.shuttersoundzero.service.PairingForegroundService

/**
 * 페어링을 시작하기 전에만 사용하는 간편 입력 권한 안내 Activity.
 * 실제 6자리 코드 입력 중에는 Activity를 전환하지 않는다.
 */
class PairingOverlayPermissionActivity : Activity() {
    private var openedOverlaySettings = false
    private var pairingStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Settings.canDrawOverlays(this)) {
            startPairingAndFinish()
        } else {
            showPermissionExplanation()
        }
    }

    override fun onResume() {
        super.onResume()
        if (openedOverlaySettings && !pairingStarted) {
            startPairingAndFinish()
        }
    }

    private fun showPermissionExplanation() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("간편 페어링 입력")
            .setMessage(
                "간략 알림을 눌렀을 때 삼성 설정 화면을 닫지 않고 6자리 코드 입력창만 띄우려면 " +
                    "‘다른 앱 위에 표시’ 권한이 필요합니다.\n\n" +
                    "이 권한은 페어링 코드 입력창을 표시할 때만 사용하며, 기존 알림 입력 방식도 계속 사용할 수 있습니다."
            )
            .setPositiveButton("간편 입력 사용") { _, _ ->
                openedOverlaySettings = true
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
            .setNegativeButton("기존 방식 사용") { _, _ ->
                startPairingAndFinish()
            }
            .create()

        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnCancelListener { startPairingAndFinish() }
        dialog.show()
    }

    private fun startPairingAndFinish() {
        if (pairingStarted) return
        pairingStarted = true

        val prefs = PreferencesRepository.getInstance(this)
        prefs.isPermissionRevokedByUser = false

        val devOptionsOff = !CscMuteManager.isDeveloperOptionsEnabled(this)
        PairingForegroundService.start(this, devOptionsOff)
        CscMuteManager.openPairingSetupScreen(this)
        finish()
    }
}
