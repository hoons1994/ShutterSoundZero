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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.charmingcolor.shuttersoundzero.core.CscMuteManager
import com.charmingcolor.shuttersoundzero.core.DeveloperOptionsManager
import com.charmingcolor.shuttersoundzero.core.adb.StandaloneAdbManager
import com.charmingcolor.shuttersoundzero.data.PreferencesRepository
import com.charmingcolor.shuttersoundzero.security.AppLockAuthenticator
import com.charmingcolor.shuttersoundzero.security.AppLockSession
import com.charmingcolor.shuttersoundzero.update.AppUpdateManager
import kotlinx.coroutines.launch

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
    val coroutineScope = rememberCoroutineScope()

    var isSoftwareUpdateCheck by remember {
        mutableStateOf(prefs.isSoftwareUpdateCheckEnabled)
    }
    var isAppUpdateAutoCheck by remember {
        mutableStateOf(prefs.isAppUpdateAutoCheckEnabled)
    }
    var isAppLockEnabled by remember { mutableStateOf(prefs.isAppLockEnabled) }
    var isLockSetupInProgress by remember { mutableStateOf(false) }
    var lockErrorMessage by remember { mutableStateOf<String?>(null) }
    var showDeveloperOptionsConfirm by remember { mutableStateOf(false) }
    var showDeveloperOptionsFallback by remember { mutableStateOf(false) }
    var developerOptionsResultMessage by remember { mutableStateOf<String?>(null) }
    var showLicenseDialog by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var showReapplyWirelessDebuggingHelp by remember { mutableStateOf(false) }
    var showRestoreWirelessDebuggingHelp by remember { mutableStateOf(false) }
    var restoreResultMessage by remember { mutableStateOf<String?>(null) }
    var isReapplyInProgress by remember { mutableStateOf(false) }
    var isRestoreInProgress by remember { mutableStateOf(false) }
    var isCscMuted by remember { mutableStateOf(CscMuteManager.isCscShutterSoundMuted(context)) }

    var isUpdateChecking by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<AppUpdateManager.UpdateInfo?>(null) }
    var verifiedUpdate by remember { mutableStateOf<AppUpdateManager.VerifiedUpdate?>(null) }
    var isUpdateDownloading by remember { mutableStateOf(false) }
    var updateDownloadProgress by remember { mutableStateOf(0) }
    var updateStatusMessage by remember { mutableStateOf<String?>(null) }
    var updateErrorMessage by remember { mutableStateOf<String?>(null) }
    var installPermissionHint by remember { mutableStateOf(false) }

    fun checkForAppUpdate() {
        if (isUpdateChecking || isUpdateDownloading) return
        coroutineScope.launch {
            isUpdateChecking = true
            updateErrorMessage = null
            installPermissionHint = false
            try {
                when (val result = AppUpdateManager.checkForUpdate(context)) {
                    is AppUpdateManager.UpdateCheckResult.UpToDate -> {
                        prefs.lastAppUpdateCheckAtMillis = System.currentTimeMillis()
                        prefs.knownAvailableAppUpdateVersion = null
                        updateInfo = null
                        verifiedUpdate = null
                        updateStatusMessage =
                            "현재 최신 버전을 사용하고 있습니다.\n\nv$versionName"
                    }
                    is AppUpdateManager.UpdateCheckResult.Available -> {
                        prefs.lastAppUpdateCheckAtMillis = System.currentTimeMillis()
                        prefs.knownAvailableAppUpdateVersion = result.update.versionName
                        updateInfo = result.update
                        verifiedUpdate = null
                        updateDownloadProgress = 0
                    }
                }
            } catch (error: Throwable) {
                updateErrorMessage = friendlyUpdateError(error)
            } finally {
                isUpdateChecking = false
            }
        }
    }

    fun downloadUpdate(update: AppUpdateManager.UpdateInfo) {
        if (isUpdateDownloading) return
        if (!AppUpdateManager.canRequestPackageInstalls(context)) {
            installPermissionHint = true
            try {
                AppUpdateManager.openInstallPermissionSettings(context)
            } catch (error: Throwable) {
                updateErrorMessage = friendlyUpdateError(error)
            }
            return
        }
        coroutineScope.launch {
            isUpdateDownloading = true
            updateDownloadProgress = 0
            updateErrorMessage = null
            installPermissionHint = false
            try {
                val verified = AppUpdateManager.downloadAndVerify(
                    context = context,
                    update = update,
                    onProgress = { progress -> updateDownloadProgress = progress }
                )
                verifiedUpdate = verified
                if (!AppUpdateManager.launchInstaller(context, verified)) {
                    updateErrorMessage =
                        "Android 설치 화면을 열 수 없습니다. 기기의 설치 권한 설정을 확인해 주세요."
                }
            } catch (error: Throwable) {
                verifiedUpdate = null
                updateErrorMessage = friendlyUpdateError(error)
            } finally {
                isUpdateDownloading = false
            }
        }
    }

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

            GroupLabel("카메라 설정")
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

            Spacer(modifier = Modifier.height(24.dp))

            GroupLabel("보안")
            SettingsCard {
                SwitchRow(
                    title = "앱 잠금",
                    subtitle = "앱 실행 시 지문 또는 PIN·패턴·비밀번호로 확인",
                    checked = isAppLockEnabled,
                    onCheckedChange = { enabled ->
                        if (isLockSetupInProgress) return@SwitchRow

                        val activity = context.findActivity()
                        if (activity == null || !AppLockAuthenticator.canAuthenticate(context)) {
                            lockErrorMessage =
                                "기기에 지문 또는 PIN·패턴·비밀번호를 먼저 설정한 뒤 다시 시도해 주세요."
                            return@SwitchRow
                        }

                        isLockSetupInProgress = true
                        AppLockAuthenticator.authenticate(
                            activity = activity,
                            title = if (enabled) "앱 잠금 설정" else "앱 잠금 해제",
                            subtitle = if (enabled) {
                                "지문 또는 화면 잠금으로 본인 확인해 주세요."
                            } else {
                                "앱 잠금을 해제하려면 지문 또는 화면 잠금으로 본인 확인해 주세요."
                            },
                            onSuccess = {
                                isLockSetupInProgress = false
                                if (enabled) {
                                    AppLockSession.unlock()
                                    prefs.isAppLockEnabled = true
                                    isAppLockEnabled = true
                                } else {
                                    prefs.isAppLockEnabled = false
                                    isAppLockEnabled = false
                                }
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
                    title = "개발자 옵션 전체 끄기",
                    subtitle = "선택 사항 · 앱은 설정 완료 후 무선 디버깅 자동 종료를 시도",
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

            GroupLabel("상태 확인")
            SettingsCard {
                SwitchRow(
                    title = "소프트웨어 업데이트 후 상태 확인",
                    subtitle = "업데이트 후 실제 셔터음 상태를 확인하고, 다시 설정이 필요할 때만 알림",
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
                    subtitle = "상태를 바꿀 때만 무선 디버깅이 필요하며, 변경 후 앱이 다시 끄려고 시도합니다."
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            SettingsHelpSection()

            Spacer(modifier = Modifier.height(24.dp))

            GroupLabel("앱 정보")
            SettingsCard {
                InfoRow(
                    title = "버전",
                    subtitle = versionName
                )
                RowDivider()
                SwitchRow(
                    title = "앱 업데이트 자동 확인",
                    subtitle = "앱을 열 때 하루 한 번 이하로 최신 정식 버전만 확인 · APK는 자동 다운로드하지 않음",
                    checked = isAppUpdateAutoCheck,
                    onCheckedChange = { enabled ->
                        isAppUpdateAutoCheck = enabled
                        prefs.isAppUpdateAutoCheckEnabled = enabled
                        if (enabled) {
                            // 다음 홈 화면 진입 시 즉시 한 번 확인한 뒤 24시간 간격을 적용한다.
                            prefs.lastAppUpdateCheckAtMillis = 0L
                        }
                    }
                )
                RowDivider()
                ClickableRow(
                    title = "앱 업데이트",
                    subtitle = when {
                        isUpdateChecking -> "GitHub Releases에서 최신 버전 확인 중…"
                        updateInfo != null -> "v${updateInfo?.versionName} 사용 가능 · 눌러 변경사항 확인"
                        isAppUpdateAutoCheck -> "자동 확인 켜짐 · 눌러 지금 확인"
                        else -> "자동 확인 꺼짐 · 눌러 지금 확인"
                    },
                    onClick = {
                        val available = updateInfo
                        if (available != null) {
                            updateInfo = available
                        } else {
                            checkForAppUpdate()
                        }
                    }
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

        if (showReapplyWirelessDebuggingHelp) {
            AlertDialog(
                onDismissRequest = { showReapplyWirelessDebuggingHelp = false },
                title = { Text("무선 디버깅을 먼저 켜 주세요") },
                text = {
                    Text(
                        "설정 완료 후 무선 디버깅이 꺼져 있는 것은 정상입니다.\n\n" +
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
            AlertDialog(
                onDismissRequest = { showRestoreWirelessDebuggingHelp = false },
                title = { Text("무선 디버깅을 먼저 켜 주세요") },
                text = {
                    Text(
                        "설정 완료 후 무선 디버깅이 꺼져 있는 것은 정상입니다.\n\n" +
                            "카메라 셔터음을 원래대로 복원하는 동안에만 무선 디버깅이 필요합니다. " +
                            "[무선 디버깅 설정 열기]에서 켠 뒤 앱으로 돌아와 설정 → 카메라 설정의 [카메라 셔터음 원래대로 복원]을 다시 눌러 주세요. 복원이 끝나면 앱이 무선 디버깅을 다시 끄려고 시도합니다."
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
                            "복원이 끝나면 무선 디버깅도 다시 끄려고 시도합니다."
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
                                    isCscMuted = false
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

        updateStatusMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { updateStatusMessage = null },
                title = { Text("앱 업데이트") },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = { updateStatusMessage = null }) {
                        Text("확인")
                    }
                },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        updateErrorMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { updateErrorMessage = null },
                title = { Text("업데이트 확인") },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = { updateErrorMessage = null }) {
                        Text("확인")
                    }
                },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        updateInfo?.let { update ->
            AlertDialog(
                onDismissRequest = {
                    if (!isUpdateDownloading) {
                        updateInfo = null
                        verifiedUpdate = null
                        installPermissionHint = false
                    }
                },
                title = { Text("새 버전 v${update.versionName}") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "현재 v$versionName → v${update.versionName}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = update.releaseNotes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 19.sp
                        )

                        if (isUpdateDownloading) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            Text(
                                text = "APK 다운로드 및 검증 중 · $updateDownloadProgress%",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        verifiedUpdate?.let { verified ->
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            Text(
                                text = "✓ SHA-256 검증 완료\n✓ 패키지 및 버전 검증 완료\n✓ 앱 서명 인증서 일치 확인",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary,
                                lineHeight = 19.sp
                            )
                            Text(
                                text = "검증된 버전: v${verified.versionName} (${verified.versionCode})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (installPermissionHint) {
                            Text(
                                text = "Android의 [이 출처 허용]을 켠 뒤 이 화면으로 돌아와 [업데이트]를 다시 눌러 주세요.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    when {
                        isUpdateDownloading -> {
                            TextButton(onClick = {}, enabled = false) {
                                Text("$updateDownloadProgress%")
                            }
                        }
                        verifiedUpdate == null -> {
                            TextButton(onClick = { downloadUpdate(update) }) {
                                Text("업데이트")
                            }
                        }
                        else -> {
                            TextButton(
                                onClick = {
                                    val verified = verifiedUpdate ?: return@TextButton
                                    if (!AppUpdateManager.canRequestPackageInstalls(context)) {
                                        installPermissionHint = true
                                        try {
                                            AppUpdateManager.openInstallPermissionSettings(context)
                                        } catch (error: Throwable) {
                                            updateErrorMessage = friendlyUpdateError(error)
                                        }
                                    } else if (!AppUpdateManager.launchInstaller(context, verified)) {
                                        updateErrorMessage =
                                            "Android 설치 화면을 열 수 없습니다. 기기의 설치 권한 설정을 확인해 주세요."
                                    }
                                }
                            ) {
                                Text("설치")
                            }
                        }
                    }
                },
                dismissButton = {
                    if (!isUpdateDownloading) {
                        TextButton(
                            onClick = {
                                updateInfo = null
                                verifiedUpdate = null
                                installPermissionHint = false
                            }
                        ) {
                            Text("나중에")
                        }
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
                        "앱은 카메라 무음 설정을 적용한 뒤 무선 디버깅만 자동으로 끄려고 시도합니다.\n\n" +
                            "이 메뉴는 개발자 옵션 자체와 USB 디버깅까지 모두 끄고 싶은 경우에만 사용하세요. " +
                            "이미 ShutterSoundZero에 부여된 WRITE_SECURE_SETTINGS 권한과 적용된 카메라 무음 설정은 이 작업에서 취소하지 않습니다."
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

private fun friendlyUpdateError(error: Throwable): String {
    val message = error.message?.trim().orEmpty()
    return if (message.isBlank()) {
        "업데이트를 확인하지 못했습니다. 네트워크 연결을 확인한 뒤 다시 시도해 주세요."
    } else {
        message
    }
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
