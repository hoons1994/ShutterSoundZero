from pathlib import Path
import re


def read(path: str) -> str:
    return Path(path).read_text(encoding="utf-8")


def write(path: str, text: str) -> None:
    Path(path).write_text(text, encoding="utf-8")


def replace_once(path: str, old: str, new: str) -> None:
    text = read(path)
    if old not in text:
        raise RuntimeError(f"replacement target not found: {path}\n{old[:200]}")
    write(path, text.replace(old, new, 1))


# 1) 메인 화면: 상태 표시는 한 곳만, 복원 기능은 설정으로 이동
path = "app/src/main/java/com/charmingcolor/shuttersoundzero/ui/main/MainScreen.kt"
text = read(path)
for old in [
    "import androidx.compose.animation.animateColorAsState\n",
    "import androidx.compose.animation.core.tween\n",
    "import androidx.compose.foundation.background\n",
    "import androidx.compose.foundation.layout.Box\n",
    "import androidx.compose.foundation.shape.CircleShape\n",
    "import androidx.compose.ui.draw.clip\n",
]:
    text = text.replace(old, "")

text = text.replace(
'''            AppHeader(
                isMuted = uiState.isCscMuted,
                onSettingsClick = { onItemClick(Settings) }
            )''',
'''            AppHeader(
                onSettingsClick = { onItemClick(Settings) }
            )'''
)
text = text.replace(
'''                        uiState.isCscMuted ->
                            "진동·무음 모드에서 카메라 셔터음이 나지 않도록 설정되어 있습니다. 적용된 설정은 무선 디버깅을 꺼도 유지됩니다."''',
'''                        uiState.isCscMuted ->
                            "진동·무음 모드에서 카메라 셔터음이 나지 않도록 설정되어 있습니다. 평소에는 무선 디버깅을 꺼두는 것이 정상이며, 나중에 설정을 바꿀 때만 잠시 다시 켜면 됩니다."'''
)
old = '''                if (hasEffectivePermission) {
                    RowDivider()
                    ActionRow(
                        title = if (uiState.isCscMuted) "원래대로 복원" else "카메라 무음 다시 적용",
                        onClick = { viewModel.toggleCscMute(!uiState.isCscMuted) }
                    )
                }'''
new = '''                if (hasEffectivePermission && !uiState.isCscMuted) {
                    RowDivider()
                    ActionRow(
                        title = "카메라 무음 다시 적용",
                        onClick = { viewModel.toggleCscMute(true) }
                    )
                }'''
if old not in text:
    raise RuntimeError("MainScreen action block not found")
text = text.replace(old, new, 1)

pattern = re.compile(r'''@Composable\nprivate fun AppHeader\(isMuted: Boolean, onSettingsClick: \(\) -> Unit\) \{.*?\n\}\n\n// ── 공통 레이아웃 컴포넌트''', re.S)
replacement = '''@Composable
private fun AppHeader(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CardPaddingH)
            .padding(top = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "셔터음 제로",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                letterSpacing = (-0.5).sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        IconButton(onClick = onSettingsClick) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "설정",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── 공통 레이아웃 컴포넌트'''
text, count = pattern.subn(replacement, text, count=1)
if count != 1:
    raise RuntimeError("AppHeader replacement failed")
write(path, text)

# 2) 메인 재적용: 무선 디버깅이 꺼져 있으면 실패를 기다리지 말고 먼저 안내
path = "app/src/main/java/com/charmingcolor/shuttersoundzero/ui/main/MainScreenViewModel.kt"
replace_once(
    path,
'''        if (!CscMuteManager.hasWritePermission(app)) {
            _uiState.update {
                it.copy(errorMessage = "보안 설정 변경 권한이 필요합니다. 아래 [권한 설정]을 진행해 주세요.")
            }
            return
        }

        val previousDesiredMute = prefs.shouldMuteOnBoot
''',
'''        if (!CscMuteManager.hasWritePermission(app)) {
            _uiState.update {
                it.copy(errorMessage = "보안 설정 변경 권한이 필요합니다. 아래 [1회 설정 시작]을 진행해 주세요.")
            }
            return
        }

        if (!DeveloperOptionsManager.isWirelessDebuggingEnabled(app)) {
            _uiState.update {
                it.copy(
                    showSwitchFailureHelp = true,
                    errorMessage = null,
                    infoMessage = null
                )
            }
            return
        }

        val previousDesiredMute = prefs.shouldMuteOnBoot
'''
)

# 3) 빠른 설정 타일도 무선 디버깅 OFF 상태에서 먼저 정확히 안내
path = "app/src/main/java/com/charmingcolor/shuttersoundzero/service/CameraMuteTileService.kt"
replace_once(
    path,
'''        val currentMuted = CscMuteManager.isCscShutterSoundMuted(context)
        val targetMuted = !currentMuted

        // 빠른 체감을 위한 낙관적 타일 업데이트
''',
'''        if (!DeveloperOptionsManager.isWirelessDebuggingEnabled(context)) {
            Toast.makeText(
                context,
                "설정을 바꾸려면 무선 디버깅을 잠시 켠 뒤 타일을 다시 눌러 주세요.",
                Toast.LENGTH_LONG
            ).show()
            updateTileState()
            return
        }

        val currentMuted = CscMuteManager.isCscShutterSoundMuted(context)
        val targetMuted = !currentMuted

        // 빠른 체감을 위한 낙관적 타일 업데이트
'''
)

