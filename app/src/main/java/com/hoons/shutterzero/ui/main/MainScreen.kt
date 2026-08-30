package com.hoons.shutterzero.ui.main

import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.hoons.shutterzero.theme.BrandBlueLight
import com.hoons.shutterzero.theme.StatusAmber
import com.hoons.shutterzero.theme.StatusGreen
import com.hoons.shutterzero.ui.dialog.WirelessPairingDialog

private val CardRadius = 20.dp
private val CardPaddingH = 20.dp
private val CardPaddingV = 16.dp

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    var showAdbGuideDialog by remember { mutableStateOf(false) }
    var showWirelessPairingDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.infoMessage, uiState.errorMessage) {
        uiState.infoMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessages()
        }
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessages()
        }
    }

    val hasEffectivePermission = uiState.hasCscPermission

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            AppHeader(
                isMuted = uiState.isCscMuted,
                onRefresh = {
                    viewModel.refreshState()
                    Toast.makeText(context, "상태를 새로고침했습니다.", Toast.LENGTH_SHORT).show()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            GroupLabel("셔터음 제어")
            SettingsCard {
                SwitchRow(
                    title = "카메라 셔터음 끄기",
                    subtitle = "진동·무음 모드 시 촬영음 완전 차단",
                    checked = uiState.isCscMuted,
                    onCheckedChange = { enable ->
                        if (!hasEffectivePermission) showAdbGuideDialog = true
                        else viewModel.toggleCscMute(enable)
                    }
                )
                RowDivider()
                StatusRow(
                    title = if (hasEffectivePermission) "시스템 권한 연동됨" else "1회 권한 설정 필요",
                    valueText = if (hasEffectivePermission) "완료" else "설정 안내",
                    valueColor = if (hasEffectivePermission) StatusGreen else BrandBlueLight,
                    onClick = { showAdbGuideDialog = true }
                )
                RowDivider()
                ActionRow(
                    title = "상단바 알림으로 무선 페어링 (추천)",
                    onClick = { viewModel.startNotificationPairing(context) }
                )
                RowDivider()
                ActionRow(
                    title = "PC 연결 ADB 설정 가이드",
                    onClick = { showAdbGuideDialog = true }
                )
                RowDivider()
                ActionRow(
                    title = "카메라 열어서 테스트",
                    onClick = {
                        try {
                            context.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
                        } catch (e: Exception) {
                            Toast.makeText(context, "카메라를 실행할 수 없습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            GroupLabel("스마트폰 단독 설정 (추천)")
            SettingsCard {
                WirelessPairingSection(
                    isMuted = uiState.isCscMuted,
                    hasPermission = hasEffectivePermission,
                    onStartNotificationPairing = { viewModel.startNotificationPairing(context) },
                    onStartManualPairing = { showWirelessPairingDialog = true },
                    onOpenDeveloperOptions = { viewModel.openDeveloperOptions(context) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            GroupLabel("자동화")
            SettingsCard {
                SwitchRow(
                    title = "재부팅 후 자동 유지",
                    subtitle = "기기가 꺼졌다 켜져도 무음 상태 자동 복원",
                    checked = uiState.isAutoRestoreOnBoot,
                    onCheckedChange = { viewModel.setAutoRestoreOnBoot(it) }
                )
                RowDivider()
                SwitchRow(
                    title = "펌웨어 업데이트 자동 감지",
                    subtitle = "시스템 업데이트로 설정 초기화 시 자동 복원 및 알림",
                    checked = uiState.isFirmwareUpdateCheckEnabled,
                    onCheckedChange = { viewModel.setFirmwareUpdateCheck(it) }
                )
                RowDivider()
                InfoRow(
                    title = "빠른 설정 타일",
                    subtitle = "알림창 하단 [편집]에서 '카메라 무음' 타일 추가 가능"
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showWirelessPairingDialog) {
        DisposableEffect(Unit) {
            viewModel.startMdnsDiscovery()
            onDispose {
                viewModel.stopMdnsDiscovery()
            }
        }
        WirelessPairingDialog(
            detectedPairingPort = uiState.detectedPairingPort,
            isPairing = uiState.isWirelessPairingInProgress,
            errorMessage = uiState.wirelessPairingError,
            onDismiss = { showWirelessPairingDialog = false },
            onPair = { port, code ->
                viewModel.pairAndApplyMute(port, code) { success ->
                    if (success) {
                        showWirelessPairingDialog = false
                    }
                }
            }
        )
    }

    if (showAdbGuideDialog) {
        GuideDialog(
            adbGrantCommand = uiState.adbGrantCommand,
            onDismiss = { showAdbGuideDialog = false },
            onCopy = { cmd ->
                clipboardManager.setText(AnnotatedString(cmd))
                Toast.makeText(context, "명령어가 클립보드에 복사되었습니다.", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

// ── 헤더 ──────────────────────────────────────

@Composable
private fun AppHeader(isMuted: Boolean, onRefresh: () -> Unit) {
    val dotColor by animateColorAsState(
        targetValue = if (isMuted) StatusGreen else Color(0xFFBCC1CA),
        animationSpec = tween(400),
        label = "statusDot"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CardPaddingH)
            .padding(top = 12.dp, bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "셔터 제로",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            TextButton(onClick = onRefresh) {
                Text(
                    text = "새로고침",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = if (isMuted) "셔터음 무음 적용 중" else "셔터음 기본 상태",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── 공통 레이아웃 컴포넌트 ─────────────────────

@Composable
private fun GroupLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.1.sp
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = CardPaddingH + 4.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CardPaddingH),
        shape = RoundedCornerShape(CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column { content() }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = CardPaddingH),
        color = MaterialTheme.colorScheme.surfaceVariant,
        thickness = 0.5.dp
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CardPaddingH, vertical = CardPaddingV),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun StatusRow(
    title: String,
    valueText: String,
    valueColor: Color,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = CardPaddingH, vertical = CardPaddingV),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = valueColor
        )
    }
}

@Composable
private fun ActionRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = CardPaddingH, vertical = CardPaddingV),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "›",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InfoRow(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CardPaddingH, vertical = CardPaddingV)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp
        )
    }
}

// ── 스마트폰 단독 무선 페어링 섹션 ──────────────────────

@Composable
private fun WirelessPairingSection(
    isMuted: Boolean,
    hasPermission: Boolean,
    onStartNotificationPairing: () -> Unit,
    onStartManualPairing: () -> Unit,
    onOpenDeveloperOptions: () -> Unit
) {
    StatusRow(
        title = "자체 무선 페어링 (외부 앱 불필요)",
        valueText = if (hasPermission) "연동 완료" else "설정 필요",
        valueColor = if (hasPermission) StatusGreen else BrandBlueLight,
        onClick = onStartNotificationPairing
    )

    RowDivider()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CardPaddingH, vertical = CardPaddingV),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "상단바 알림으로 1초 만에 무음화",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "1. 아래 [상단바 알림 페어링 시작]을 누르면 개발자 옵션이 열립니다.\n" +
                   "2. [무선 디버깅] ➔ [페어링 코드로 기기 페어링]을 터치합니다.\n" +
                   "3. 화면을 닫지 않고 상단바를 아래로 내려 알림창에 6자리 코드를 입력하면 즉시 무음화됩니다!",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )
    }

    RowDivider()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CardPaddingH, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onStartNotificationPairing,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandBlueLight)
        ) {
            Text(
                text = if (hasPermission) "⚡ 상단바 알림 페어링 다시 실행" else "✨ 상단바 알림으로 무선 페어링 시작 (추천)",
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        OutlinedButton(
            onClick = onStartManualPairing,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("직접 코드 입력 팝업 열기")
        }
        OutlinedButton(
            onClick = onOpenDeveloperOptions,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("⚙️ 개발자 옵션 (무선 디버깅) 바로가기")
        }
    }
}

// ── 가이드 다이얼로그 ──────────────────────────

@Composable
fun GuideDialog(
    adbGrantCommand: String,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(
                text = "PC 연결 ADB 설정 가이드",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "PC와 스마트폰을 USB 케이블로 연결한 뒤, 아래 3단계에 따라 명령어를 1회만 실행하면 시스템 권한이 부여됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )

                // 1단계
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "1단계 · 스마트폰 USB 디버깅 켜기",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "1. 스마트폰 [설정] ➔ [휴대전화 정보] ➔ [소프트웨어 정보]로 이동합니다.\n" +
                               "2. [빌드번호] 항목을 7번 연속 빠르게 터치하여 개발자 모드를 켭니다.\n" +
                               "3. [설정] 첫 화면 맨 아래 생성된 [개발자 옵션]으로 들어갑니다.\n" +
                               "4. [USB 디버깅] 스위치를 켭니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }

                // 2단계
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "2단계 · PC와 케이블 연결 및 권한 승인",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "1. USB 케이블로 스마트폰과 PC를 연결합니다.\n" +
                               "2. 스마트폰 화면에 'USB 디버깅을 허용하시겠습니까?' 팝업이 뜨면 '이 컴퓨터에서 항상 허용'에 체크하고 [허용]을 누릅니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }

                // 3단계
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "3단계 · PC 터미널에서 명령어 실행",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "PC에서 터미널(PowerShell 또는 명령 프롬프트)을 열고, 아래 명령어를 복사하여 붙여넣은 뒤 Enter를 누릅니다:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = adbGrantCommand,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { onCopy(adbGrantCommand) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("명령어 복사")
                            }
                        }
                    }
                    Text(
                        text = "※ 실행 후 오류 메시지 없이 다음 줄로 넘어가면 설정 완료입니다. 앱으로 돌아오시면 스위치가 즉시 활성화됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusGreen,
                        lineHeight = 18.sp
                    )
                }

                // 도움말
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "💡 참고사항",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "• PC에 ADB가 설치되어 있지 않다면 Google 공식 'Platform Tools'를 받거나, 크롬 브라우저에서 'WebADB' 사이트를 이용하시면 별도 프로그램 설치 없이 브라우저에서 바로 실행할 수 있습니다.\n" +
                               "• PC 연결이 번거로우신 경우 메인 화면의 [스마트폰 단독 설정 (Shizuku)]을 이용하시면 스마트폰만으로 1초 만에 설정할 수 있습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 19.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기", fontWeight = FontWeight.SemiBold)
            }
        }
    )
}