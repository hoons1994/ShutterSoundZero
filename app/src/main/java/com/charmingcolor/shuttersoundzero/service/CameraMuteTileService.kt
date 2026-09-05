package com.charmingcolor.shuttersoundzero.service

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import com.charmingcolor.shuttersoundzero.R
import com.charmingcolor.shuttersoundzero.core.CscMuteManager
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

        val currentMuted = CscMuteManager.isCscShutterSoundMuted(context)
        val targetMuted = !currentMuted

        // 빠른 체감을 위한 낙관적 타일 업데이트
        qsTile?.let { tile ->
            tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_camera_mute)
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
                // ADB 시도 중 사용자가 권한 연동을 해제했을 수 있으므로 직접 쓰기 전 다시 검증한다.
                val canDirectWrite = !prefs.isPermissionRevokedByUser &&
                    CscMuteManager.hasWritePermission(context)
                val directResult = if (canDirectWrite) {
                    CscMuteManager.setCscShutterSoundMuted(context, targetMuted)
                } else {
                    Result.failure(SecurityException("Permission unavailable"))
                }

                if (directResult.isSuccess) {
                    prefs.shouldMuteOnBoot = targetMuted
                    Toast.makeText(context, "카메라 셔터음 설정이 변경되었습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    prefs.shouldMuteOnBoot = currentMuted
                    Log.w(TAG, "Tile toggle failed via ADB and direct write")
                    Toast.makeText(context, "설정 변경 실패: Wi-Fi 및 무선 디버깅을 확인해 주세요.", Toast.LENGTH_LONG).show()
                }
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
