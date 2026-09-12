package com.charmingcolor.shuttersoundzero.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * 앱 환경설정 저장소.
 */
class PreferencesRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /**
     * 최초 설치 사용자에게만 첫 실행 중요 안내를 보여주기 위한 상태.
     */
    var hasSeenInitialNotice: Boolean
        get() = prefs.getBoolean(KEY_INITIAL_NOTICE_SEEN, false)
        set(value) = prefs.edit { putBoolean(KEY_INITIAL_NOTICE_SEEN, value) }

    /**
     * 이 기능이 추가되기 전부터 사용하던 설치본에는 기존 환경설정 값이 남아 있다.
     * 기존 값이 하나라도 있으면 새 설치가 아닌 것으로 보고 안내를 다시 띄우지 않는다.
     * 아직 한 번도 실행하지 않은 설치본은 기존 값이 없으므로 첫 실행 안내를 정상 표시한다.
     */
    fun ensureInitialNoticeMigration() {
        if (prefs.contains(KEY_INITIAL_NOTICE_SEEN)) return
        val isExistingInstall = prefs.all.isNotEmpty()
        prefs.edit { putBoolean(KEY_INITIAL_NOTICE_SEEN, isExistingInstall) }
    }

    var shouldMuteOnBoot: Boolean
        get() = prefs.getBoolean(KEY_SHOULD_MUTE_ON_BOOT, false)
        set(value) = prefs.edit { putBoolean(KEY_SHOULD_MUTE_ON_BOOT, value) }

    /**
     * 사용자 화면에서는 "소프트웨어 업데이트 후 상태 확인"으로 노출한다.
     * 기존 설치 사용자의 설정을 보존하기 위해 저장 키 이름은 그대로 유지한다.
     */
    var isSoftwareUpdateCheckEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOFTWARE_UPDATE_CHECK, true)
        set(value) = prefs.edit { putBoolean(KEY_SOFTWARE_UPDATE_CHECK, value) }

    /**
     * 기존 설치 사용자의 기준값을 보존하기 위해 저장 키 이름은 그대로 유지한다.
     */
    var lastSoftwareFingerprint: String?
        get() = prefs.getString(KEY_LAST_SOFTWARE_FINGERPRINT, null)
        set(value) = prefs.edit { putString(KEY_LAST_SOFTWARE_FINGERPRINT, value) }

    /**
     * 업데이트 감지가 켜져 있는데 아직 기준값이 없다면 현재 빌드를 기준으로 저장한다.
     * 앱을 정상 실행한 시점에 호출해 다음 OS/One UI 업데이트부터 확실히 감지한다.
     */
    fun ensureSoftwareUpdateBaseline(currentFingerprint: String) {
        if (isSoftwareUpdateCheckEnabled && lastSoftwareFingerprint == null) {
            lastSoftwareFingerprint = currentFingerprint
        }
    }

    /**
     * 과거 버전은 성공한 ADB connect 포트 자체를 연동 이력의 간접 증거로 사용했다.
     * 이제 포트는 업데이트 때 안전하게 버릴 수 있도록 영구 연동 이력을 별도 키로 이전한다.
     */
    fun ensureAdbLinkageHistoryMigration(hasWritePermission: Boolean) {
        if (prefs.contains(KEY_HAS_ADB_LINKAGE_HISTORY)) return
        val explicitlyRevoked = prefs.getBoolean(KEY_PERMISSION_REVOKED_BY_USER, false)
        val legacyEvidence = !explicitlyRevoked && (
            prefs.getInt(KEY_LAST_CONNECT_PORT, -1) > 0 ||
                prefs.getBoolean(KEY_SHOULD_MUTE_ON_BOOT, false) ||
                hasWritePermission
            )
        prefs.edit { putBoolean(KEY_HAS_ADB_LINKAGE_HISTORY, legacyEvidence) }
    }

    var hasAdbLinkageHistory: Boolean
        get() = prefs.getBoolean(KEY_HAS_ADB_LINKAGE_HISTORY, false)
        set(value) = prefs.edit { putBoolean(KEY_HAS_ADB_LINKAGE_HISTORY, value) }

    var lastConnectPort: Int
        get() = prefs.getInt(KEY_LAST_CONNECT_PORT, -1)
        set(value) = prefs.edit {
            putInt(KEY_LAST_CONNECT_PORT, value)
            if (value in 1..65535) {
                putBoolean(KEY_HAS_ADB_LINKAGE_HISTORY, true)
            }
        }

    /**
     * 무선 ADB connect 포트는 무선 디버깅 재시작·재부팅·소프트웨어 업데이트 뒤 바뀔 수 있는
     * 임시 값이다. 사용자 설정과 권한 연동 이력은 유지하면서 현재 세션의 연결 흔적만 폐기한다.
     */
    fun clearTransientAdbConnectionState() {
        prefs.edit { remove(KEY_LAST_CONNECT_PORT) }
    }

    var isPermissionRevokedByUser: Boolean
        get() = prefs.getBoolean(KEY_PERMISSION_REVOKED_BY_USER, false)
        set(value) = prefs.edit {
            putBoolean(KEY_PERMISSION_REVOKED_BY_USER, value)
            if (value) {
                putBoolean(KEY_HAS_ADB_LINKAGE_HISTORY, false)
            }
        }

    /**
     * 마지막 1회 설정 실패 위치. 성공하거나 사용자가 다시 시작하면 비운다.
     */
    var lastSetupIssue: SetupIssue?
        get() = prefs.getString(KEY_LAST_SETUP_ISSUE, null)?.let { stored ->
            runCatching { SetupIssue.valueOf(stored) }.getOrNull()
        }
        set(value) = prefs.edit {
            if (value == null) remove(KEY_LAST_SETUP_ISSUE)
            else putString(KEY_LAST_SETUP_ISSUE, value.name)
        }

    var isAppLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_APP_LOCK_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_APP_LOCK_ENABLED, value) }

    /**
     * 앱을 열었을 때 최신 정식 버전을 조용히 확인할지 여부.
     * APK 다운로드와 설치는 항상 사용자가 [업데이트]를 누른 뒤에만 시작한다.
     */
    var isAppUpdateAutoCheckEnabled: Boolean
        get() = prefs.getBoolean(KEY_APP_UPDATE_AUTO_CHECK, true)
        set(value) = prefs.edit { putBoolean(KEY_APP_UPDATE_AUTO_CHECK, value) }

    var lastAppUpdateCheckAtMillis: Long
        get() = prefs.getLong(KEY_LAST_APP_UPDATE_CHECK_AT, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_APP_UPDATE_CHECK_AT, value) }

    var knownAvailableAppUpdateVersion: String?
        get() = prefs.getString(KEY_KNOWN_AVAILABLE_APP_UPDATE_VERSION, null)
        set(value) = prefs.edit {
            if (value == null) remove(KEY_KNOWN_AVAILABLE_APP_UPDATE_VERSION)
            else putString(KEY_KNOWN_AVAILABLE_APP_UPDATE_VERSION, value)
        }

    companion object {
        private const val PREF_NAME = "galaxy_camera_mute_prefs"
        private const val KEY_SHOULD_MUTE_ON_BOOT = "should_mute_on_boot"
        private const val KEY_INITIAL_NOTICE_SEEN = "initial_notice_seen"

        // Legacy key strings intentionally kept for migration compatibility.
        private const val KEY_SOFTWARE_UPDATE_CHECK = "firmware_update_check"
        private const val KEY_LAST_SOFTWARE_FINGERPRINT = "last_firmware_fingerprint"

        private const val KEY_LAST_CONNECT_PORT = "last_connect_port"
        private const val KEY_HAS_ADB_LINKAGE_HISTORY = "has_adb_linkage_history"
        private const val KEY_PERMISSION_REVOKED_BY_USER = "permission_revoked_by_user"
        private const val KEY_LAST_SETUP_ISSUE = "last_setup_issue"
        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        private const val KEY_APP_UPDATE_AUTO_CHECK = "app_update_auto_check"
        private const val KEY_LAST_APP_UPDATE_CHECK_AT = "last_app_update_check_at"
        private const val KEY_KNOWN_AVAILABLE_APP_UPDATE_VERSION = "known_available_app_update_version"

        @Volatile
        private var INSTANCE: PreferencesRepository? = null

        fun getInstance(context: Context): PreferencesRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PreferencesRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
