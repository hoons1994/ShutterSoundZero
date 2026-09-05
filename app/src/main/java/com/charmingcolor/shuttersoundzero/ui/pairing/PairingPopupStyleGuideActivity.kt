package com.charmingcolor.shuttersoundzero.ui.pairing

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.charmingcolor.shuttersoundzero.core.CscMuteManager
import com.charmingcolor.shuttersoundzero.data.PreferencesRepository
import com.charmingcolor.shuttersoundzero.service.PairingForegroundService
import com.charmingcolor.shuttersoundzero.theme.ShutterSoundZeroTheme
import com.charmingcolor.shuttersoundzero.ui.components.ModernPromptCard

/**
 * 최초 무선 페어링 전에 One UI의 앱별 알림 팝업 스타일을 한 번 안내한다.
 * 실제 삼성 페어링 코드 화면을 열기 전에만 표시되므로 페어링 모드에는 영향을 주지 않는다.
 */
class PairingPopupStyleGuideActivity : ComponentActivity() {
    private val prefs by lazy { PreferencesRepository.getInstance(this) }
    private var openedNotificationSettings = false
    private var pairingStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (prefs.hasShownPairingPopupStyleGuide) {
            startPairingAndFinish()
            return
        }

        onBackPressedDispatcher.addCallback(this) {
            prefs.hasShownPairingPopupStyleGuide = true
            startPairingAndFinish()
        }

        setContent {
            ShutterSoundZeroTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.38f))
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ModernPromptCard(
                        eyebrow = "처음 한 번만",
                        title = "페어링 알림을 더 편하게",
                        message =
                            "ShutterSoundZero만 [자세한 팝업]으로 설정하면 페어링 알림에서 " +
                                "[코드 입력] 버튼을 바로 사용할 수 있습니다.\n\n" +
                                "알림 설정에서 [알림 팝업 스타일] → [자세한 팝업]을 선택해 주세요. " +
                                "변경하지 않아도 간략한 팝업을 펼쳐 동일하게 페어링할 수 있습니다.",
                        primaryLabel = "알림 설정 열기",
                        onPrimary = {
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
                        },
                        secondaryLabel = "현재 방식으로 계속",
                        onSecondary = {
                            prefs.hasShownPairingPopupStyleGuide = true
                            startPairingAndFinish()
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (openedNotificationSettings && !pairingStarted) {
            startPairingAndFinish()
        }
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
