package com.hoons.shutterzero.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import com.hoons.shutterzero.R
import com.hoons.shutterzero.core.CscMuteManager
import com.hoons.shutterzero.core.adb.StandaloneAdbManager
import com.hoons.shutterzero.data.PreferencesRepository
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

        if (!CscMuteManager.hasWritePermission(context)) {
            Toast.makeText(
                context,
                "권한이 필요합니다. 앱을 실행하여 권한 설정을 완료해 주세요.",
                Toast.LENGTH_LONG
            ).show()
            updateTileState()
            return
        }

        val currentMuted = prefs.shouldMuteOnBoot
        val targetMuted = !currentMuted

        // 빠른 체감을 위한 낙관적 타일 업데이트
        qsTile?.let { tile ->
            tile.state = if (targetMuted) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.subtitle = if (targetMuted) getString(R.string.tile_muted) else getString(R.string.tile_unmuted)
            tile.updateTile()
        }

        serviceScope.launch {
            val adbManager = StandaloneAdbManager.getInstance(context)
            val result = adbManager.setCameraMute(targetMuted)

            if (result.isSuccess) {
                prefs.shouldMuteOnBoot = targetMuted
                val msg = if (targetMuted) {
                    "카메라 셔터음 무음화가 활성화되었습니다. (진동/무음 시 무음)"
                } else {
                    "카메라 셔터음이 기본 상태로 복원되었습니다."
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            } else {
                // 직접 쓰기 시도(폴백)
                val directResult = CscMuteManager.setCscShutterSoundMuted(context, targetMuted)
                if (directResult.isSuccess) {
                    prefs.shouldMuteOnBoot = targetMuted
                    Toast.makeText(context, "카메라 셔터음 설정이 변경되었습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    Log.w(TAG, "Tile toggle failed via ADB and direct write: ${result.exceptionOrNull()?.message}")
                    Toast.makeText(context, "설정 변경 실패: Wi-Fi 및 무선 디버깅을 확인해 주세요.", Toast.LENGTH_LONG).show()
                }
            }
            updateTileState()
        }
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
