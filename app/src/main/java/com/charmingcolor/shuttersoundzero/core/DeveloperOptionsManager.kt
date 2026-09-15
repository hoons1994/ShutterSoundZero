package com.charmingcolor.shuttersoundzero.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import com.charmingcolor.shuttersoundzero.core.adb.CameraMuteOperationGate

/**
 * 앱 설정이 끝난 뒤 개발자 옵션과 ADB 디버깅을 안전하게 종료한다.
 *
 * WRITE_SECURE_SETTINGS 권한이 이미 부여된 경우에는 앱이 직접 글로벌 설정을
 * 변경한다. 권한이 없거나 제조사 구현에서 쓰기가 거부되면 설정 화면으로
 * 이동해 사용자가 직접 끌 수 있도록 안내한다.
 */
object DeveloperOptionsManager {
    private const val WRITE_SECURE_SETTINGS_PERMISSION =
        "android.permission.WRITE_SECURE_SETTINGS"

    // Settings.Global.ADB_WIFI_ENABLED is a hidden framework constant.
    private const val ADB_WIFI_ENABLED = "adb_wifi_enabled"

    fun canDisableDirectly(context: Context): Boolean {
        return context.checkSelfPermission(WRITE_SECURE_SETTINGS_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun isWirelessDebuggingEnabled(context: Context): Boolean {
        return Settings.Global.getInt(
            context.contentResolver,
            ADB_WIFI_ENABLED,
            0
        ) != 0
    }

    /**
     * 설정 작업이 끝난 뒤 무선 디버깅만 끈다.
     *
     * 개발자 옵션 전체나 USB 디버깅은 건드리지 않는다. 이미 부여된
     * WRITE_SECURE_SETTINGS 권한과 적용된 CSC 값도 유지된다.
     *
     * 다른 카메라 설정 변경이 실행 중이거나 ADB mutex를 기다리는 동안에는
     * 해당 작업의 연결을 끊지 않도록 정리를 건너뛴다. idle 확인부터 실제 설정
     * 변경까지 같은 작업 게이트에서 수행해 새 ADB 작업과의 TOCTOU 경쟁을 막는다.
     */
    fun disableWirelessDebugging(context: Context): Result<Unit> {
        if (!canDisableDirectly(context)) {
            return Result.failure(
                SecurityException("WRITE_SECURE_SETTINGS 권한이 필요합니다.")
            )
        }

        return runCatching {
            val cleaned = CameraMuteOperationGate.runCleanupIfIdle {
                val changed = Settings.Global.putInt(
                    context.contentResolver,
                    ADB_WIFI_ENABLED,
                    0
                )
                check(changed) { "무선 디버깅 설정을 변경하지 못했습니다." }
                check(!isWirelessDebuggingEnabled(context)) {
                    "무선 디버깅이 아직 활성화되어 있습니다."
                }
            }
            check(cleaned) { "카메라 설정 변경 작업이 진행 중입니다." }
        }
    }

    /**
     * 사용자가 명시적으로 요청한 경우 개발자 옵션 마스터 스위치와 USB/무선 ADB를 함께 끈다.
     * 이미 앱에 부여된 WRITE_SECURE_SETTINGS 권한 자체는 취소하지 않는다.
     */
    fun disableDeveloperOptions(context: Context): Result<Unit> {
        if (!canDisableDirectly(context)) {
            return Result.failure(
                SecurityException("WRITE_SECURE_SETTINGS 권한이 필요합니다.")
            )
        }

        return runCatching {
            val cleaned = CameraMuteOperationGate.runCleanupIfIdle {
                val resolver = context.contentResolver

                // ADB 연결을 먼저 종료한 뒤 개발자 옵션 마스터 상태를 내린다.
                val wirelessAdbDisabled = Settings.Global.putInt(
                    resolver,
                    ADB_WIFI_ENABLED,
                    0
                )
                val usbAdbDisabled = Settings.Global.putInt(
                    resolver,
                    Settings.Global.ADB_ENABLED,
                    0
                )
                val developerOptionsDisabled = Settings.Global.putInt(
                    resolver,
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                    0
                )

                check(wirelessAdbDisabled && usbAdbDisabled && developerOptionsDisabled) {
                    "개발자 옵션 설정을 모두 변경하지 못했습니다."
                }
            }
            check(cleaned) { "카메라 설정 변경 작업이 진행 중입니다." }
        }
    }

    fun openDeveloperOptions(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}
