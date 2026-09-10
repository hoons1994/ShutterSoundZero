package com.charmingcolor.shuttersoundzero.service

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import com.charmingcolor.shuttersoundzero.R
import com.charmingcolor.shuttersoundzero.core.CscMuteManager
import com.charmingcolor.shuttersoundzero.core.CscStateVerifier
import com.charmingcolor.shuttersoundzero.core.DeveloperOptionsManager
import com.charmingcolor.shuttersoundzero.core.adb.StandaloneAdbManager
import com.charmingcolor.shuttersoundzero.data.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 상단바 빠른 설정(Quick Settings) 패널에 등록되는 타일 서비스
 * 사용자가 상단 알림창을 내려 원클릭으로 카메라 무음 연동을 켜고 끌 수 있습니다.
 */
class CameraMuteTileService : TileService() {
    companion object {
        private const val TAG = "CameraMuteTileService"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val context = applicationContext
        val prefs = PreferencesRepository.getInstance(context)

        if (!CscMuteManager.isSamsungDevice()) {
            Toast.makeText(
                context,
                "⚠️ 이 앱은 삼성 갤럭시 전용 앱입니다. 다른 제조사 기기에서는 사용할 수 없습니다.",
                Toast.LENGTH_LONG
            ).show()
            updateTileState()
            return
        }

        if (prefs.isPermissionRevokedByUser || !CscMuteManager.hasWritePermission(context)) {
            Toast.makeText(
                context,
                "권한 연동이 해제되어 있습니다. 앱을 열어 다시 연동해 주세요.",
                Toast.LENGTH_LONG
            ).show()
            updateTileState()
            return
        }

        if (!DeveloperOptionsManager.isWirelessDebuggingEnabled(context)) {
            Toast.makeText(
                context,
                "설정을 바꾸려면 무선 디버깅을 잠시 켠 뒤 타일을 다시 눌러 주세요.",
                Toast.LENGTH_LONG
            ).show()
            updateTileState()
            return
        }

        val currentMuted = CscMuteManager.isCscShutterSoundMuted(context)
        val targetMuted = !currentMuted
        val previousDesiredMute = prefs.shouldMuteOnBoot

        // 빠른 체감을 위한 낙관적 타일 업데이트. 작업이 끝나면 실제 CSC 값으로 다시 확정한다.
        qsTile?.let { tile ->
            tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_camera_mute)
            tile.state = if (targetMuted) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.subtitle = if (targetMuted) getString(R.string.tile_muted) else getString(R.string.tile_unmuted)
            tile.updateTile()
        }

        serviceScope.launch {
            val adbManager = StandaloneAdbManager.getInstance(context)
            val result = adbManager.setCameraMute(targetMuted)
            val stateApplied = result.isSuccess && CscStateVerifier.waitFor(targetMuted) {
                CscMuteManager.isCscShutterSoundMuted(context)
            }

            if (stateApplied) {
                prefs.shouldMuteOnBoot = targetMuted
                val wirelessCleanup = DeveloperOptionsManager.disableWirelessDebugging(context)
                val baseMessage = if (targetMuted) {
                    "카메라 무음 설정이 적용되었습니다."
                } else {
                    "카메라 셔터음이 기본 상태로 복원되었습니다."
                }
                val msg = if (wirelessCleanup.isSuccess) {
                    "$baseMessage 무선 디버깅도 껐습니다."
                } else {
                    "$baseMessage 무선 디버깅은 직접 꺼 주세요."
                }
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            } else {
                // 명령이 성공으로 끝났더라도 실제 CSC 값이 바뀌지 않았다면 실패로 처리한다.
                prefs.shouldMuteOnBoot = previousDesiredMute
                Log.w(
                    TAG,
                    if (result.isSuccess) {
                        "Tile toggle command completed but CSC state did not match request"
                    } else {
                        "Tile toggle failed via ADB"
                    }
                )
                Toast.makeText(
                    context,
                    "설정 변경 실패: 실제 카메라 설정을 확인하지 못했습니다. 무선 디버깅을 켠 뒤 다시 시도해 주세요.",
                    Toast.LENGTH_LONG
                ).show()
            }
            updateTileState()
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val context = applicationContext
        val prefs = PreferencesRepository.getInstance(context)
        val hasUsablePermission = !prefs.isPermissionRevokedByUser &&
            CscMuteManager.hasWritePermission(context)
        val isMuted = hasUsablePermission && CscMuteManager.isCscShutterSoundMuted(context)

        // Refresh the icon explicitly so existing tiles do not remain stuck on a cached launcher icon.
        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_camera_mute)
        tile.state = if (isMuted) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_name)
        tile.subtitle = when {
            !hasUsablePermission -> getString(R.string.tile_permission_required)
            isMuted -> getString(R.string.tile_muted)
            else -> getString(R.string.tile_unmuted)
        }
        tile.updateTile()
    }
}
