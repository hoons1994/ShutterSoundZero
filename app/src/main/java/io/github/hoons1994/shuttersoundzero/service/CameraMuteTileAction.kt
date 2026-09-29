package io.github.hoons1994.shuttersoundzero.service

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import io.github.hoons1994.shuttersoundzero.R
import io.github.hoons1994.shuttersoundzero.core.CscMuteManager
import io.github.hoons1994.shuttersoundzero.core.CscStateVerifier
import io.github.hoons1994.shuttersoundzero.core.DeveloperOptionsManager
import io.github.hoons1994.shuttersoundzero.core.adb.LocalNetworkAccess
import io.github.hoons1994.shuttersoundzero.core.adb.CameraMuteFailure
import io.github.hoons1994.shuttersoundzero.core.adb.StandaloneAdbManager
import io.github.hoons1994.shuttersoundzero.data.PreferencesRepository

internal object CameraMuteTileAction {
    private const val TAG = "CameraMuteTileAction"

    suspend fun execute(
        context: Context,
        onStarted: () -> Unit = {}
    ) {
        val appContext = context.applicationContext
        val targetMuted = !CscMuteManager.isCscShutterSoundMuted(appContext)
        executeTarget(appContext, targetMuted, onStarted)
    }

    suspend fun executeTarget(
        context: Context,
        targetMuted: Boolean,
        onStarted: () -> Unit = {}
    ) {
        val appContext = context.applicationContext
        val prefs = PreferencesRepository.getInstance(appContext)

        if (!CscMuteManager.isSamsungDevice()) {
            showMessage(
                appContext,
                appContext.getString(R.string.main_non_samsung_device_warning)
            )
            requestTileRefresh(appContext)
            return
        }

        if (prefs.isPermissionRevokedByUser || !CscMuteManager.hasWritePermission(appContext)) {
            showMessage(appContext, appContext.getString(R.string.tile_action_permission_link_revoked))
            requestTileRefresh(appContext)
            return
        }

        if (!LocalNetworkAccess.isGranted(appContext)) {
            showMessage(
                appContext,
                appContext.getString(R.string.tile_action_local_network_required)
            )
            requestTileRefresh(appContext)
            return
        }

        if (!DeveloperOptionsManager.isWirelessDebuggingEnabled(appContext)) {
            showMessage(appContext, appContext.getString(R.string.tile_action_enable_wireless_debugging))
            requestTileRefresh(appContext)
            return
        }

        onStarted()

        try {
            val adbManager = StandaloneAdbManager.getInstance(appContext)
            val result = adbManager.setCameraMute(targetMuted)
            val stateApplied = result.isSuccess && CscStateVerifier.waitFor(targetMuted) {
                CscMuteManager.isCscShutterSoundMuted(appContext)
            }

            if (stateApplied) {
                val wirelessCleanup = DeveloperOptionsManager.disableWirelessDebugging(appContext)
                val message = when {
                    targetMuted && wirelessCleanup.isSuccess ->
                        appContext.getString(R.string.tile_action_mute_applied_wireless_disabled)
                    !targetMuted && wirelessCleanup.isSuccess ->
                        appContext.getString(R.string.tile_action_restore_applied_wireless_disabled)
                    targetMuted ->
                        appContext.getString(R.string.tile_action_mute_applied_disable_wireless_manually)
                    else ->
                        appContext.getString(R.string.tile_action_restore_applied_disable_wireless_manually)
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
                    CameraMuteFailure.from(result.exceptionOrNull()).message + " 앱 홈에서 복구를 진행해 주세요."
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
