from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"교체 대상을 찾지 못했습니다: {path}\n{old}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")


# 버전
replace_once(
    "app/build.gradle.kts",
    '        versionCode = 141\n        versionName = "1.4.1"',
    '        versionCode = 142\n        versionName = "1.4.2"',
)

# WRITE_SECURE_SETTINGS는 권한 연동 상태 확인에만 사용한다.
# 비공개 Settings.System 키의 실제 변경은 일반 앱 UID에서 차단되므로 ADB shell 경로만 사용한다.
csc = Path("app/src/main/java/com/charmingcolor/shuttersoundzero/core/CscMuteManager.kt")
text = csc.read_text(encoding="utf-8")
old_permission = '''    /**
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
'''
new_permission = '''    /**
     * 최초 무선 디버깅 페어링으로 부여한 WRITE_SECURE_SETTINGS 권한이 유지되는지 확인한다.
     *
     * 이 권한만으로 비공개 Settings.System 키를 앱 UID에서 직접 변경할 수 있는 것은 아니다.
     * CSC 값 변경은 shell UID로 실행되는 로컬 ADB `settings put system` 경로를 사용한다.
     */
    fun hasWritePermission(context: Context): Boolean {
        return context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") ==
            PackageManager.PERMISSION_GRANTED
    }
'''
if old_permission not in text:
    raise SystemExit("CscMuteManager 직접 쓰기 제거 대상을 찾지 못했습니다.")
csc.write_text(text.replace(old_permission, new_permission, 1), encoding="utf-8")

# 빠른 설정 타일: 실패 시 앱 UID 직접 쓰기로 우회하지 않는다.
tile = Path("app/src/main/java/com/charmingcolor/shuttersoundzero/service/CameraMuteTileService.kt")
text = tile.read_text(encoding="utf-8")
old_tile = '''            } else {
                // ADB 시도 중 사용자가 권한 연동을 해제했을 수 있으므로 직접 쓰기 전 다시 검증한다.
                val canDirectWrite = !prefs.isPermissionRevokedByUser &&
                    CscMuteManager.hasWritePermission(context)
                val directResult = if (canDirectWrite) {
                    CscMuteManager.setCscShutterSoundMuted(context, targetMuted)
                } else {
                    Result.failure(SecurityException("Permission unavailable"))
                }

                if (directResult.isSuccess) {
                    prefs.shouldMuteOnBoot = targetMuted
                    Toast.makeText(context, "카메라 셔터음 설정이 변경되었습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    prefs.shouldMuteOnBoot = currentMuted
                    Log.w(TAG, "Tile toggle failed via ADB and direct write")
                    Toast.makeText(context, "설정 변경 실패: Wi-Fi 및 무선 디버깅을 확인해 주세요.", Toast.LENGTH_LONG).show()
                }
            }
'''
new_tile = '''            } else {
                prefs.shouldMuteOnBoot = currentMuted
                Log.w(TAG, "Tile toggle failed via ADB")
                Toast.makeText(
                    context,
                    "설정 변경 실패: 무선 디버깅을 켠 뒤 다시 시도해 주세요.",
                    Toast.LENGTH_LONG
                ).show()
            }
'''
if old_tile not in text:
    raise SystemExit("CameraMuteTileService 직접 쓰기 fallback 제거 대상을 찾지 못했습니다.")
tile.write_text(text.replace(old_tile, new_tile, 1), encoding="utf-8")

# 메인 화면 안내
replace_once(
    "app/src/main/java/com/charmingcolor/shuttersoundzero/ui/main/MainScreen.kt",
    '''    InfoRow(
        title = "소프트웨어 업데이트 후 권한 확인",
        subtitle = "기기 소프트웨어 업데이트 후에는 시스템 보안 설정 권한이 해제될 수 있습니다. 업데이트 후 [1회 설정 필요]로 표시되면 [권한 요청]을 다시 진행해 주세요."
    )''',
    '''    InfoRow(
        title = "소프트웨어 업데이트 후 재적용 안내",
        subtitle = "업데이트 후 셔터음 설정이 풀렸다면 무선 디버깅을 켠 뒤 [카메라 셔터음 끄기]를 다시 켜 주세요. [1회 설정 필요]로 표시되면 [권한 요청]도 다시 진행해야 합니다."
    )''',
)

