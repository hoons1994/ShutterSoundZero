from pathlib import Path

settings_path = Path("app/src/main/java/com/charmingcolor/shuttersoundzero/ui/settings/SettingsScreen.kt")
text = settings_path.read_text(encoding="utf-8")

state_anchor = "    var isReapplyInProgress by remember { mutableStateOf(false) }\n    var isRestoreInProgress by remember { mutableStateOf(false) }\n"
state_replacement = state_anchor + "    var isCscMuted by remember { mutableStateOf(CscMuteManager.isCscShutterSoundMuted(context)) }\n"
if state_anchor not in text:
    raise SystemExit("state anchor not found")
text = text.replace(state_anchor, state_replacement, 1)

start_marker = '            GroupLabel("카메라 설정")\n'
end_marker = '            Spacer(modifier = Modifier.height(24.dp))\n\n            GroupLabel("보안")\n'
start = text.find(start_marker)
end = text.find(end_marker, start)
if start == -1 or end == -1:
    raise SystemExit("camera settings section not found")

new_section = '''            GroupLabel("카메라 설정")
            SettingsCard {
                if (isCscMuted) {
                    ClickableRow(
                        title = "카메라 셔터음 원래대로 복원",
                        subtitle = "현재 무음 설정이 적용되어 있습니다 · 복원하는 동안 무선 디버깅을 잠시 켜야 합니다",
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
                } else {
                    ClickableRow(
                        title = "카메라 무음 다시 적용",
                        subtitle = "현재 셔터음이 기본 상태입니다 · 적용하는 동안 무선 디버깅을 잠시 켜야 합니다",
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
                                        isCscMuted = true
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
                }
                RowDivider()
                InfoRow(
                    title = if (isCscMuted) {
                        "현재 카메라 무음이 적용되어 있습니다"
                    } else {
                        "현재 카메라 셔터음이 기본 상태입니다"
                    },
                    subtitle = if (isCscMuted) {
                        "원래대로 복원할 때만 무선 디버깅을 잠시 켜면 됩니다. 복원이 끝나면 앱이 무선 디버깅을 다시 끄려고 시도합니다."
                    } else {
                        "카메라 무음을 다시 적용할 때만 무선 디버깅을 잠시 켜면 됩니다. 적용이 끝나면 앱이 무선 디버깅을 다시 끄려고 시도합니다."
                    }
                )
            }

'''
text = text[:start] + new_section + text[end:]

restore_anchor = "                                    prefs.shouldMuteOnBoot = false\n                                    val cleanup = DeveloperOptionsManager.disableWirelessDebugging(context)\n"
restore_replacement = "                                    prefs.shouldMuteOnBoot = false\n                                    isCscMuted = false\n                                    val cleanup = DeveloperOptionsManager.disableWirelessDebugging(context)\n"
if restore_anchor not in text:
    raise SystemExit("restore success anchor not found")
text = text.replace(restore_anchor, restore_replacement, 1)
settings_path.write_text(text, encoding="utf-8")

build_path = Path("app/build.gradle.kts")
build = build_path.read_text(encoding="utf-8")
build = build.replace('versionCode = 143', 'versionCode = 144', 1)
build = build.replace('versionName = "1.4.3"', 'versionName = "1.4.4"', 1)
build_path.write_text(build, encoding="utf-8")

readme_path = Path("README.md")
readme = readme_path.read_text(encoding="utf-8")
readme = readme.replace('Version-1.4.3-orange.svg', 'Version-1.4.4-orange.svg', 1)
readme_path.write_text(readme, encoding="utf-8")

notes_path = Path(".github/release-notes/v1.4.4.md")
notes_path.write_text(
    "설정 화면의 카메라 동작을 현재 셔터음 상태에 맞게 더 단순하게 정리했습니다.\n\n"
    "• 카메라 무음이 적용된 상태에서는 [카메라 셔터음 원래대로 복원]만 표시합니다.\n"
    "• 원래대로 복원한 상태에서는 같은 위치에 [카메라 무음 다시 적용]을 표시해 필요한 동작 하나만 보이도록 개선했습니다.\n"
    "• 다시 적용 또는 복원이 완료되면 설정 화면의 동작도 즉시 현재 상태에 맞게 전환됩니다.\n"
    "• 실제 셔터음 상태를 변경할 때만 무선 디버깅이 잠시 필요하다는 안내는 유지합니다.\n",
    encoding="utf-8",
)
