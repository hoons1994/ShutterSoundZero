package com.hoons.shutterzero.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.hoons.shutterzero.core.CscMuteManager
import com.hoons.shutterzero.data.PreferencesRepository

/**
 * 갤럭시 스마트폰 재부팅 또는 앱 업데이트 시 CSC 셔터음 설정을 자동으로 유지하는 리시버
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "Received broadcast intent: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON") {

            val prefs = PreferencesRepository.getInstance(context)
            if (prefs.isAutoRestoreOnBootEnabled && prefs.shouldMuteOnBoot) {
                if (CscMuteManager.hasWritePermission(context)) {
                    val result = CscMuteManager.setCscShutterSoundMuted(context, true)
                    result.onSuccess {
                        Log.i(TAG, "Successfully restored CSC camera mute after boot")
                    }.onFailure { error ->
                        Log.e(TAG, "Failed to restore CSC camera mute after boot: ${error.message}")
                    }
                } else {
                    Log.w(TAG, "Cannot restore CSC mute on boot: Missing WRITE_SECURE_SETTINGS permission")
                }
            }
        }
    }
}