# 설정 화면의 자동 복원 표현 제거
replace_once(
    "app/src/main/java/com/charmingcolor/shuttersoundzero/ui/settings/SettingsScreen.kt",
    '''                SwitchRow(
                    title = "소프트웨어 업데이트 자동 감지",
                    subtitle = "업데이트 후 카메라 무음 설정 상태를 확인하고 필요한 경우 자동 복원",''',
    '''                SwitchRow(
                    title = "소프트웨어 업데이트 자동 감지",
                    subtitle = "업데이트를 감지해 셔터음 상태를 확인하고 재적용이 필요하면 알림으로 안내",''',
)

# ViewModel의 오래된 '자동 복원' 표현만 정리
replace_once(
    "app/src/main/java/com/charmingcolor/shuttersoundzero/ui/main/MainScreenViewModel.kt",
    "                // 실패 시 사용자의 기존 자동 복원 의도는 보존하고, UI는 실제 CSC 값 그대로 유지한다.",
    "                // 실패 시 사용자의 기존 무음 사용 의도는 보존하고, UI는 실제 CSC 값 그대로 유지한다.",
)

# BootReceiver: 앱 UID 직접 쓰기를 제거하고 상태 확인 + 재적용 안내만 수행
boot_receiver = '''package com.charmingcolor.shuttersoundzero.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.charmingcolor.shuttersoundzero.MainActivity
import com.charmingcolor.shuttersoundzero.R
import com.charmingcolor.shuttersoundzero.core.CscMuteManager
import com.charmingcolor.shuttersoundzero.data.PreferencesRepository

/**
 * 기기 재부팅과 앱 업데이트 시 현재 소프트웨어 빌드와 실제 CSC 상태를 확인한다.
 *
 * 비공개 Settings.System CSC 키는 일반 앱 UID에서 직접 수정할 수 없으므로,
 * 재부팅·소프트웨어 업데이트로 무음 상태가 초기화된 경우 자동 복원을 시도하지 않는다.
 * 대신 실제 상태와 권한 연동 상태를 확인해 사용자가 무선 디버깅을 켜고 다시 적용하도록 안내한다.
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"

        // 기존 사용자 알림 채널 설정을 보존하기 위해 ID는 변경하지 않는다.
        private const val CHANNEL_ID = "firmware_updates"
        private const val NOTIFICATION_ID = 2001
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (!isSupportedAction(action)) return

        Log.i(TAG, "Received supported boot/update broadcast")

        val prefs = PreferencesRepository.getInstance(context)
        val currentFingerprint = Build.FINGERPRINT
        val previousFingerprint = prefs.lastSoftwareFingerprint
        val updateCheckEnabled = prefs.isSoftwareUpdateCheckEnabled
        val isSoftwareUpdated = updateCheckEnabled &&
            previousFingerprint != null &&
            previousFingerprint != currentFingerprint

        if (updateCheckEnabled) {
            if (previousFingerprint == null) {
                prefs.lastSoftwareFingerprint = currentFingerprint
                Log.i(TAG, "Initialized software update baseline")
            } else if (previousFingerprint != currentFingerprint) {
                prefs.lastSoftwareFingerprint = currentFingerprint
                Log.i(TAG, "Software update detected")
            }
        }

        if (isSoftwareUpdated) {
            handleDetectedSoftwareUpdate(context, prefs)
            return
        }

        if (isBootAction(action)) {
            notifyIfMuteNeedsReapplyAfterBoot(context, prefs)
        }
    }

    private fun handleDetectedSoftwareUpdate(
        context: Context,
        prefs: PreferencesRepository
    ) {
        if (!prefs.shouldMuteOnBoot) {
            showNotification(
                context,
                "소프트웨어 업데이트가 감지되었습니다. 카메라 무음 기능은 현재 사용 중이 아닙니다."
            )
            return
        }

        if (prefs.isPermissionRevokedByUser) {
            Log.i(TAG, "Permission linkage was revoked by user")
            showNotification(
                context,
                "소프트웨어 업데이트가 감지되었습니다. 권한 연동이 해제되어 있습니다. 무선 디버깅을 켠 뒤 [권한 요청]을 다시 진행해 주세요."
            )
            return
        }

        if (CscMuteManager.isCscShutterSoundMuted(context)) {
            Log.i(TAG, "CSC camera mute remained active after software update")
            showNotification(
                context,
                "소프트웨어 업데이트가 감지되었습니다. 카메라 무음 설정은 정상적으로 유지되고 있습니다."
            )
            return
        }

        val hasPermission = CscMuteManager.hasWritePermission(context)
        Log.w(TAG, "CSC camera mute needs reapply after software update; permission=$hasPermission")
        showNotification(
            context,
            if (hasPermission) {
                "소프트웨어 업데이트 후 카메라 무음 설정이 초기화되었습니다. 무선 디버깅을 켠 뒤 앱에서 [카메라 셔터음 끄기]를 다시 켜 주세요."
            } else {
                "소프트웨어 업데이트 후 카메라 무음 설정과 권한 연동이 초기화되었습니다. 무선 디버깅을 켠 뒤 [권한 요청]을 다시 진행해 주세요."
            }
        )
    }

    private fun notifyIfMuteNeedsReapplyAfterBoot(
        context: Context,
        prefs: PreferencesRepository
    ) {
        if (!prefs.shouldMuteOnBoot || prefs.isPermissionRevokedByUser) return
        if (CscMuteManager.isCscShutterSoundMuted(context)) return

        val hasPermission = CscMuteManager.hasWritePermission(context)
        Log.w(TAG, "CSC camera mute needs reapply after reboot; permission=$hasPermission")
        showNotification(
            context,
            if (hasPermission) {
                "재부팅 후 카메라 무음 설정이 초기화되었습니다. 무선 디버깅을 켠 뒤 앱에서 다시 적용해 주세요."
            } else {
                "재부팅 후 카메라 무음 설정과 권한 상태를 확인해야 합니다. 무선 디버깅을 켠 뒤 [권한 요청]을 다시 진행해 주세요."
            }
        )
    }

    private fun isSupportedAction(action: String?): Boolean {
        return isBootAction(action) || action == Intent.ACTION_MY_PACKAGE_REPLACED
    }

    private fun isBootAction(action: String?): Boolean {
        return action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
    }

    private fun showNotification(context: Context, message: String) {
        try {
            createNotificationChannel(context)

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_qs_camera_mute)
                .setContentTitle("셔터음 제로")
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            val notificationsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

            if (notificationsAllowed) {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            } else {
                Log.i(TAG, "Update/reboot notification suppressed because notification permission is missing")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to post update/reboot notification (${e.javaClass.simpleName})")
        }
    }

    private fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "소프트웨어 업데이트 알림",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "재부팅·소프트웨어 업데이트 후 카메라 무음 상태 및 재적용 안내"
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.createNotificationChannel(channel)
    }
}
'''
Path("app/src/main/java/com/charmingcolor/shuttersoundzero/receiver/BootReceiver.kt").write_text(
    boot_receiver,
    encoding="utf-8",
)

