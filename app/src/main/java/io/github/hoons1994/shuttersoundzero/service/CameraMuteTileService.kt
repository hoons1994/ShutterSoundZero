package io.github.hoons1994.shuttersoundzero.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.hoons1994.shuttersoundzero.R
import io.github.hoons1994.shuttersoundzero.core.CscMuteManager
import io.github.hoons1994.shuttersoundzero.core.adb.LocalNetworkAccess
import io.github.hoons1994.shuttersoundzero.data.PreferencesRepository
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
        private const val AUTHENTICATION_REQUEST_CODE = 1001
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
        val prefs = PreferencesRepository.getInstance(applicationContext)
        when (TileActionSecurityPolicy.decide(isLocked, prefs.isAppLockEnabled)) {
            TileActionSecurityDecision.REQUEST_DEVICE_UNLOCK -> unlockAndRun {
                handleUnlockedClick()
            }
            TileActionSecurityDecision.REQUEST_APP_AUTHENTICATION -> launchAuthenticationActivity(targetMuted())
            TileActionSecurityDecision.EXECUTE -> executeAuthorizedAction()
        }
    }

    private fun handleUnlockedClick() {
        val prefs = PreferencesRepository.getInstance(applicationContext)
        when (TileActionSecurityPolicy.decide(isLocked, prefs.isAppLockEnabled)) {
            TileActionSecurityDecision.REQUEST_DEVICE_UNLOCK -> return
            TileActionSecurityDecision.REQUEST_APP_AUTHENTICATION -> launchAuthenticationActivity(targetMuted())
            TileActionSecurityDecision.EXECUTE -> executeAuthorizedAction()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun launchAuthenticationActivity(targetMuted: Boolean) {
        val intent = Intent(this, TileAuthenticationActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(TileAuthenticationActivity.EXTRA_TARGET_MUTED, targetMuted)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                AUTHENTICATION_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            startActivityAndCollapse(intent)
        }
    }

    private fun executeAuthorizedAction() {
        serviceScope.launch {
            CameraMuteTileAction.execute(applicationContext, ::showOptimisticTileState)
        }
    }

    private fun targetMuted(): Boolean =
        !CscMuteManager.isCscShutterSoundMuted(applicationContext)

    private fun showOptimisticTileState(targetMuted: Boolean) {
        qsTile?.let { tile ->
            tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_camera_mute)
            tile.state = if (targetMuted) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.subtitle = if (targetMuted) getString(R.string.tile_muted) else getString(R.string.tile_unmuted)
            tile.updateTile()
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val context = applicationContext
        val prefs = PreferencesRepository.getInstance(context)
        val hasUsablePermission = !prefs.isPermissionRevokedByUser &&
            CscMuteManager.hasWritePermission(context) &&
            LocalNetworkAccess.isGranted(context)
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
