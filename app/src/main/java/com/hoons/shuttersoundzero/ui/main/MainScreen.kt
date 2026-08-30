package com.hoons.shuttersoundzero.ui.main

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.hoons.shuttersoundzero.Settings
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
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.navigation3.runtime.NavKey
import com.hoons.shuttersoundzero.theme.BrandBlueLight
import com.hoons.shuttersoundzero.theme.StatusAmber
import com.hoons.shuttersoundzero.theme.StatusGreen
import com.hoons.shuttersoundzero.ui.dialog.WirelessPairingDialog
import com.hoons.shuttersoundzero.ui.notification.PairingNotificationHelper

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

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startNotificationPairing(context)
        } else {
            Toast.makeText(
                context,
                "상단바 코드 입력을 위해 알림 권한이 필요합니다. [앱 설정]에서 알림을 켜주세요.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val requestPairingNotification: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (!PairingNotificationHelper.areNotificationsEnabled(context)) {
            Toast.makeText(context, "알림이 차단되어 있습니다. 알림 설정을 켜주세요.", Toast.LENGTH_LONG).show()
            PairingNotificationHelper.openNotificationSettings(context)
        } else {
            viewModel.startNotificationPairing(context)
        }
    }

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
                onSettingsClick = { onItemClick(Settings) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            GroupLabel("셔터음 제어")
            SettingsCard {
                SwitchRow(
                    title = "카메라 셔터음 끄기",
                    subtitle = "진동·무음 모드 시 촬영음 완전 차단",
                    checked = uiState.isCscMuted,
                    onCheckedChange = { enable ->
                        if (!com.hoons.shuttersoundzero.core.CscMuteManager.isSamsungDevice()) {
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

            Spacer(modifier = Modifier.height(24.dp))

            GroupLabel("권한 설정")
            SettingsCard {
                PermissionSetupSection(
                    hasPermission = hasEffectivePermission,
                    onStartNotificationPairing = {
                        if (!com.hoons.shuttersoundzero.core.CscMuteManager.isSamsungDevice()) {
                            Toast.makeText(
                                context,
                                "⚠️ 이 앱은 삼성 갤럭시 전용 앱입니다. 다른 제조사 기기에서는 사용할 수 없습니다.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            requestPairingNotification()
                        }
                    },
                    onStartManualPairing = {
                        if (!com.hoons.shuttersoundzero.core.CscMuteManager.isSamsungDevice()) {
                            Toast.makeText(
                                context,
                                "⚠️ 이 앱은 삼성 갤럭시 전용 앱입니다. 다른 제조사 기기에서는 사용할 수 없습니다.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            showWirelessPairingDialog = true
                        }
                    },
                    onOpenAdbGuide = { showAdbGuideDialog = true },
                    onResetPermission = { viewModel.resetPermission() }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            GroupLabel("주의사항 및 법적 고지")
            SettingsCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚖️",
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "불법촬영 및 사생활 침해 금지 안내",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "본 앱은 도서관, 미술관, 학술대회, 강의실 등 정숙이 요구되는 공공장소나 반려동물·아기 촬영 등 정당한 편의를 위해 제공됩니다.\n\n" +
                                "• 타인의 의사에 반하는 불법촬영, 성적 수치심을 유발하는 촬영, 사생활 침해 목적으로 절대 사용할 수 없습니다.\n" +
                                "• 위반 시 「성폭력범죄의 처벌 등에 관한 특례법」(카메라등이용촬영죄) 등 관련 법률에 따라 엄중한 형사 처벌을 받을 수 있습니다.\n" +
                                "• 모든 촬영 행위에 대한 법적 책임은 사용자 본인에게 있습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
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
private fun AppHeader(isMuted: Boolean, onSettingsClick: () -> Unit) {
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

// ── 권한 설정 통합 섹션 ──────────────────────

@Composable
private fun PermissionSetupSection(
    hasPermission: Boolean,
    onStartNotificationPairing: () -> Unit,
    onStartManualPairing: () -> Unit,
    onOpenAdbGuide: () -> Unit,
    onResetPermission: () -> Unit
) {
    StatusRow(
        title = "시스템 보안 설정 권한",
        valueText = if (hasPermission) "연동 완료" else "1회 설정 필요",
        valueColor = if (hasPermission) StatusGreen else BrandBlueLight,
        onClick = if (!hasPermission) onStartNotificationPairing else null
    )

    RowDivider()

    if (!hasPermission) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CardPaddingH, vertical = CardPaddingV),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "원터치 무선 연동 (추천)",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "PC 연결 없이 개발자 옵션의 [페어링 코드로 기기 페어링] 화면에서 상단바를 내려 6자리 숫자만 입력하면 즉시 권한이 부여됩니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 19.sp
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
                    text = "✨ 상단바 알림으로 권한 연동 시작",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenAdbGuide,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("PC 연결 가이드", fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = onStartManualPairing,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("직접 코드 입력", fontSize = 13.sp)
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CardPaddingH, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "보안 설정 권한이 정상 연동되었습니다. PC 연결 없이 언제든 자유롭게 셔터음을 제어할 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onResetPermission) {
                    Text(
                        text = "권한 재설정 (연동 해제)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
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