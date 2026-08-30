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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
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
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

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
                text = "무선 디버깅 권한 요청",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "개발자 옵션의 [페어링 코드로 기기 페어링] 화면에서 상단바를 내려 6자리 숫자만 입력하면 즉시 권한이 연동됩니다.",
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
                    text = "권한 요청",
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
                text = "보안 설정 권한이 정상 연동되었습니다. 언제든 자유롭게 셔터음을 제어할 수 있습니다.",
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