package com.hoons.shutterzero.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 앱 환경설정 저장소 (재부팅 시 자동 적용 여부 등 관리)
 */
class PreferencesRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    var isAutoRestoreOnBootEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RESTORE_BOOT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_RESTORE_BOOT, value).apply()

    var shouldMuteOnBoot: Boolean
        get() = prefs.getBoolean(KEY_SHOULD_MUTE_ON_BOOT, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOULD_MUTE_ON_BOOT, value).apply()

    companion object {
        private const val PREF_NAME = "galaxy_camera_mute_prefs"
        private const val KEY_AUTO_RESTORE_BOOT = "auto_restore_boot"
        private const val KEY_SHOULD_MUTE_ON_BOOT = "should_mute_on_boot"

        @Volatile
        private var INSTANCE: PreferencesRepository? = null

        fun getInstance(context: Context): PreferencesRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PreferencesRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
