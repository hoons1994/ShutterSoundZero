package io.github.hoons1994.shuttersoundzero.service

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import io.github.hoons1994.shuttersoundzero.core.CscMuteManager
import io.github.hoons1994.shuttersoundzero.core.CscStateVerifier
import io.github.hoons1994.shuttersoundzero.core.DeveloperOptionsManager
import io.github.hoons1994.shuttersoundzero.core.adb.LocalNetworkAccess
import io.github.hoons1994.shuttersoundzero.core.adb.StandaloneAdbManager
import io.github.hoons1994.shuttersoundzero.data.PreferencesRepository

internal object CameraMuteTileAction {
    private const val TAG = "CameraMuteTileAction"

    suspend fun execute(
        context: Context,
        onTargetState: (Boolean) -> Unit = {}
    ) {
        val appContext = context.applicationContext
        val prefs = PreferencesRepository.getInstance(appContext)

        if (!CscMuteManager.isSamsungDevice()) {
            showMessage(
                appContext,
                "⚠️ 이 앱은 삼성 갤럭시 전용 앱입니다. 다른 제조사 기기에서는 사용할 수 없습니다."
            )
            requestTileRefresh(appContext)
            return
        }

        if (prefs.isPermissionRevokedByUser || !CscMuteManager.hasWritePermission(appContext)) {
            showMessage(appContext, "권한 연동이 해제되어 있습니다. 앱을 열어 다시 연동해 주세요.")
            requestTileRefresh(appContext)
            return
        }

        if (!LocalNetworkAccess.isGranted(appContext)) {
            showMessage(
                appContext,
                "Android 17에서 기기 연결을 위해 로컬 네트워크 권한이 필요합니다. 앱을 열어 권한을 허용해 주세요."
            )
            requestTileRefresh(appContext)
            return
        }

        if (!DeveloperOptionsManager.isWirelessDebuggingEnabled(appContext)) {
            showMessage(appContext, "설정을 바꾸려면 무선 디버깅을 잠시 켠 뒤 타일을 다시 눌러 주세요.")
            requestTileRefresh(appContext)
            return
        }

        val targetMuted = !CscMuteManager.isCscShutterSoundMuted(appContext)
        onTargetState(targetMuted)

        try {
            val adbManager = StandaloneAdbManager.getInstance(appContext)
            val result = adbManager.setCameraMute(targetMuted)
            val stateApplied = result.isSuccess && CscStateVerifier.waitFor(targetMuted) {
                CscMuteManager.isCscShutterSoundMuted(appContext)
            }

            if (stateApplied) {
                val wirelessCleanup = DeveloperOptionsManager.disableWirelessDebugging(appContext)
                val baseMessage = if (targetMuted) {
                    "카메라 무음 설정이 적용되었습니다."
                } else {
                    "카메라 셔터음이 기본 상태로 복원되었습니다."
                }
                val message = if (wirelessCleanup.isSuccess) {
                    "$baseMessage 무선 디버깅도 껐습니다."
                } else {
                    "$baseMessage 무선 디버깅은 직접 꺼 주세요."
                }
                showMessage(appContext, message)
            } else {
                Log.w(
                    TAG,
                    if (result.isSuccess) {
                        "Tile toggle command completed but CSC state did not match request"
                    } else {
                        "Tile toggle failed via ADB"
                    }
                )
                showMessage(
                    appContext,
                    "설정 변경 실패: 실제 카메라 설정을 확인하지 못했습니다. 무선 디버깅을 켠 뒤 다시 시도해 주세요."
                )
            }
        } finally {
            requestTileRefresh(appContext)
        }
    }

    private fun showMessage(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    private fun requestTileRefresh(context: Context) {
        TileService.requestListeningState(
            context,
            ComponentName(context, CameraMuteTileService::class.java)
        )
    }
}
