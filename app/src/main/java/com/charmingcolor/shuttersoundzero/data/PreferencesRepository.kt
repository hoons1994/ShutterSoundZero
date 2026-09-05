package com.charmingcolor.shuttersoundzero.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * 앱 환경설정 저장소 (재부팅 시 자동 적용 여부 등 관리)
 */
class PreferencesRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    var isAutoRestoreOnBootEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RESTORE_BOOT, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_RESTORE_BOOT, value) }

    var shouldMuteOnBoot: Boolean
        get() = prefs.getBoolean(KEY_SHOULD_MUTE_ON_BOOT, false)
        set(value) = prefs.edit { putBoolean(KEY_SHOULD_MUTE_ON_BOOT, value) }

    var isFirmwareUpdateCheckEnabled: Boolean
        get() = prefs.getBoolean(KEY_FIRMWARE_UPDATE_CHECK, true)
        set(value) = prefs.edit { putBoolean(KEY_FIRMWARE_UPDATE_CHECK, value) }

    var lastFirmwareFingerprint: String?
        get() = prefs.getString(KEY_LAST_FIRMWARE_FINGERPRINT, null)
        set(value) = prefs.edit { putString(KEY_LAST_FIRMWARE_FINGERPRINT, value) }

    var lastConnectPort: Int
        get() = prefs.getInt(KEY_LAST_CONNECT_PORT, -1)
        set(value) = prefs.edit { putInt(KEY_LAST_CONNECT_PORT, value) }

    var isPermissionRevokedByUser: Boolean
        get() = prefs.getBoolean(KEY_PERMISSION_REVOKED_BY_USER, false)
        set(value) = prefs.edit { putBoolean(KEY_PERMISSION_REVOKED_BY_USER, value) }

    var isAppLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_APP_LOCK_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_APP_LOCK_ENABLED, value) }

    companion object {
        private const val PREF_NAME = "galaxy_camera_mute_prefs"
        private const val KEY_AUTO_RESTORE_BOOT = "auto_restore_boot"
        private const val KEY_SHOULD_MUTE_ON_BOOT = "should_mute_on_boot"
        private const val KEY_FIRMWARE_UPDATE_CHECK = "firmware_update_check"
        private const val KEY_LAST_FIRMWARE_FINGERPRINT = "last_firmware_fingerprint"
        private const val KEY_LAST_CONNECT_PORT = "last_connect_port"
        private const val KEY_PERMISSION_REVOKED_BY_USER = "permission_revoked_by_user"
        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"

        @Volatile
        private var INSTANCE: PreferencesRepository? = null

        fun getInstance(context: Context): PreferencesRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PreferencesRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
