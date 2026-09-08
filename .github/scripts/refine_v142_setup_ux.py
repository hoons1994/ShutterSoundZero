from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    if old not in text:
        raise RuntimeError(f"replacement target not found: {path}\n{old[:200]}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


def replace_all(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    if old not in text:
        raise RuntimeError(f"replacement target not found: {path}\n{old[:200]}")
    p.write_text(text.replace(old, new), encoding="utf-8")


# 1) 설정 완료 직후 무선 디버깅만 안전하게 끌 수 있는 API 추가
path = "app/src/main/java/com/charmingcolor/shuttersoundzero/core/DeveloperOptionsManager.kt"
replace_once(
    path,
    '''    fun canDisableDirectly(context: Context): Boolean {
        return context.checkSelfPermission(WRITE_SECURE_SETTINGS_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * 개발자 옵션 마스터 스위치와 USB/무선 ADB를 함께 끈다.
''',
    '''    fun canDisableDirectly(context: Context): Boolean {
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
     */
    fun disableWirelessDebugging(context: Context): Result<Unit> {
        if (!canDisableDirectly(context)) {
            return Result.failure(
                SecurityException("WRITE_SECURE_SETTINGS 권한이 필요합니다.")
            )
        }

        return runCatching {
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
    }

    /**
     * 사용자가 명시적으로 요청한 경우 개발자 옵션 마스터 스위치와 USB/무선 ADB를 함께 끈다.
'''
)

# 2) 실제 페어링 서비스: 무음 적용 성공 후 무선 디버깅 자동 종료
path = "app/src/main/java/com/charmingcolor/shuttersoundzero/service/PairingForegroundService.kt"
replace_once(
    path,
    'import com.charmingcolor.shuttersoundzero.core.CscMuteManager\n',
    'import com.charmingcolor.shuttersoundzero.core.CscMuteManager\nimport com.charmingcolor.shuttersoundzero.core.DeveloperOptionsManager\n'
)
replace_once(
    path,
    '''        private const val EXTRA_DEV_OPTIONS_OFF = "dev_options_off"
        private const val EXTRA_PAIRING_CODE = "pairing_code"
''',
    '''        private const val EXTRA_DEV_OPTIONS_OFF = "dev_options_off"
        private const val EXTRA_PAIRING_CODE = "pairing_code"
        private const val EXTRA_WIRELESS_DEBUGGING_DISABLED = "wireless_debugging_disabled"
'''
)
replace_once(
    path,
    '''        fun complete(context: Context) {
            try {
                context.startService(
                    Intent(context, PairingForegroundService::class.java).setAction(ACTION_COMPLETE)
                )
            } catch (_: Exception) {
                PairingNotificationHelper.cancelNotification(context)
                PairingNotificationHelper.showSuccessNotification(context)
            }
        }
''',
    '''        fun complete(context: Context, wirelessDebuggingDisabled: Boolean) {
            try {
                context.startService(
                    Intent(context, PairingForegroundService::class.java).apply {
                        action = ACTION_COMPLETE
                        putExtra(EXTRA_WIRELESS_DEBUGGING_DISABLED, wirelessDebuggingDisabled)
                    }
                )
            } catch (_: Exception) {
                PairingNotificationHelper.cancelNotification(context)
                PairingNotificationHelper.showSuccessNotification(
                    context,
                    wirelessDebuggingDisabled = wirelessDebuggingDisabled
                )
            }
        }
'''
)
replace_once(
    path,
    '''            ACTION_STOP -> stopPairing(showSuccess = false)
            ACTION_COMPLETE -> stopPairing(showSuccess = true)
''',
    '''            ACTION_STOP -> stopPairing(showSuccess = false, wirelessDebuggingDisabled = false)
            ACTION_COMPLETE -> stopPairing(
                showSuccess = true,
                wirelessDebuggingDisabled = intent.getBooleanExtra(
                    EXTRA_WIRELESS_DEBUGGING_DISABLED,
                    false
                )
            )
'''
)
replace_once(
    path,
    '''                        if (muteResult.isSuccess) {
                            Log.i(TAG, "Pairing workflow completed successfully")
                            complete(this@PairingForegroundService)

                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(
                                    this@PairingForegroundService,
                                    "✨ 셔터음 제로: 셔터음 무음화 연동이 완료되었습니다!",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
''',
    '''                        if (muteResult.isSuccess) {
                            val wirelessCleanup = DeveloperOptionsManager.disableWirelessDebugging(
                                this@PairingForegroundService
                            )
                            val wirelessDebuggingDisabled = wirelessCleanup.isSuccess
                            if (wirelessDebuggingDisabled) {
                                Log.i(TAG, "Wireless debugging disabled after successful setup")
                            } else {
                                wirelessCleanup.exceptionOrNull()?.let {
                                    logFailure("Unable to disable wireless debugging after setup", it)
                                }
                            }

                            Log.i(TAG, "Pairing workflow completed successfully")
                            complete(
                                this@PairingForegroundService,
                                wirelessDebuggingDisabled = wirelessDebuggingDisabled
                            )

                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(
                                    this@PairingForegroundService,
                                    if (wirelessDebuggingDisabled) {
                                        "✨ 설정 완료! 카메라 무음 설정을 적용하고 무선 디버깅도 껐습니다."
                                    } else {
                                        "✨ 카메라 무음 설정은 완료됐습니다. 무선 디버깅은 직접 꺼 주세요."
                                    },
                                    Toast.LENGTH_LONG
                                ).show()
                            }
'''
)
replace_once(
    path,
    '''    private fun stopPairing(showSuccess: Boolean) {
''',
    '''    private fun stopPairing(showSuccess: Boolean, wirelessDebuggingDisabled: Boolean) {
'''
)
replace_once(
    path,
    '''        if (showSuccess) PairingNotificationHelper.showSuccessNotification(this)
''',
    '''        if (showSuccess) {
            PairingNotificationHelper.showSuccessNotification(
                this,
                wirelessDebuggingDisabled = wirelessDebuggingDisabled
            )
        }
'''
)

# 3) 페어링 성공 알림의 오래된 "스위치를 켜세요" 문구 제거
path = "app/src/main/java/com/charmingcolor/shuttersoundzero/ui/notification/PairingNotificationHelper.kt"
replace_once(
    path,
    '''    fun showSuccessNotification(context: Context) {
''',
    '''    fun showSuccessNotification(
        context: Context,
        wirelessDebuggingDisabled: Boolean = false
    ) {
'''
)
replace_once(
    path,
    '''            .setContentTitle("셔터음 제로: 권한 연동 완료 ✨")
            .setContentText("보안 설정 권한이 연동되었습니다. 앱에서 셔터음 끄기 스위치를 켜보세요.")
''',
    '''            .setContentTitle("셔터음 제로: 설정 완료 ✨")
            .setContentText(
                if (wirelessDebuggingDisabled) {
                    "카메라 무음 설정을 적용했고 무선 디버깅도 껐습니다. 이제 앱을 계속 열어둘 필요가 없습니다."
                } else {
                    "카메라 무음 설정은 완료됐습니다. Wi-Fi 연결 알림을 피하려면 무선 디버깅을 직접 꺼 주세요."
                }
            )
'''
)
replace_once(
    path,
    '            .setContentTitle("무선 페어링 적용 중 ⏳")\n            .setContentText("기기 페어링 및 카메라 셔터음 무음 설정을 적용하고 있습니다...")\n',
    '            .setContentTitle("초기 설정 적용 중 ⏳")\n            .setContentText("권한 연동과 카메라 무음 설정을 적용하고 있습니다...")\n'
)

# 4) 메인 화면을 "한 번 설정하고 잊어버리기" 중심으로 단순화
path = "app/src/main/java/com/charmingcolor/shuttersoundzero/ui/main/MainScreen.kt"
old_control = '''            GroupLabel("셔터음 제어")
            SettingsCard {
                SwitchRow(
                    title = "카메라 셔터음 끄기",
                    subtitle = "진동·무음 모드 시 촬영음 완전 차단",
                    checked = uiState.isCscMuted,
                    onCheckedChange = { enable ->
                        if (!com.charmingcolor.shuttersoundzero.core.CscMuteManager.isSamsungDevice()) {
                            Toast.makeText(
                                context,
                                "⚠️ 이 앱은 삼성 갤럭시 전용 앱입니다. 다른 제조사 기기에서는 사용할 수 없습니다.",
                                Toast.LENGTH_LONG
                            ).show()
                            return@SwitchRow
                        }
                        if (!hasEffectivePermission) {
                            Toast.makeText(
                                context,
                                "권한이 부여되지 않았습니다. 아래 [권한 설정]을 먼저 진행해 주세요.",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            viewModel.toggleCscMute(enable)
                        }
                    }
                )
                RowDivider()
                ActionRow(
                    title = "카메라 열어서 테스트",
                    onClick = {
                        try {
                            val cameraIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(cameraIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "기본 카메라 앱을 실행할 수 없습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
'''
new_control = '''            GroupLabel("현재 상태")
            SettingsCard {
                StatusRow(
                    title = "카메라 무음 설정",
                    valueText = when {
                        uiState.isCscMuted -> "설정 완료"
                        hasEffectivePermission -> "재적용 필요"
                        else -> "1회 설정 필요"
                    },
                    valueColor = when {
                        uiState.isCscMuted -> StatusGreen
                        hasEffectivePermission -> StatusAmber
                        else -> BrandBlueLight
                    },
                    onClick = null
                )
                RowDivider()
                InfoRow(
                    title = when {
                        uiState.isCscMuted -> "이제 앱을 계속 열어둘 필요가 없습니다"
                        hasEffectivePermission -> "권한은 유지되어 있습니다"
                        else -> "처음 한 번만 설정하면 됩니다"
                    },
                    subtitle = when {
                        uiState.isCscMuted ->
                            "진동·무음 모드에서 카메라 셔터음이 나지 않도록 설정되어 있습니다. 적용된 설정은 무선 디버깅을 꺼도 유지됩니다."
                        hasEffectivePermission ->
                            "현재 카메라 무음 설정이 적용되어 있지 않습니다. 무선 디버깅을 잠시 켠 뒤 [카메라 무음 다시 적용]을 눌러 주세요."
                        else ->
                            "[1회 설정 시작]에서 6자리 페어링 코드만 입력하면 권한 연동과 무음 설정을 한 번에 적용하고, 완료 후 무선 디버깅도 자동으로 끕니다."
                    }
                )
                if (hasEffectivePermission) {
                    RowDivider()
                    ActionRow(
                        title = if (uiState.isCscMuted) "원래대로 복원" else "카메라 무음 다시 적용",
                        onClick = { viewModel.toggleCscMute(!uiState.isCscMuted) }
                    )
                }
                RowDivider()
                ActionRow(
                    title = "카메라 열어서 테스트",
                    onClick = {
                        try {
                            val cameraIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(cameraIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "기본 카메라 앱을 실행할 수 없습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
'''
replace_once(path, old_control, new_control)
replace_once(path, '            GroupLabel("권한 설정")\n', '            GroupLabel("초기 설정 및 복구")\n')
replace_once(path, '"셔터음 무음 적용 중"', '"카메라 무음 설정 완료"')
replace_once(
    path,
    '''    InfoRow(
        title = "소프트웨어 업데이트 후 재적용 안내",
        subtitle = "업데이트 후 셔터음 설정이 풀렸다면 무선 디버깅을 켠 뒤 [카메라 셔터음 끄기]를 다시 켜 주세요. [1회 설정 필요]로 표시되면 [권한 요청]도 다시 진행해야 합니다."
    )
''',
    '''    InfoRow(
        title = "재부팅·소프트웨어 업데이트 후",
        subtitle = "설정이 그대로면 아무 작업이 필요 없습니다. 셔터음이 다시 들리면 무선 디버깅을 잠시 켜고 [카메라 무음 다시 적용]을 눌러 주세요. [1회 설정 필요]가 표시될 때만 권한 연동을 다시 진행하면 됩니다."
    )
'''
)
replace_once(
    path,
    '''                text = "무선 디버깅 권한 요청",
''',
    '''                text = "1회 설정",
'''
)
replace_once(
    path,
    '''                text = "개발자 옵션의 [페어링 코드로 기기 페어링] 화면에서 상단바를 내려 6자리 숫자만 입력하면 즉시 권한이 연동됩니다.",
''',
    '''                text = "개발자 옵션의 [페어링 코드로 기기 페어링] 화면에서 상단바를 내려 6자리 숫자만 입력하면 됩니다. 앱이 권한 연동과 무음 설정을 적용한 뒤 무선 디버깅도 자동으로 끕니다.",
'''
)
replace_once(path, 'text = "권한 요청",', 'text = "1회 설정 시작",')
replace_once(
    path,
    '''                text = "보안 설정 권한이 정상 연동되었습니다. 언제든 자유롭게 셔터음을 제어할 수 있습니다.",
''',
    '''                text = "권한 연동은 완료되어 있습니다. 평소에는 무선 디버깅을 꺼두세요. 카메라 무음 상태를 바꾸거나 다시 적용할 때만 잠시 켜면 됩니다.",
'''
)
replace_once(
    path,
    '''                    text = "💡 설정 변경 안내",
''',
    '''                    text = "무선 디버깅이 필요합니다",
'''
)
replace_once(
    path,
    '''                    text = "안드로이드 시스템 보안 정책상 설정을 변경(소리 켜기/끄기)할 때는 일시적으로 [무선 디버깅]이 켜져 있어야 합니다.\\n\\n※ 이미 적용된 카메라 무음 상태는 평소 무선 디버깅을 꺼두셔도 영구히 유지됩니다.",
''',
    '''                    text = "카메라 셔터음 설정을 바꾸는 순간에만 [무선 디버깅]이 필요합니다.\\n\\n[무선 디버깅 켜기]에서 활성화한 뒤 앱으로 돌아와 다시 시도해 주세요. 설정 변경이 끝나면 앱이 무선 디버깅을 다시 끄려고 시도합니다. 이미 적용된 카메라 무음 설정은 무선 디버깅을 꺼도 유지됩니다.",
'''
)
replace_once(
    path,
    '''    }
}

// ── 헤더 ──────────────────────────────────────
''',
    '''    }

    if (uiState.showWirelessDebuggingCleanupHelp) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissWirelessDebuggingCleanupHelp() },
            shape = RoundedCornerShape(24.dp),
            title = { Text("무선 디버깅을 꺼 주세요") },
            text = {
                Text(
                    "카메라 무음 설정은 정상적으로 적용됐지만 이 기기에서는 무선 디버깅을 자동으로 끄지 못했습니다.\\n\\n" +
                        "무선 디버깅을 계속 켜두면 Wi-Fi 연결 시 허용 여부를 묻는 시스템 알림이 다시 나타날 수 있습니다. 지금 무선 디버깅을 꺼 주세요."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissWirelessDebuggingCleanupHelp()
                        com.charmingcolor.shuttersoundzero.core.CscMuteManager.openWirelessDebuggingOrDevOptions(context)
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("무선 디버깅 설정 열기")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissWirelessDebuggingCleanupHelp() }) {
                    Text("나중에")
                }
            }
        )
    }
}

// ── 헤더 ──────────────────────────────────────
'''
)

# 5) 메인 화면 수동 변경도 성공 후 무선 디버깅을 다시 끔
path = "app/src/main/java/com/charmingcolor/shuttersoundzero/ui/main/MainScreenViewModel.kt"
replace_once(
    path,
    'import com.charmingcolor.shuttersoundzero.core.CscMuteManager\n',
    'import com.charmingcolor.shuttersoundzero.core.CscMuteManager\nimport com.charmingcolor.shuttersoundzero.core.DeveloperOptionsManager\n'
)
replace_once(
    path,
    '''    val errorMessage: String? = null,
    val showSwitchFailureHelp: Boolean = false
''',
    '''    val errorMessage: String? = null,
    val showSwitchFailureHelp: Boolean = false,
    val showWirelessDebuggingCleanupHelp: Boolean = false
'''
)
replace_once(
    path,
    '''            if (muteResult.isSuccess) {
                stopMdnsDiscovery()
                prefs.shouldMuteOnBoot = true
                refreshState()
                _uiState.update {
                    it.copy(
                        isWirelessPairingInProgress = false,
                        wirelessPairingError = null,
                        infoMessage = "✨ 자체 무선 페어링 완료! 카메라 셔터음 무음화가 적용되었습니다."
                    )
                }
                onComplete(true)
''',
    '''            if (muteResult.isSuccess) {
                stopMdnsDiscovery()
                prefs.shouldMuteOnBoot = true
                val wirelessCleanup = DeveloperOptionsManager.disableWirelessDebugging(getApplication())
                refreshState()
                _uiState.update {
                    it.copy(
                        isWirelessPairingInProgress = false,
                        wirelessPairingError = null,
                        infoMessage = if (wirelessCleanup.isSuccess) {
                            "✨ 설정 완료! 카메라 무음 설정을 적용하고 무선 디버깅도 껐습니다."
                        } else {
                            "✨ 카메라 무음 설정은 완료됐습니다. 무선 디버깅은 직접 꺼 주세요."
                        },
                        showWirelessDebuggingCleanupHelp = wirelessCleanup.isFailure
                    )
                }
                onComplete(true)
'''
)
replace_once(
    path,
    '''            if (adbResult.isSuccess && actualStateMatchesRequest) {
                prefs.shouldMuteOnBoot = enableMute
                _uiState.update {
                    it.copy(
                        infoMessage = if (enableMute) "카메라 셔터음 무음화가 활성화되었습니다. (진동/무음 시 무음)"
                        else "카메라 셔터음이 기본 상태(소리 발생)로 복원되었습니다.",
                        errorMessage = null
                    )
                }
''',
    '''            if (adbResult.isSuccess && actualStateMatchesRequest) {
                prefs.shouldMuteOnBoot = enableMute
                val wirelessCleanup = DeveloperOptionsManager.disableWirelessDebugging(app)
                _uiState.update {
                    it.copy(
                        infoMessage = when {
                            enableMute && wirelessCleanup.isSuccess ->
                                "카메라 무음 설정을 적용했고 무선 디버깅도 껐습니다."
                            !enableMute && wirelessCleanup.isSuccess ->
                                "카메라 셔터음을 기본 상태로 복원했고 무선 디버깅도 껐습니다."
                            enableMute ->
                                "카메라 무음 설정은 완료됐습니다. 무선 디버깅은 직접 꺼 주세요."
                            else ->
                                "카메라 셔터음은 기본 상태로 복원됐습니다. 무선 디버깅은 직접 꺼 주세요."
                        },
                        errorMessage = null,
                        showWirelessDebuggingCleanupHelp = wirelessCleanup.isFailure
                    )
                }
'''
)
replace_once(
    path,
    '''    fun dismissSwitchFailureHelp() {
        _uiState.update { it.copy(showSwitchFailureHelp = false) }
    }

    fun dismissMessages() {
''',
    '''    fun dismissSwitchFailureHelp() {
        _uiState.update { it.copy(showSwitchFailureHelp = false) }
    }

    fun dismissWirelessDebuggingCleanupHelp() {
        _uiState.update { it.copy(showWirelessDebuggingCleanupHelp = false) }
    }

    fun dismissMessages() {
'''
)

# 6) 빠른 설정 타일도 변경 성공 후 무선 디버깅 자동 종료
path = "app/src/main/java/com/charmingcolor/shuttersoundzero/service/CameraMuteTileService.kt"
replace_once(
    path,
    'import com.charmingcolor.shuttersoundzero.core.CscMuteManager\n',
    'import com.charmingcolor.shuttersoundzero.core.CscMuteManager\nimport com.charmingcolor.shuttersoundzero.core.DeveloperOptionsManager\n'
)
replace_once(
    path,
    '''            if (result.isSuccess) {
                prefs.shouldMuteOnBoot = targetMuted
                val msg = if (targetMuted) {
                    "카메라 셔터음 무음화가 활성화되었습니다. (진동/무음 시 무음)"
                } else {
                    "카메라 셔터음이 기본 상태로 복원되었습니다."
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
''',
    '''            if (result.isSuccess) {
                prefs.shouldMuteOnBoot = targetMuted
                val wirelessCleanup = DeveloperOptionsManager.disableWirelessDebugging(context)
                val baseMessage = if (targetMuted) {
                    "카메라 무음 설정이 적용되었습니다."
                } else {
                    "카메라 셔터음이 기본 상태로 복원되었습니다."
                }
                val msg = if (wirelessCleanup.isSuccess) {
                    "$baseMessage 무선 디버깅도 껐습니다."
                } else {
                    "$baseMessage 무선 디버깅은 직접 꺼 주세요."
                }
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
'''
)

# 7) 업데이트/재부팅은 정상일 때 조용히, 재적용이 필요할 때만 알림
path = "app/src/main/java/com/charmingcolor/shuttersoundzero/receiver/BootReceiver.kt"
replace_once(
    path,
    '''        if (!prefs.shouldMuteOnBoot) {
            showNotification(
                context,
                "소프트웨어 업데이트가 감지되었습니다. 카메라 무음 기능은 현재 사용 중이 아닙니다."
            )
            return
        }
''',
    '''        if (!prefs.shouldMuteOnBoot) {
            Log.i(TAG, "Software update detected; camera mute is not in use")
            return
        }
'''
)
replace_once(
    path,
    '''        if (CscMuteManager.isCscShutterSoundMuted(context)) {
            Log.i(TAG, "CSC camera mute remained active after software update")
            showNotification(
                context,
                "소프트웨어 업데이트가 감지되었습니다. 카메라 무음 설정은 정상적으로 유지되고 있습니다."
            )
            return
        }
''',
    '''        if (CscMuteManager.isCscShutterSoundMuted(context)) {
            Log.i(TAG, "CSC camera mute remained active after software update; no action needed")
            return
        }
'''
)
replace_all(
    path,
    "소프트웨어 업데이트 후 카메라 무음 설정 상태 및 복원 안내",
    "소프트웨어 업데이트 후 카메라 무음 설정 상태 확인 및 재적용 안내"
)

# 8) 설정 화면의 용어를 실제 동작에 맞게 수정
path = "app/src/main/java/com/charmingcolor/shuttersoundzero/ui/settings/SettingsScreen.kt"
replace_once(
    path,
    '''                    title = "개발자 옵션 끄기",
                    subtitle = "설정 완료 후 개발자 옵션과 USB·무선 디버깅 종료",
''',
    '''                    title = "개발자 옵션 전체 끄기",
                    subtitle = "선택 사항 · 앱은 설정 완료 후 무선 디버깅만 자동으로 종료",
'''
)
replace_once(path, '            GroupLabel("자동화")\n', '            GroupLabel("상태 확인")\n')
replace_once(
    path,
    '''                    title = "소프트웨어 업데이트 자동 감지",
                    subtitle = "업데이트를 감지해 셔터음 상태를 확인하고 재적용이 필요하면 알림으로 안내",
''',
    '''                    title = "소프트웨어 업데이트 후 상태 확인",
                    subtitle = "업데이트 후 실제 셔터음 상태를 확인하고, 다시 설정이 필요할 때만 알림",
'''
)
replace_once(
    path,
    '''                    title = "빠른 설정 타일",
                    subtitle = "알림창 하단 [편집]에서 '카메라 무음' 타일 추가 가능"
''',
    '''                    title = "빠른 설정 타일",
                    subtitle = "상태를 바꿀 때만 무선 디버깅이 필요하며, 변경 후 앱이 다시 끄려고 시도합니다."
'''
)
replace_once(
    path,
    '''                        "개발자 옵션과 USB·무선 디버깅을 모두 끕니다.\\n\\n" +
                            "무선 디버깅 연결은 즉시 종료됩니다. 이미 ShutterSoundZero에 부여된 " +
                            "WRITE_SECURE_SETTINGS 권한은 이 작업에서 취소하지 않습니다.\\n\\n" +
                            "카메라 무음 권한 설정을 완료한 뒤 진행하는 것을 권장합니다."
''',
    '''                        "앱은 카메라 무음 설정을 적용한 뒤 무선 디버깅만 자동으로 끄려고 시도합니다.\\n\\n" +
                            "이 메뉴는 개발자 옵션 자체와 USB 디버깅까지 모두 끄고 싶은 경우에만 사용하세요. " +
                            "이미 ShutterSoundZero에 부여된 WRITE_SECURE_SETTINGS 권한과 적용된 카메라 무음 설정은 이 작업에서 취소하지 않습니다."
'''
)

# 9) README: 설정 후 잊어버리는 사용 흐름과 정확한 필요 조건 설명
path = "README.md"
replace_once(
    path,
    "화면을 실시간 감시하는 접근성 기반 방식 대신 삼성 갤럭시의 **순정 시스템(CSC) 설정값**을 활용합니다. 최초 1회 권한 연동 후에는 앱이나 빠른 설정 타일에서 카메라 셔터음 상태를 제어할 수 있으며, 앱은 실제 CSC 값을 다시 읽어 현재 기기 상태와 화면 표시가 일치하도록 관리합니다.",
    "화면을 실시간 감시하는 접근성 기반 방식 대신 삼성 갤럭시의 **순정 시스템(CSC) 설정값**을 활용합니다. 최초 1회 권한 연동과 무음 설정을 마치면 무선 디버깅을 꺼두고 앱을 계속 실행하지 않아도 적용된 설정이 유지됩니다. 나중에 셔터음 상태를 바꾸거나 다시 적용할 때만 무선 디버깅을 잠시 켜면 됩니다."
)
replace_once(
    path,
    '''4. **🎛️ 빠른 설정(Quick Settings) 타일 지원**
   * 앱을 열지 않고도 알림창의 빠른 설정 패널에서 카메라 무음 상태를 빠르게 전환할 수 있습니다.

5. **🔄 재부팅 및 소프트웨어 업데이트 상태 확인·복원**
   * 재부팅이나 소프트웨어 업데이트 이후 CSC 상태를 확인하고 필요한 경우 사용자가 설정한 무음 상태를 복원합니다.
   * 소프트웨어 업데이트 후 시스템 보안 설정 권한이 해제된 경우 메인 화면에서 [권한 요청]을 다시 진행해야 합니다.
   * 소프트웨어 업데이트 자동 감지는 앱 설정에서 켜거나 끌 수 있습니다.
''',
    '''4. **🎛️ 빠른 설정(Quick Settings) 타일 지원**
   * 알림창의 빠른 설정 패널에서 카메라 무음 상태를 바꿀 수 있습니다.
   * 실제 설정을 변경하는 순간에는 무선 디버깅이 켜져 있어야 하며, 변경이 끝나면 앱이 무선 디버깅을 다시 끄려고 시도합니다.

5. **🔄 재부팅 및 소프트웨어 업데이트 후 상태 확인**
   * 재부팅이나 소프트웨어 업데이트 뒤에도 무음 설정이 그대로 유지되어 있으면 아무 작업이 필요 없습니다.
   * 설정이 초기화된 경우에만 무선 디버깅을 잠시 켜고 앱에서 다시 적용하도록 알림으로 안내합니다.
   * 시스템 보안 설정 권한까지 해제된 경우에만 [1회 설정]을 다시 진행해야 합니다.
   * 소프트웨어 업데이트 후 상태 확인 기능은 앱 설정에서 켜거나 끌 수 있습니다.
'''
)
replace_once(
    path,
    '''7. **🧹 개발자 옵션 정리 지원**
   * 최초 권한 설정을 마친 뒤 개발자 옵션과 USB·무선 디버깅을 끄는 과정을 앱 설정에서 도와줍니다.
   * 이미 부여된 `WRITE_SECURE_SETTINGS` 권한은 개발자 옵션을 꺼도 유지됩니다.
''',
    '''7. **🧹 설정 완료 후 무선 디버깅 정리**
   * 최초 설정이나 셔터음 상태 변경이 성공하면 앱이 무선 디버깅을 자동으로 끄려고 시도합니다.
   * 자동으로 끄지 못한 기기에서는 무선 디버깅 설정 화면을 바로 열 수 있도록 안내합니다.
   * 개발자 옵션 자체와 USB 디버깅까지 끄고 싶은 경우에는 앱 설정의 [개발자 옵션 전체 끄기]를 선택적으로 사용할 수 있습니다.
   * 이미 부여된 `WRITE_SECURE_SETTINGS` 권한은 무선 디버깅이나 개발자 옵션을 꺼도 유지됩니다.
'''
)
replace_once(
    path,
    "> **💡 설정 완료 후**: 권한 연동이 끝나면 [무선 디버깅]은 꺼두셔도 이미 적용된 카메라 무음 상태와 `WRITE_SECURE_SETTINGS` 권한은 유지됩니다. 필요하면 앱의 **설정 → 개발자 옵션 끄기** 기능을 사용할 수 있습니다.",
    "> **💡 설정 완료 후**: 앱이 카메라 무음 설정을 적용한 뒤 [무선 디버깅]을 자동으로 끄려고 시도합니다. 자동 종료가 실패하면 앱의 안내에 따라 직접 꺼 주세요. 적용된 카메라 무음 상태와 `WRITE_SECURE_SETTINGS` 권한은 무선 디버깅을 꺼도 유지되므로 앱을 계속 실행해 둘 필요가 없습니다."
)

# 10) 호환성/주석/저장소 문구도 동일한 의미로 정리
path = "docs/COMPATIBILITY.md"
replace_all(path, "재부팅 후 복원", "재부팅 후 상태")
replace_once(
    path,
    "- 기기 재부팅 후 설정이 정상적으로 유지 또는 복원되는지\n- 앱 업데이트 후 기존 설정이 유지되는지\n- One UI 또는 Android 업데이트 이후에도 정상적으로 동작하는지",
    "- 기기 재부팅 후 설정이 정상적으로 유지되는지, 초기화된 경우 재적용 안내가 표시되는지\n- 앱 업데이트 후 기존 설정이 유지되는지\n- One UI 또는 Android 업데이트 이후 실제 상태를 확인하고 필요한 경우 재적용 안내가 표시되는지"
)

path = "app/src/main/AndroidManifest.xml"
replace_all(
    path,
    "부팅 후 소프트웨어 버전 확인 및 필요한 경우 CSC 무음 설정 복구",
    "부팅 후 소프트웨어 버전과 CSC 상태 확인 및 필요한 경우 재적용 안내"
)
replace_all(
    path,
    "소프트웨어 업데이트 감지 결과 및 복원 안내 알림 권한",
    "소프트웨어 업데이트 감지 결과 및 재적용 안내 알림 권한"
)

path = "app/src/main/java/com/charmingcolor/shuttersoundzero/data/PreferencesRepository.kt"
replace_once(
    path,
    '     * 사용자 화면에서는 "소프트웨어 업데이트 자동 감지"로 노출한다.\n',
    '     * 사용자 화면에서는 "소프트웨어 업데이트 후 상태 확인"으로 노출한다.\n'
)

# 11) v1.4.2 사용자 릴리즈 노트
Path(".github/release-notes/v1.4.2.md").write_text(
    """초기 설정을 더 간단하게 만들고 무선 디버깅 안내를 실제 동작에 맞게 정리했습니다.\n\n"
    "• 6자리 페어링이 끝나면 권한 연동과 카메라 무음 설정을 한 번에 적용하고, 성공 시 무선 디버깅도 자동으로 끄도록 개선했습니다.\n"
    "• 자동으로 무선 디버깅을 끄지 못한 경우 Wi-Fi 연결 때 반복 알림이 생기지 않도록 직접 끄는 방법을 바로 안내합니다.\n"
    "• 메인 화면을 현재 상태 중심으로 단순화해 설정 완료 후에는 앱을 계속 열어둘 필요가 없다는 점을 명확히 표시합니다.\n"
    "• 재부팅이나 소프트웨어 업데이트 후 설정이 그대로면 알림을 띄우지 않고, 실제로 재적용이 필요한 경우에만 안내합니다.\n"
    "• 셔터음 상태를 다시 바꾸거나 재적용할 때만 무선 디버깅이 잠시 필요하다는 점을 메인 화면, 설정, 빠른 설정 타일 안내에 일관되게 반영했습니다.\n",
    encoding="utf-8"
)

print("v1.4.2 setup UX refinements applied")
