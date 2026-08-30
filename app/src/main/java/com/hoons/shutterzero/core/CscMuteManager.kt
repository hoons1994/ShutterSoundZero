package com.hoons.shutterzero.core

import android.content.Context
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
}
