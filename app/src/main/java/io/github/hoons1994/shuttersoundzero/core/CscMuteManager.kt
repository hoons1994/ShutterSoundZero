package io.github.hoons1994.shuttersoundzero.core

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
     * @return 값 0은 true, 값 1은 false. 조회 실패·값 부재·유효하지 않은 값은 null.
     */
    fun readCscMutedState(context: Context): Boolean? = readCscMutedState(
        readInt = { Settings.System.getInt(context.contentResolver, CSC_KEY) },
        readString = { Settings.System.getString(context.contentResolver, CSC_KEY) },
        onReadFailure = { error ->
            Log.w(TAG, "Unable to read CSC setting (${error.javaClass.simpleName})")
        }
    )

    internal fun readCscMutedState(
        readInt: () -> Int,
        readString: () -> String?,
        onReadFailure: (Exception) -> Unit = {}
    ): Boolean? {
        val value = try {
            readInt()
        } catch (e: Exception) {
            onReadFailure(e)
            try {
                readString()?.toIntOrNull()
            } catch (ex: Exception) {
                onReadFailure(ex)
                null
            }
        }
        return when (value) {
            0 -> true
            1 -> false
            else -> null
        }
    }

    /**
     * 최초 무선 디버깅 페어링으로 부여한 WRITE_SECURE_SETTINGS 권한이 유지되는지 확인한다.
     *
     * 이 권한만으로 비공개 Settings.System 키를 앱 UID에서 직접 변경할 수 있는 것은 아니다.
     * CSC 값 변경은 shell UID로 실행되는 로컬 ADB `settings put system` 경로를 사용한다.
     */
    fun hasWritePermission(context: Context): Boolean {
        return context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") ==
            PackageManager.PERMISSION_GRANTED
    }

}