# Manifest 주석도 실제 동작에 맞춤
replace_once(
    "app/src/main/AndroidManifest.xml",
    "    <!-- 부팅 후 소프트웨어 버전 확인 및 필요한 경우 CSC 무음 설정 복구 -->",
    "    <!-- 부팅 후 소프트웨어 버전과 CSC 상태 확인 및 필요한 경우 재적용 안내 -->",
)
replace_once(
    "app/src/main/AndroidManifest.xml",
    "    <!-- 소프트웨어 업데이트 감지 결과 및 복원 안내 알림 권한 (Android 13+) -->",
    "    <!-- 소프트웨어 업데이트 감지 결과 및 재적용 안내 알림 권한 (Android 13+) -->",
)

# README
readme = Path("README.md")
text = readme.read_text(encoding="utf-8")
replacements = [
    (
        "[![Version](https://img.shields.io/badge/Version-1.4.1-orange.svg)]",
        "[![Version](https://img.shields.io/badge/Version-1.4.2-orange.svg)]",
    ),
    (
        "최초 1회 권한 연동 후에는 앱이나 빠른 설정 타일에서 카메라 셔터음 상태를 제어할 수 있으며, 앱은 실제 CSC 값을 다시 읽어 현재 기기 상태와 화면 표시가 일치하도록 관리합니다.",
        "최초 1회 권한 연동 후 앱이나 빠른 설정 타일에서 카메라 셔터음 상태를 제어할 수 있습니다. 실제 설정을 변경할 때는 Android 보안 정책상 무선 디버깅이 켜져 있어야 하며, 앱은 실제 CSC 값을 다시 읽어 현재 기기 상태와 화면 표시가 일치하도록 관리합니다.",
    ),
    (
        '''5. **🔄 재부팅 및 소프트웨어 업데이트 상태 확인·복원**
   * 재부팅이나 소프트웨어 업데이트 이후 CSC 상태를 확인하고 필요한 경우 사용자가 설정한 무음 상태를 복원합니다.
   * 소프트웨어 업데이트 후 시스템 보안 설정 권한이 해제된 경우 메인 화면에서 [권한 요청]을 다시 진행해야 합니다.
   * 소프트웨어 업데이트 자동 감지는 앱 설정에서 켜거나 끌 수 있습니다.''',
        '''5. **🔄 재부팅 및 소프트웨어 업데이트 상태 확인**
   * 재부팅이나 소프트웨어 업데이트 이후 실제 CSC 상태를 확인합니다.
   * 무음 설정이 초기화된 경우 자동 복원으로 표시하지 않고, 무선 디버깅을 켠 뒤 다시 적용하도록 알림으로 안내합니다.
   * 시스템 보안 설정 권한까지 해제된 경우 메인 화면에서 [권한 요청]을 다시 진행해야 합니다.
   * 소프트웨어 업데이트 자동 감지는 앱 설정에서 켜거나 끌 수 있습니다.''',
    ),
    (
        "   * 이미 부여된 `WRITE_SECURE_SETTINGS` 권한은 개발자 옵션을 꺼도 유지됩니다.",
        "   * 이미 부여된 `WRITE_SECURE_SETTINGS` 권한은 개발자 옵션을 꺼도 유지됩니다. 다만 카메라 셔터음 설정을 실제로 변경할 때는 무선 디버깅을 다시 켜야 합니다.",
    ),
    (
        "> **💡 설정 완료 후**: 권한 연동이 끝나면 [무선 디버깅]은 꺼두셔도 이미 적용된 카메라 무음 상태와 `WRITE_SECURE_SETTINGS` 권한은 유지됩니다. 필요하면 앱의 **설정 → 개발자 옵션 끄기** 기능을 사용할 수 있습니다.",
        "> **💡 설정 완료 후**: 권한 연동이 끝나면 [무선 디버깅]을 꺼도 이미 적용된 카메라 무음 상태와 `WRITE_SECURE_SETTINGS` 권한은 유지됩니다. 이후 셔터음 상태를 다시 변경할 때만 무선 디버깅을 켜면 됩니다. 필요하면 앱의 **설정 → 개발자 옵션 끄기** 기능을 사용할 수 있습니다.",
    ),
]
for old, new in replacements:
    if old not in text:
        raise SystemExit(f"README 교체 대상을 찾지 못했습니다: {old[:80]}")
    text = text.replace(old, new, 1)
