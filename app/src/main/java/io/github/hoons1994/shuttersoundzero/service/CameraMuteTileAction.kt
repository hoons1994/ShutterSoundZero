package io.github.hoons1994.shuttersoundzero.service

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import io.github.hoons1994.shuttersoundzero.AppDependencies
import io.github.hoons1994.shuttersoundzero.R
import io.github.hoons1994.shuttersoundzero.core.CscMuteManager
import io.github.hoons1994.shuttersoundzero.core.DeveloperOptionsManager
import io.github.hoons1994.shuttersoundzero.core.adb.LocalNetworkAccess
import io.github.hoons1994.shuttersoundzero.core.adb.CameraMuteFailure
import io.github.hoons1994.shuttersoundzero.data.PreferencesRepository

internal object CameraMuteTileAction {
    private const val TAG = "CameraMuteTileAction"

    suspend fun execute(
        context: Context,
        onStarted: () -> Unit = {}
    ) {
        val appContext = context.applicationContext
        val currentMuted = CscMuteManager.readCscMutedState(appContext)
        if (currentMuted == null) {
            showMessage(appContext, appContext.getString(R.string.csc_state_unknown_guidance))
            requestTileRefresh(appContext)
            return
        }
        val targetMuted = !currentMuted
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

        val wirelessDebuggingEnabled = DeveloperOptionsManager.readWirelessDebuggingEnabled(appContext)
        if (wirelessDebuggingEnabled != true) {
            showMessage(appContext, appContext.getString(
                if (wirelessDebuggingEnabled == null) R.string.wireless_debugging_state_unknown_guidance
                else R.string.tile_action_enable_wireless_debugging
            ))
            requestTileRefresh(appContext)
            return
        }

        onStarted()

        try {
            val result = AppDependencies.changeCameraMute(appContext)(targetMuted)

            if (result.isSuccess) {
                val wirelessCleanup = result.getOrThrow().wirelessCleanup
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
                Log.w(TAG, "Tile camera setting change failed")
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
