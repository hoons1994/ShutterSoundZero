package com.charmingcolor.shuttersoundzero.core

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log

/**
 * 기기 특화 CSC 설정 관리자
 * 
 * 시스템 설정 테이블에는 카메라 촬영음 강제 여부를 결정하는
 * "csc_pref_camera_forced_shuttersound_key" 가 존재합니다.
 * 
 * - 값 1: 강제 셔터음 활성화 (국내향 기본값 - 진동/무음이어도 셔터음 발생)
 * - 값 0: 강제 셔터음 비활성화 (기기 볼륨/진동/무음 모드와 연동되어 무음/진동 시 소리 안 남)
 */
object CscMuteManager {
    private const val TAG = "CscMuteManager"
    private const val SETTINGS_PACKAGE = "com.android.settings"
    private const val SETTINGS_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
    private const val SOFTWARE_INFO_PREFERENCE_KEY = "software_info"
    private const val WIRELESS_DEBUGGING_PREFERENCE_KEY = "toggle_adb_wireless"
    const val CSC_KEY = "csc_pref_camera_forced_shuttersound_key"

    /**
     * 현재 기기가 삼성 갤럭시 기기인지 확인
     */
    fun isSamsungDevice(): Boolean {
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        val brand = android.os.Build.BRAND.lowercase()
        return manufacturer.contains("samsung") || brand.contains("samsung")
    }

    /**
     * 현재 CSC 셔터음 강제 설정값 조회
     * @return 0이면 무음 모드 연동(무음/진동 시 셔터음 제거됨), 1이면 강제 소리 발생
     */
    fun isCscShutterSoundMuted(context: Context): Boolean {
        return try {
            val value = Settings.System.getInt(context.contentResolver, CSC_KEY, 1)
            value == 0
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read CSC key as int, fallback to string query: ${e.message}")
            try {
                val strValue = Settings.System.getString(context.contentResolver, CSC_KEY)
                strValue == "0"
            } catch (ex: Exception) {
                Log.e(TAG, "Unable to read CSC setting: ${ex.message}")
                false
            }
        }
    }

    /**
     * 앱에 WRITE_SECURE_SETTINGS 또는 시스템 설정 쓰기 권한이 있는지 확인
     */
    fun hasWritePermission(context: Context): Boolean {
        val secureGranted = context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED
        val systemCanWrite = Settings.System.canWrite(context)
        return secureGranted || systemCanWrite
    }

    /**
     * CSC 셔터음 설정 변경 시도
     * @param mute true이면 0(무음 연동), false이면 1(기본 강제 소리)
     */
    fun setCscShutterSoundMuted(context: Context, mute: Boolean): Result<Unit> {
        val targetValue = if (mute) 0 else 1
        return try {
            val success = Settings.System.putInt(context.contentResolver, CSC_KEY, targetValue)
            if (success) {
                Log.i(TAG, "Successfully updated $CSC_KEY to $targetValue")
                Result.success(Unit)
            } else {
                // String fallback
                val strSuccess = Settings.System.putString(context.contentResolver, CSC_KEY, targetValue.toString())
                if (strSuccess) {
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException("시스템 설정 변경에 실패했습니다. WRITE_SECURE_SETTINGS 권한을 확인해주세요."))
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while modifying $CSC_KEY", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error modifying $CSC_KEY", e)
            Result.failure(e)
        }
    }

    /**
     * PC ADB를 통해 앱에 보안 설정 권한을 부여하는 명령어
     */
    fun getAdbGrantPermissionCommand(context: Context): String {
        return "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
    }

    /**
     * PC ADB를 통해 직접 설정을 변경하는 단독 명령어
     */
    fun getAdbDirectCommand(mute: Boolean): String {
        val value = if (mute) 0 else 1
        return "adb shell settings put system $CSC_KEY $value"
    }

    /**
     * 현재 상태를 ADB로 확인하는 명령어
     */
    fun getAdbCheckCommand(): String {
        return "adb shell settings get system $CSC_KEY"
    }

    /**
     * 개발자 옵션 활성화 여부 확인
     */
    fun isDeveloperOptionsEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            ) != 0
        } catch (_: Exception) {
            false
        }
    }

    /** 휴대전화 정보 화면을 열고 소프트웨어 정보 항목을 강조한다. */
    fun openSoftwareInfoSettings(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).apply {
                putExtra(SETTINGS_FRAGMENT_ARG_KEY, SOFTWARE_INFO_PREFERENCE_KEY)
                flags = settingsTaskFlags(context)
            })
        } catch (_: Exception) {
        }
    }

    private fun settingsTaskFlags(context: Context): Int {
        var flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        if (!context.hasActivity()) flags = flags or Intent.FLAG_ACTIVITY_NEW_TASK
        return flags
    }

    private fun Context.hasActivity(): Boolean {
        var current: Context? = this
        while (current is ContextWrapper) {
            if (current is android.app.Activity) return true
            val base = current.baseContext
            if (base === current) break
            current = base
        }
        return current is android.app.Activity
    }

    /** 개발자 옵션을 열고 무선 디버깅 항목으로 이동해 강조한다. */
    fun openWirelessDebuggingOrDevOptions(context: Context) {
        val developerOptionsIntent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
            setPackage(SETTINGS_PACKAGE)
            putExtra(SETTINGS_FRAGMENT_ARG_KEY, WIRELESS_DEBUGGING_PREFERENCE_KEY)
            flags = settingsTaskFlags(context)
        }

        try {
            context.startActivity(developerOptionsIntent)
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                    putExtra(SETTINGS_FRAGMENT_ARG_KEY, WIRELESS_DEBUGGING_PREFERENCE_KEY)
                    flags = settingsTaskFlags(context)
                })
            } catch (_: Exception) {
                // 설정 앱을 열 수 없는 기기에서는 아무 작업도 하지 않는다.
            }
        }
    }

    /**
     * 토스트 알림의 노출 시간을 길게 연장하여 사용자가 설정 화면 이동 후에도 충분히 읽고 조작할 수 있도록 지원
     */
    fun showExtendedToast(context: Context, message: String, durationMultiplier: Int = 2) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        for (i in 0 until durationMultiplier) {
            handler.postDelayed({
                try {
                    android.widget.Toast.makeText(
                        context,
                        message,
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                } catch (_: Exception) {}
            }, i * 2500L)
        }
    }

    /** 개발자 옵션 상태에 맞는 페어링 설정 화면으로 이동한다. */
    fun openPairingSetupScreen(context: Context) {
        if (!isDeveloperOptionsEnabled(context)) {
            openSoftwareInfoSettings(context)
        } else {
            openWirelessDebuggingOrDevOptions(context)
        }
    }

    /**
     * 페어링 안내 알림을 표시한 뒤 개발자 옵션 상태에 맞는 설정 화면으로 이동한다.
     */
    fun navigateToSmartSetupScreen(context: Context) {
        val devOptionsOff = !isDeveloperOptionsEnabled(context)

        // 1. 상단바 알림창에 헤드업 팝업 및 고정 알림 띄우기 (사용자가 지울 때까지 상단바에 지속 유지)
        com.charmingcolor.shuttersoundzero.ui.notification.PairingNotificationHelper.showPairingNotification(
            context,
            isDevOptionsOff = devOptionsOff
        )

        // 2. 적절한 설정 화면으로 직행
        openPairingSetupScreen(context)
    }

    /**
     * Wi-Fi 네트워크 연결 여부 확인
     */
    fun isWifiConnected(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Wi-Fi 설정 화면 열기
     */
    fun openWifiSettings(context: Context) {
        try {
            val intent = android.content.Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}