readme.write_text(text, encoding="utf-8")

# 호환성 문서의 표현도 자동 복원으로 오해되지 않게 조정
compat = Path("docs/COMPATIBILITY.md")
text = compat.read_text(encoding="utf-8")
compat_replacements = [
    ("| 기기 | 모델 번호 | Android | One UI | 보안 패치 | 앱 버전 | 페어링 | 셔터음 설정 | Quick Settings | 재부팅 후 복원 | 상태 | 비고 |",
     "| 기기 | 모델 번호 | Android | One UI | 보안 패치 | 앱 버전 | 페어링 | 셔터음 설정 | Quick Settings | 재부팅 후 상태 | 상태 | 비고 |"),
    ("- 기기 재부팅 후 설정이 정상적으로 유지 또는 복원되는지",
     "- 기기 재부팅 후 설정이 유지되는지, 초기화된 경우 무선 디버깅을 켠 뒤 정상적으로 재적용할 수 있는지"),
    ("- One UI 또는 Android 업데이트 이후에도 정상적으로 동작하는지",
     "- One UI 또는 Android 업데이트 이후 상태를 정확히 감지하고 필요한 재적용 절차를 안내하는지"),
]
for old, new in compat_replacements:
    if old not in text:
        raise SystemExit(f"호환성 문서 교체 대상을 찾지 못했습니다: {old}")
    text = text.replace(old, new, 1)
compat.write_text(text, encoding="utf-8")

# 릴리즈 노트
release_note = '''무선 디버깅 필요 조건과 소프트웨어 업데이트 후 안내를 실제 동작에 맞게 정리했습니다.

• 카메라 셔터음 ON/OFF 및 빠른 설정 타일에서 설정을 변경할 때 무선 디버깅이 켜져 있어야 한다는 점을 명확히 안내합니다.
• 재부팅이나 소프트웨어 업데이트 후 무음 설정이 초기화되면 자동 복원으로 표시하지 않고, 무선 디버깅을 켠 뒤 다시 적용하도록 안내합니다.
• 시스템 보안 설정 권한까지 해제된 경우 [권한 요청]을 다시 진행하도록 안내합니다.
• 일반 앱에서 허용되지 않는 CSC 직접 쓰기 우회 코드를 제거해 실패 동작을 명확하게 처리합니다.
'''
Path(".github/release-notes/v1.4.2.md").write_text(release_note, encoding="utf-8")