# 4) 설정 화면으로 '원래대로 복원' 이동 + 무선 디버깅 선행 안내
path = "app/src/main/java/com/charmingcolor/shuttersoundzero/ui/settings/SettingsScreen.kt"
text = read(path)
text = text.replace(
    "import com.charmingcolor.shuttersoundzero.core.DeveloperOptionsManager\n",
    "import com.charmingcolor.shuttersoundzero.core.CscMuteManager\nimport com.charmingcolor.shuttersoundzero.core.DeveloperOptionsManager\nimport com.charmingcolor.shuttersoundzero.core.adb.StandaloneAdbManager\n"
)
text = text.replace(
'''    var showLicenseDialog by remember { mutableStateOf(false) }

    var isUpdateChecking''',
'''    var showLicenseDialog by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var showRestoreWirelessDebuggingHelp by remember { mutableStateOf(false) }
    var restoreResultMessage by remember { mutableStateOf<String?>(null) }
    var isRestoreInProgress by remember { mutableStateOf(false) }

    var isUpdateChecking'''
)
anchor = '''            GroupLabel("보안")
            SettingsCard {'''
insert = '''            GroupLabel("카메라 설정")
            SettingsCard {
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

            Spacer(modifier = Modifier.height(24.dp))

            GroupLabel("보안")
            SettingsCard {'''
if anchor not in text:
    raise RuntimeError("Settings camera section anchor not found")
text = text.replace(anchor, insert, 1)

anchor = '''        lockErrorMessage?.let { message ->
            AlertDialog('''
dialogs = '''        if (showRestoreWirelessDebuggingHelp) {
            AlertDialog(
                onDismissRequest = { showRestoreWirelessDebuggingHelp = false },
                title = { Text("무선 디버깅을 먼저 켜 주세요") },
                text = {
                    Text(
                        "설정 완료 후 무선 디버깅이 꺼져 있는 것은 정상입니다.\n\n" +
                            "카메라 셔터음을 원래대로 복원하는 동안에만 무선 디버깅이 필요합니다. " +
                            "[무선 디버깅 설정 열기]에서 켠 뒤 앱으로 돌아와 [카메라 셔터음 원래대로 복원]을 다시 눌러 주세요. 복원이 끝나면 앱이 다시 끕니다."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showRestoreWirelessDebuggingHelp = false
                            CscMuteManager.openWirelessDebuggingOrDevOptions(context)
                        }
                    ) {
                        Text("무선 디버깅 설정 열기")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRestoreWirelessDebuggingHelp = false }) {
                        Text("취소")
                    }
                },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        if (showRestoreConfirm) {
            AlertDialog(
                onDismissRequest = { if (!isRestoreInProgress) showRestoreConfirm = false },
                title = { Text("카메라 셔터음을 원래대로 복원할까요?") },
                text = {
                    Text(
                        "진동·무음 모드에서도 카메라 셔터음이 나오는 기본 상태로 되돌립니다.\n\n" +
                            "복원이 끝나면 무선 디버깅도 다시 끕니다."
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = !isRestoreInProgress,
                        onClick = {
                            if (isRestoreInProgress) return@TextButton
                            isRestoreInProgress = true
                            coroutineScope.launch {
                                val result = StandaloneAdbManager.getInstance(context).setCameraMute(false)
                                if (result.isSuccess && !CscMuteManager.isCscShutterSoundMuted(context)) {
                                    prefs.shouldMuteOnBoot = false
                                    val cleanup = DeveloperOptionsManager.disableWirelessDebugging(context)
                                    restoreResultMessage = if (cleanup.isSuccess) {
                                        "카메라 셔터음을 기본 상태로 복원했고 무선 디버깅도 껐습니다."
                                    } else {
                                        "카메라 셔터음은 기본 상태로 복원했습니다. 무선 디버깅은 기기 설정에서 직접 꺼 주세요."
                                    }
                                } else {
                                    restoreResultMessage =
                                        "카메라 셔터음을 복원하지 못했습니다. 무선 디버깅이 켜져 있는지 확인한 뒤 다시 시도해 주세요."
                                }
                                isRestoreInProgress = false
                                showRestoreConfirm = false
                            }
                        }
                    ) {
                        Text(if (isRestoreInProgress) "복원 중…" else "원래대로 복원")
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !isRestoreInProgress,
                        onClick = { showRestoreConfirm = false }
                    ) {
                        Text("취소")
                    }
                },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        restoreResultMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { restoreResultMessage = null },
                title = { Text("카메라 설정") },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = { restoreResultMessage = null }) {
                        Text("확인")
                    }
                },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        lockErrorMessage?.let { message ->
            AlertDialog('''
if anchor not in text:
    raise RuntimeError("Settings dialog anchor not found")
text = text.replace(anchor, dialogs, 1)
write(path, text)

# 5) 릴리즈 노트도 새 UI와 일치
path = ".github/release-notes/v1.4.2.md"
text = read(path)
needle = "• 메인 화면을 현재 상태 중심으로 단순화해 설정 완료 후에는 앱을 계속 열어둘 필요가 없다는 점을 명확히 표시합니다.\n"
replacement = (
    "• 메인 화면의 중복된 완료 표시를 정리하고, 설정 완료 후에는 현재 상태와 카메라 테스트에만 집중하도록 단순화했습니다.\n"
    "• 카메라 셔터음을 원래대로 복원하는 기능은 설정 화면으로 옮겼으며, 재적용·복원 전에는 무선 디버깅을 먼저 켜도록 안내합니다.\n"
)
if needle not in text:
    raise RuntimeError("release note target not found")
write(path, text.replace(needle, replacement, 1))

print("v1.4.2 completion UX refinements applied")
