package com.hoons.shutterzero.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import com.hoons.shutterzero.R
import com.hoons.shutterzero.core.CscMuteManager
import com.hoons.shutterzero.data.PreferencesRepository

/**
 * 상단바 빠른 설정(Quick Settings) 패널에 등록되는 타일 서비스
 * 사용자가 상단 알림창을 내려 원클릭으로 카메라 무음 연동을 켜고 끌 수 있습니다.
 */
class CameraMuteTileService : TileService() {
    companion object {
        private const val TAG = "CameraMuteTileService"
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val context = applicationContext

        if (!CscMuteManager.hasWritePermission(context)) {
            Toast.makeText(
                context,
                "권한이 필요합니다. 앱을 실행하여 ADB 권한 설정을 완료해주세요.",
                Toast.LENGTH_LONG
            ).show()
            updateTileState()
            return
        }

        val currentMuted = CscMuteManager.isCscShutterSoundMuted(context)
        val targetMuted = !currentMuted

        val result = CscMuteManager.setCscShutterSoundMuted(context, targetMuted)
        result.onSuccess {
            PreferencesRepository.getInstance(context).shouldMuteOnBoot = targetMuted
            val msg = if (targetMuted) {
                "카메라 셔터음 무음화가 활성화되었습니다. (진동/무음 시 무음)"
            } else {
                "카메라 셔터음이 기본 상태로 복원되었습니다."
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }.onFailure { error ->
            Log.e(TAG, "Failed to toggle tile state: ${error.message}")
            Toast.makeText(context, "설정 변경 실패: ${error.message}", Toast.LENGTH_LONG).show()
        }

        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val context = applicationContext
        val isMuted = CscMuteManager.isCscShutterSoundMuted(context)

        tile.state = if (isMuted) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_name)
        tile.subtitle = if (isMuted) getString(R.string.tile_muted) else getString(R.string.tile_unmuted)
        tile.updateTile()
    }
}
