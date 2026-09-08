from pathlib import Path


def rep(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"missing target in {path}: {old[:160]}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


main = "app/src/main/java/com/charmingcolor/shuttersoundzero/ui/main/MainScreen.kt"
rep(
    main,
    '"현재 카메라 무음 설정이 적용되어 있지 않습니다. 무선 디버깅을 잠시 켠 뒤 [카메라 무음 다시 적용]을 눌러 주세요."',
    '"현재 카메라 무음 설정이 적용되어 있지 않습니다. 설정 → 카메라 설정에서 [카메라 무음 다시 적용]을 사용해 주세요. 상태를 바꾸는 동안에만 무선 디버깅을 잠시 켜면 됩니다."',
)
rep(
    main,
    '''                if (hasEffectivePermission && !uiState.isCscMuted) {
                    RowDivider()
                    ActionRow(
                        title = "카메라 무음 다시 적용",
                        onClick = { viewModel.toggleCscMute(true) }
                    )
                }
''',
    "",
)
rep(
    main,
    '"[1회 설정 시작]에서 6자리 페어링 코드만 입력하면 권한 연동과 무음 설정을 한 번에 적용하고, 완료 후 무선 디버깅도 자동으로 끕니다."',
    '"[1회 설정 시작]에서 6자리 페어링 코드만 입력하면 권한 연동과 무음 설정을 한 번에 적용합니다. 완료 후에는 무선 디버깅 자동 종료를 시도하고, 자동으로 끄지 못하면 바로 안내합니다."',
)
rep(
    main,
    'message = "무선 디버깅 권한을 연동하려면 기기가 Wi-Fi 네트워크에 연결되어 있어야 합니다.\\n\\nWi-Fi 설정에서 네트워크에 연결한 뒤 다시 권한 요청을 진행해 주세요."',
    'message = "1회 설정을 진행하려면 기기가 Wi-Fi 네트워크에 연결되어 있어야 합니다.\\n\\nWi-Fi 설정에서 네트워크에 연결한 뒤 [1회 설정 시작]을 다시 눌러 주세요."',
)

settings = "app/src/main/java/com/charmingcolor/shuttersoundzero/ui/settings/SettingsScreen.kt"
rep(
    settings,
    '''    var showRestoreConfirm by remember { mutableStateOf(false) }
    var showRestoreWirelessDebuggingHelp by remember { mutableStateOf(false) }
    var restoreResultMessage by remember { mutableStateOf<String?>(null) }
    var isRestoreInProgress by remember { mutableStateOf(false) }
''',
    '''    var showRestoreConfirm by remember { mutableStateOf(false) }
    var showReapplyWirelessDebuggingHelp by remember { mutableStateOf(false) }
    var showRestoreWirelessDebuggingHelp by remember { mutableStateOf(false) }
    var restoreResultMessage by remember { mutableStateOf<String?>(null) }
    var isReapplyInProgress by remember { mutableStateOf(false) }
    var isRestoreInProgress by remember { mutableStateOf(false) }
''',
)
rep(
    settings,
    '''            SettingsCard {
                ClickableRow(
                    title = "카메라 셔터음 원래대로 복원",
                    subtitle = "필요할 때만 사용 · 변경하는 동안 무선 디버깅을 잠시 켜야 합니다",
                    onClick = {
                        if (!CscMuteManager.hasWritePermission(context)) {
                            restoreResultMessage =
                                "아직 1회 설정이 완료되지 않았습니다. 메인 화면에서 [1회 설정 시작]을 먼저 진행해 주세요."
                        } else if (!DeveloperOptionsManager.isWirelessDebuggingEnabled(context)) {
                            showRestoreWirelessDebuggingHelp = true
                        } else {
                            showRestoreConfirm = true
                        }
                    }
                )
                RowDivider()
                InfoRow(
                    title = "평소에는 무선 디버깅을 꺼두세요",
                    subtitle = "카메라 무음 설정은 무선 디버깅을 꺼도 유지됩니다. 다시 적용하거나 원래대로 복원할 때만 잠시 켜면 됩니다."
                )
            }
''',
    '''            SettingsCard {
                ClickableRow(
                    title = "카메라 무음 다시 적용",
                    subtitle = "무음 설정이 풀렸을 때 사용 · 적용하는 동안 무선 디버깅을 잠시 켜야 합니다",
                    onClick = {
                        if (!CscMuteManager.hasWritePermission(context)) {
                            restoreResultMessage =
                                "아직 1회 설정이 완료되지 않았습니다. 메인 화면에서 [1회 설정 시작]을 먼저 진행해 주세요."
                        } else if (!DeveloperOptionsManager.isWirelessDebuggingEnabled(context)) {
                            showReapplyWirelessDebuggingHelp = true
                        } else if (!isReapplyInProgress) {
                            isReapplyInProgress = true
                            coroutineScope.launch {
                                val result = StandaloneAdbManager.getInstance(context).setCameraMute(true)
                                if (result.isSuccess && CscMuteManager.isCscShutterSoundMuted(context)) {
                                    prefs.shouldMuteOnBoot = true
                                    val cleanup = DeveloperOptionsManager.disableWirelessDebugging(context)
                                    restoreResultMessage = if (cleanup.isSuccess) {
                                        "카메라 무음 설정을 다시 적용했고 무선 디버깅도 껐습니다."
                                    } else {
                                        "카메라 무음 설정은 다시 적용했습니다. 무선 디버깅은 기기 설정에서 직접 꺼 주세요."
                                    }
                                } else {
                                    restoreResultMessage =
                                        "카메라 무음 설정을 다시 적용하지 못했습니다. 무선 디버깅이 켜져 있는지 확인한 뒤 다시 시도해 주세요."
                                }
                                isReapplyInProgress = false
                            }
                        }
                    }
                )
                RowDivider()
                ClickableRow(
                    title = "카메라 셔터음 원래대로 복원",
                    subtitle = "필요할 때만 사용 · 복원하는 동안 무선 디버깅을 잠시 켜야 합니다",
                    onClick = {
                        if (!CscMuteManager.hasWritePermission(context)) {
                            restoreResultMessage =
                                "아직 1회 설정이 완료되지 않았습니다. 메인 화면에서 [1회 설정 시작]을 먼저 진행해 주세요."
                        } else if (!DeveloperOptionsManager.isWirelessDebuggingEnabled(context)) {
                            showRestoreWirelessDebuggingHelp = true
                        } else {
                            showRestoreConfirm = true
                        }
                    }
                )
                RowDivider()
                InfoRow(
                    title = "평소에는 무선 디버깅을 꺼두세요",
                    subtitle = "무음 다시 적용과 원래대로 복원은 모두 이 화면에서 진행합니다. 실제 상태를 바꾸는 동안에만 무선 디버깅을 잠시 켜면 됩니다."
                )
            }
''',
)
rep(
    settings,
    'subtitle = "선택 사항 · 앱은 설정 완료 후 무선 디버깅만 자동으로 종료",',
    'subtitle = "선택 사항 · 앱은 설정 완료 후 무선 디버깅 자동 종료를 시도",',
)
rep(
    settings,
    '''        if (showRestoreWirelessDebuggingHelp) {
''',
    '''        if (showReapplyWirelessDebuggingHelp) {
            AlertDialog(
                onDismissRequest = { showReapplyWirelessDebuggingHelp = false },
                title = { Text("무선 디버깅을 먼저 켜 주세요") },
                text = {
                    Text(
                        "설정 완료 후 무선 디버깅이 꺼져 있는 것은 정상입니다.\\n\\n" +
                            "카메라 무음 설정을 다시 적용하는 동안에만 무선 디버깅이 필요합니다. " +
                            "[무선 디버깅 설정 열기]에서 켠 뒤 앱으로 돌아와 설정 → 카메라 설정의 [카메라 무음 다시 적용]을 다시 눌러 주세요. 적용이 끝나면 앱이 무선 디버깅을 다시 끄려고 시도합니다."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showReapplyWirelessDebuggingHelp = false
                            CscMuteManager.openWirelessDebuggingOrDevOptions(context)
                        }
                    ) {
                        Text("무선 디버깅 설정 열기")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showReapplyWirelessDebuggingHelp = false }) {
                        Text("취소")
                    }
                },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        if (showRestoreWirelessDebuggingHelp) {
''',
)
rep(
    settings,
    '"[무선 디버깅 설정 열기]에서 켠 뒤 앱으로 돌아와 [카메라 셔터음 원래대로 복원]을 다시 눌러 주세요. 복원이 끝나면 앱이 다시 끕니다."',
    '"[무선 디버깅 설정 열기]에서 켠 뒤 앱으로 돌아와 설정 → 카메라 설정의 [카메라 셔터음 원래대로 복원]을 다시 눌러 주세요. 복원이 끝나면 앱이 무선 디버깅을 다시 끄려고 시도합니다."',
)
rep(
    settings,
    '"복원이 끝나면 무선 디버깅도 다시 끕니다."',
    '"복원이 끝나면 무선 디버깅도 다시 끄려고 시도합니다."',
)

boot = Path("app/src/main/java/com/charmingcolor/shuttersoundzero/receiver/BootReceiver.kt")
text = boot.read_text(encoding="utf-8")
text = text.replace(
    "재부팅·소프트웨어 업데이트로 무음 상태가 초기화된 경우 자동 복원을 시도하지 않는다.",
    "재부팅·소프트웨어 업데이트로 무음 상태가 초기화된 경우 앱이 백그라운드에서 자동 재적용하지 않는다.",
)
text = text.replace("[권한 요청]", "[1회 설정 시작]")
text = text.replace(
    "무선 디버깅을 켠 뒤 앱에서 [카메라 셔터음 끄기]를 다시 켜 주세요.",
    "무선 디버깅을 켠 뒤 설정 → 카메라 설정에서 [카메라 무음 다시 적용]을 눌러 주세요.",
)
text = text.replace(
    "무선 디버깅을 켠 뒤 앱에서 다시 적용해 주세요.",
    "무선 디버깅을 켠 뒤 설정 → 카메라 설정에서 [카메라 무음 다시 적용]을 눌러 주세요.",
)
text = text.replace('"소프트웨어 업데이트 알림"', '"카메라 무음 상태 알림"')
boot.write_text(text, encoding="utf-8")

notes = Path(".github/release-notes/v1.4.2.md")
text = notes.read_text(encoding="utf-8")
text = text.replace(
    "성공 시 무선 디버깅도 자동으로 끄도록 개선했습니다.",
    "성공 후 무선 디버깅 자동 종료를 시도하도록 개선했습니다.",
)
text = text.replace(
    "• 카메라 셔터음을 원래대로 복원하는 기능은 설정 화면으로 옮겼으며, 무선 디버깅이 꺼져 있으면 설정 화면으로 안내한 뒤 다시 시도하도록 흐름을 정리했습니다.",
    "• 카메라 무음 다시 적용과 카메라 셔터음 원래대로 복원 기능을 모두 설정 화면으로 모아, 상태 변경 기능의 위치를 일관되게 정리했습니다.",
)
text = text.replace(
    "• 셔터음 상태를 다시 바꾸거나 재적용할 때만 무선 디버깅이 잠시 필요하다는 점을 메인 화면, 설정, 빠른 설정 타일 안내에 일관되게 반영했습니다.",
    "• 무음 다시 적용이나 원래대로 복원처럼 셔터음 상태를 바꿀 때만 무선 디버깅이 잠시 필요하며, 완료 후에는 다시 끄도록 안내합니다.",
)
notes.write_text(text, encoding="utf-8")

docs = Path("docs/COMPATIBILITY.md")
text = docs.read_text(encoding="utf-8")
text = text.replace(
    "- 앱에서 카메라 셔터음 설정 변경이 정상적으로 적용되는지",
    "- 설정 → 카메라 설정에서 [카메라 무음 다시 적용]이 정상적으로 동작하는지",
)
text = text.replace(
    "- 설정을 다시 원래 상태로 복원할 수 있는지",
    "- 설정 → 카메라 설정에서 셔터음을 다시 원래 상태로 복원할 수 있는지",
)
text = text.replace(
    "- 부팅 후 자동 복원 로직 변경",
    "- 부팅 후 상태 확인 및 재적용 안내 로직 변경",
)
docs.write_text(text, encoding="utf-8")
