package com.charmingcolor.shuttersoundzero.ui.main

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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.charmingcolor.shuttersoundzero.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.navigation3.runtime.NavKey
import com.charmingcolor.shuttersoundzero.theme.BrandBlueLight
import com.charmingcolor.shuttersoundzero.theme.StatusAmber
import com.charmingcolor.shuttersoundzero.theme.StatusGreen
import com.charmingcolor.shuttersoundzero.ui.notification.PairingNotificationHelper

private val CardRadius = 20.dp
private val CardPaddingH = 20.dp
private val CardPaddingV = 16.dp
private const val AccessLocalNetworkPermission = "android.permission.ACCESS_LOCAL_NETWORK"

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onItemClick: (NavKey) -> Unit = {},
    viewModel: MainScreenViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    var showWifiRequiredDialog by remember { mutableStateOf(false) }

    val startPairing = {
        viewModel.startNotificationPairing(context)
    }

    val localNetworkPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startPairing()
        } else {
            Toast.makeText(
                context,
                "무선 ADB 연결을 위해 로컬 네트워크 권한이 필요합니다. [앱 설정]에서 허용해주세요.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val requestLocalNetworkAccess: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 37 &&
            ContextCompat.checkSelfPermission(context, AccessLocalNetworkPermission) != PackageManager.PERMISSION_GRANTED
        ) {
            localNetworkPermissionLauncher.launch(AccessLocalNetworkPermission)
        } else {
            startPairing()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            requestLocalNetworkAccess()
        } else {
            Toast.makeText(
                context,
                "상단바 코드 입력을 위해 알림 권한이 필요합니다. [앱 설정]에서 알림을 켜주세요.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val requestPairingNotification: () -> Unit = {
        if (!com.charmingcolor.shuttersoundzero.core.CscMuteManager.isWifiConnected(context)) {
            showWifiRequiredDialog = true
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (!PairingNotificationHelper.areNotificationsEnabled(context)) {
            Toast.makeText(context, "알림이 차단되어 있습니다. 알림 설정을 켜주세요.", Toast.LENGTH_LONG).show()
            PairingNotificationHelper.openNotificationSettings(context)
        } else {
            requestLocalNetworkAccess()
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

            GroupLabel("현재 상태")
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

            Spacer(modifier = Modifier.height(24.dp))

            GroupLabel("초기 설정 및 복구")
            SettingsCard {
                PermissionSetupSection(
                    hasPermission = hasEffectivePermission,
                    onStartNotificationPairing = {
                        if (!com.charmingcolor.shuttersoundzero.core.CscMuteManager.isSamsungDevice()) {
                            Toast.makeText(
                                context,
                                "⚠️ 이 앱은 삼성 갤럭시 전용 앱입니다. 다른 제조사 기기에서는 사용할 수 없습니다.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            requestPairingNotification()
                        }
                    },
                    onResetPermission = { viewModel.resetPermission() }
                )
            }

            if (!hasEffectivePermission) {
                Spacer(modifier = Modifier.height(24.dp))

                GroupLabel("알림 설정 변경")
                SettingsCard {
                    NotificationSettingsChangeContent(
                        onOpenNotificationSettings = {
                            PairingNotificationHelper.openNotificationSettings(context)
                        }
                    )
                }
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

    if (showWifiRequiredDialog) {
        com.charmingcolor.shuttersoundzero.ui.components.ModernPromptDialog(
            eyebrow = "연결 확인",
            title = "Wi-Fi 연결이 필요해요",
            message = "무선 디버깅 권한을 연동하려면 기기가 Wi-Fi 네트워크에 연결되어 있어야 합니다.\n\nWi-Fi 설정에서 네트워크에 연결한 뒤 다시 권한 요청을 진행해 주세요.",
            primaryLabel = "Wi-Fi 설정 열기",
            onPrimary = {
                showWifiRequiredDialog = false
                com.charmingcolor.shuttersoundzero.core.CscMuteManager.openWifiSettings(context)
            },
            secondaryLabel = "닫기",
            onSecondary = { showWifiRequiredDialog = false },
            onDismissRequest = { showWifiRequiredDialog = false }
        )
    }

    if (uiState.showSwitchFailureHelp) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissSwitchFailureHelp() },
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = "무선 디버깅이 필요합니다",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = "카메라 셔터음 설정을 바꾸는 순간에만 [무선 디버깅]이 필요합니다.\n\n[무선 디버깅 켜기]에서 활성화한 뒤 앱으로 돌아와 다시 시도해 주세요. 설정 변경이 끝나면 앱이 무선 디버깅을 다시 끄려고 시도합니다. 이미 적용된 카메라 무음 설정은 무선 디버깅을 꺼도 유지됩니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissSwitchFailureHelp()
                        com.charmingcolor.shuttersoundzero.core.CscMuteManager.openWirelessDebuggingOrDevOptions(context)
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("무선 디버깅 켜기")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissSwitchFailureHelp() }) {
                    Text("확인")
                }
            }
        )
    }

    if (uiState.showWirelessDebuggingCleanupHelp) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissWirelessDebuggingCleanupHelp() },
            shape = RoundedCornerShape(24.dp),
            title = { Text("무선 디버깅을 꺼 주세요") },
            text = {
                Text(
                    "카메라 무음 설정은 정상적으로 적용됐지만 이 기기에서는 무선 디버깅을 자동으로 끄지 못했습니다.\n\n" +
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
                text = if (isMuted) "카메라 무음 설정 완료" else "셔터음 기본 상태",
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

@Composable
private fun NotificationSettingsChangeContent(
    onOpenNotificationSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CardPaddingH, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "알림 팝업이 간략하게 보이나요?",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "ShutterSoundZero를 [자세한 팝업]으로 설정하면 페어링 알림에서 [코드 입력] 버튼을 바로 사용할 수 있습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 19.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Button(
            onClick = onOpenNotificationSettings,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandBlueLight)
        ) {
            Text(
                text = "앱 알림 설정 열기",
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

// ── 권한 설정 통합 섹션 ──────────────────────

@Composable
private fun PermissionSetupSection(
    hasPermission: Boolean,
    onStartNotificationPairing: () -> Unit,
    onResetPermission: () -> Unit
) {
    StatusRow(
        title = "시스템 보안 설정 권한",
        valueText = if (hasPermission) "연동 완료" else "1회 설정 필요",
        valueColor = if (hasPermission) StatusGreen else BrandBlueLight,
        onClick = if (!hasPermission) onStartNotificationPairing else null
    )

    RowDivider()

    InfoRow(
        title = "재부팅·소프트웨어 업데이트 후",
        subtitle = "설정이 그대로면 아무 작업이 필요 없습니다. 셔터음이 다시 들리면 무선 디버깅을 잠시 켜고 [카메라 무음 다시 적용]을 눌러 주세요. [1회 설정 필요]가 표시될 때만 권한 연동을 다시 진행하면 됩니다."
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
                text = "1회 설정",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "개발자 옵션의 [페어링 코드로 기기 페어링] 화면에서 상단바를 내려 6자리 숫자만 입력하면 됩니다. 앱이 권한 연동과 무음 설정을 적용한 뒤 무선 디버깅도 자동으로 끕니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 19.sp
            )
        }

        RowDivider()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CardPaddingH, vertical = 12.dp)
        ) {
            Button(
                onClick = onStartNotificationPairing,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandBlueLight)
            ) {
                Text(
                    text = "1회 설정 시작",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
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
                text = "권한 연동은 완료되어 있습니다. 평소에는 무선 디버깅을 꺼두세요. 카메라 무음 상태를 바꾸거나 다시 적용할 때만 잠시 켜면 됩니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick = onResetPermission,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(
                    text = "권한 재설정 (연동 해제)",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}