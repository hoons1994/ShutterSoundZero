package com.charmingcolor.shuttersoundzero.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.charmingcolor.shuttersoundzero.core.DeveloperOptionsManager
import com.charmingcolor.shuttersoundzero.data.PreferencesRepository
import com.charmingcolor.shuttersoundzero.security.AppLockAuthenticator
import com.charmingcolor.shuttersoundzero.security.AppLockSession

private val CardRadius = 20.dp
private val CardPaddingH = 20.dp
private val CardPaddingV = 16.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesRepository.getInstance(context) }
    val versionName = remember(context) { currentVersionName(context) }

    var isSoftwareUpdateCheck by remember {
        mutableStateOf(prefs.isSoftwareUpdateCheckEnabled)
    }
    var isAppLockEnabled by remember { mutableStateOf(prefs.isAppLockEnabled) }
    var isLockSetupInProgress by remember { mutableStateOf(false) }
    var lockErrorMessage by remember { mutableStateOf<String?>(null) }
    var showDeveloperOptionsConfirm by remember { mutableStateOf(false) }
    var showDeveloperOptionsFallback by remember { mutableStateOf(false) }
    var developerOptionsResultMessage by remember { mutableStateOf<String?>(null) }
    var showLicenseDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "설정",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            GroupLabel("보안")
            SettingsCard {
                SwitchRow(
                    title = "앱 잠금",
                    subtitle = "앱 실행 시 지문 또는 PIN·패턴·비밀번호로 확인",
                    checked = isAppLockEnabled,
                    onCheckedChange = { enabled ->
                        if (isLockSetupInProgress) return@SwitchRow

                        if (!enabled) {
                            isAppLockEnabled = false
                            prefs.isAppLockEnabled = false
                            return@SwitchRow
                        }

                        val activity = context.findActivity()
                        if (activity == null || !AppLockAuthenticator.canAuthenticate(context)) {
                            lockErrorMessage =
                                "기기에 지문 또는 PIN·패턴·비밀번호를 먼저 설정한 뒤 다시 시도해 주세요."
                            return@SwitchRow
                        }

                        isLockSetupInProgress = true
                        AppLockAuthenticator.authenticate(
                            activity = activity,
                            title = "앱 잠금 설정",
                            subtitle = "지문 또는 화면 잠금으로 본인 확인해 주세요.",
                            onSuccess = {
                                isLockSetupInProgress = false
                                AppLockSession.unlock()
                                prefs.isAppLockEnabled = true
                                isAppLockEnabled = true
                            },
                            onCancelled = {
                                isLockSetupInProgress = false
                            },
                            onError = {
                                isLockSetupInProgress = false
                                lockErrorMessage =
                                    "인증을 사용할 수 없습니다. 기기의 화면 잠금 설정을 확인해 주세요."
                            }
                        )
                    }
                )
                RowDivider()
                ClickableRow(
                    title = "개발자 옵션 끄기",
                    subtitle = "설정 완료 후 개발자 옵션과 USB·무선 디버깅 종료",
                    onClick = {
                        if (DeveloperOptionsManager.canDisableDirectly(context)) {
                            showDeveloperOptionsConfirm = true
                        } else {
                            showDeveloperOptionsFallback = true
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            GroupLabel("자동화")
            SettingsCard {
                SwitchRow(
                    title = "소프트웨어 업데이트 자동 감지",
                    subtitle = "업데이트 후 카메라 무음 설정 상태를 확인하고 필요한 경우 자동 복원",
                    checked = isSoftwareUpdateCheck,
                    onCheckedChange = { enabled ->
                        isSoftwareUpdateCheck = enabled
                        prefs.isSoftwareUpdateCheckEnabled = enabled
                        if (enabled) {
                            // 켜는 시점의 현재 빌드를 기준으로 저장해 다음 업데이트부터 정확히 감지한다.
                            prefs.lastSoftwareFingerprint = Build.FINGERPRINT
                        }
                    }
                )
                RowDivider()
                InfoRow(
                    title = "빠른 설정 타일",
                    subtitle = "알림창 하단 [편집]에서 '카메라 무음' 타일 추가 가능"
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            GroupLabel("앱 정보")
            SettingsCard {
                InfoRow(
                    title = "버전",
                    subtitle = versionName
                )
                RowDivider()
                InfoRow(
                    title = "개발자",
                    subtitle = "charmingcolor"
                )
                RowDivider()
                ClickableRow(
                    title = "오픈소스 라이선스",
                    subtitle = "GNU General Public License v3.0 (GPL-3.0)",
                    onClick = { showLicenseDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        lockErrorMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { lockErrorMessage = null },
                title = { Text("앱 잠금") },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = { lockErrorMessage = null }) {
                        Text("확인")
                    }
                },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        if (showDeveloperOptionsConfirm) {
            AlertDialog(
                onDismissRequest = { showDeveloperOptionsConfirm = false },
                title = { Text("개발자 옵션 끄기") },
                text = {
                    Text(
                        "개발자 옵션과 USB·무선 디버깅을 모두 끕니다.\n\n" +
                            "무선 디버깅 연결은 즉시 종료됩니다. 이미 ShutterSoundZero에 부여된 " +
                            "WRITE_SECURE_SETTINGS 권한은 이 작업에서 취소하지 않습니다.\n\n" +
                            "카메라 무음 권한 설정을 완료한 뒤 진행하는 것을 권장합니다."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeveloperOptionsConfirm = false
                            val result = DeveloperOptionsManager.disableDeveloperOptions(context)
                            if (result.isSuccess) {
                                developerOptionsResultMessage =
                                    "개발자 옵션과 USB·무선 디버깅을 껐습니다. 필요할 때는 기기 설정에서 개발자 옵션을 다시 활성화할 수 있습니다."
                            } else {
                                showDeveloperOptionsFallback = true
                            }
                        }
                    ) {
                        Text("끄기")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeveloperOptionsConfirm = false }) {
                        Text("취소")
                    }
                },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        if (showDeveloperOptionsFallback) {
            AlertDialog(
                onDismissRequest = { showDeveloperOptionsFallback = false },
                title = { Text("개발자 옵션 끄기") },
                text = {
                    Text(
                        "이 기기에서는 앱이 개발자 옵션을 직접 끌 수 없습니다.\n\n" +
                            "개발자 옵션 화면을 연 뒤 화면 상단의 사용 스위치를 꺼 주세요. " +
                            "가능하면 무선 디버깅도 꺼져 있는지 확인해 주세요."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeveloperOptionsFallback = false
                            DeveloperOptionsManager.openDeveloperOptions(context)
                        }
                    ) {
                        Text("개발자 옵션 열기")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeveloperOptionsFallback = false }) {
                        Text("취소")
                    }
                },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        developerOptionsResultMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { developerOptionsResultMessage = null },
                title = { Text("완료") },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = { developerOptionsResultMessage = null }) {
                        Text("확인")
                    }
                },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        if (showLicenseDialog) {
            AlertDialog(
                onDismissRequest = { showLicenseDialog = false },
                title = {
                    Text(
                        text = "오픈소스 라이선스",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Shutter Sound Zero (셔터음 제로)\nCopyright (C) 2026 charmingcolor",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "본 프로그램은 자유 소프트웨어입니다. 자유 소프트웨어 재단이 공표한 GNU General Public License 버전 3 (GPL-3.0)의 조건에 따라 재배포하거나 수정할 수 있습니다.\n\n이 프로그램은 유용하게 사용되기를 바라는 목적으로 배포되지만, 특정한 목적에 대한 적합성이나 상업성에 대한 묵시적 보증을 포함하여 어떠한 형태의 보증도 제공되지 않습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            thickness = 0.5.dp
                        )
                        Text(
                            text = "사용된 오픈소스 라이브러리:",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "• Android Jetpack / Compose (Apache 2.0)\n• libadb-android (GPL-3.0 / Apache 2.0)\n• Bouncy Castle (Bouncy Castle Licence)\n• Conscrypt (Apache 2.0)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLicenseDialog = false }) {
                        Text("확인")
                    }
                },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}

@Suppress("DEPRECATION")
private fun currentVersionName(context: Context): String {
    val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(0)
        )
    } else {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    return packageInfo.versionName ?: "-"
}

private fun Context.findActivity(): Activity? {
    var currentContext: Context = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) return currentContext
        currentContext = currentContext.baseContext
    }
    return currentContext as? Activity
}

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
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ClickableRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "자세히 보기",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}
