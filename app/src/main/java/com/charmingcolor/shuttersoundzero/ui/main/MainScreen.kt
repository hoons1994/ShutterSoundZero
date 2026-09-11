package com.charmingcolor.shuttersoundzero.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.charmingcolor.shuttersoundzero.Settings
import com.charmingcolor.shuttersoundzero.core.CscMuteManager
import com.charmingcolor.shuttersoundzero.data.PreferencesRepository
import com.charmingcolor.shuttersoundzero.theme.BrandBlueLight
import com.charmingcolor.shuttersoundzero.theme.StatusAmber
import com.charmingcolor.shuttersoundzero.theme.StatusGreen
import com.charmingcolor.shuttersoundzero.ui.notification.PairingNotificationHelper
import com.charmingcolor.shuttersoundzero.update.AppUpdateManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val CardRadius = 24.dp
private val ScreenPadding = 20.dp
private const val AccessLocalNetworkPermission = "android.permission.ACCESS_LOCAL_NETWORK"

private enum class HomeStatus {
    READY,
    REAPPLY_REQUIRED,
    SETUP_REQUIRED
}

private enum class StepVisualState {
    COMPLETE,
    CURRENT,
    PENDING
}

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
    val prefs = remember { PreferencesRepository.getInstance(context) }
    val updateScope = rememberCoroutineScope()

    var showWifiRequiredDialog by remember { mutableStateOf(false) }
    var availableAppUpdateVersion by remember { mutableStateOf<String?>(null) }
    var isAppUpdatePreparing by remember { mutableStateOf(false) }
    var isSilentAppUpdateChecking by remember { mutableStateOf(false) }
    var appUpdateProgress by remember { mutableStateOf(0) }
    var appUpdateStatusMessage by remember { mutableStateOf<String?>(null) }
    var appUpdateErrorMessage by remember { mutableStateOf<String?>(null) }

    fun refreshKnownAppUpdateVersion() {
        val knownVersion = prefs.knownAvailableAppUpdateVersion
        val currentVersion = AppUpdateManager.installedVersionName(context)
        if (knownVersion != null && AppUpdateManager.isNewerVersion(knownVersion, currentVersion)) {
            availableAppUpdateVersion = knownVersion
        } else {
            availableAppUpdateVersion = null
            if (knownVersion != null) prefs.knownAvailableAppUpdateVersion = null
        }
    }

    val startHomeAppUpdate: () -> Unit = {
        if (!isAppUpdatePreparing) {
            if (!AppUpdateManager.canRequestPackageInstalls(context)) {
                Toast.makeText(
                    context,
                    "최초 1회 [이 출처 허용]을 켠 뒤 앱으로 돌아와 [업데이트]를 다시 눌러 주세요.",
                    Toast.LENGTH_LONG
                ).show()
                try {
                    AppUpdateManager.openInstallPermissionSettings(context)
                } catch (error: Exception) {
                    appUpdateErrorMessage = friendlyHomeUpdateError(error)
                }
            } else {
                updateScope.launch {
                    isAppUpdatePreparing = true
                    appUpdateProgress = 0
                    appUpdateErrorMessage = null
                    try {
                        val update = when (val result = AppUpdateManager.checkForUpdate(context)) {
                            is AppUpdateManager.UpdateCheckResult.UpToDate -> {
                                prefs.lastAppUpdateCheckAtMillis = System.currentTimeMillis()
                                prefs.knownAvailableAppUpdateVersion = null
                                availableAppUpdateVersion = null
                                appUpdateStatusMessage = "이미 최신 버전을 사용하고 있습니다."
                                return@launch
                            }
                            is AppUpdateManager.UpdateCheckResult.Available -> {
                                prefs.lastAppUpdateCheckAtMillis = System.currentTimeMillis()
                                prefs.knownAvailableAppUpdateVersion = result.update.versionName
                                availableAppUpdateVersion = result.update.versionName
                                result.update
                            }
                        }

                        val verified = AppUpdateManager.downloadAndVerify(
                            context = context,
                            update = update,
                            onProgress = { progress -> appUpdateProgress = progress }
                        )
                        if (!AppUpdateManager.launchInstaller(context, verified)) {
                            error("Android 설치 화면을 열 수 없습니다. 기기의 설치 권한 설정을 확인해 주세요.")
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        appUpdateErrorMessage = friendlyHomeUpdateError(error)
                    } finally {
                        isAppUpdatePreparing = false
                    }
                }
            }
        }
    }

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
                "기기 연결을 위해 로컬 네트워크 권한이 필요합니다. [앱 설정]에서 허용해 주세요.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val requestLocalNetworkAccess: () -> Unit = {
        if (
            Build.VERSION.SDK_INT >= 37 &&
            ContextCompat.checkSelfPermission(
                context,
                AccessLocalNetworkPermission
            ) != PackageManager.PERMISSION_GRANTED
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
                "6자리 코드 입력을 위해 알림 권한이 필요합니다. [앱 설정]에서 알림을 켜 주세요.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val requestPairingNotification: () -> Unit = {
        if (!CscMuteManager.isWifiConnected(context)) {
            showWifiRequiredDialog = true
        } else if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (!PairingNotificationHelper.areNotificationsEnabled(context)) {
            Toast.makeText(
                context,
                "알림이 꺼져 있습니다. 코드 입력을 위해 알림을 켜 주세요.",
                Toast.LENGTH_LONG
            ).show()
            PairingNotificationHelper.openNotificationSettings(context)
        } else {
            requestLocalNetworkAccess()
        }
    }

    fun checkAppUpdateSilentlyIfDue() {
        if (isAppUpdatePreparing || isSilentAppUpdateChecking) return
        val now = System.currentTimeMillis()
        if (
            !AppUpdateManager.isAutomaticCheckDue(
                enabled = prefs.isAppUpdateAutoCheckEnabled,
                lastCheckAtMillis = prefs.lastAppUpdateCheckAtMillis,
                nowMillis = now
            )
        ) {
            return
        }

        prefs.lastAppUpdateCheckAtMillis = now
        isSilentAppUpdateChecking = true
        updateScope.launch {
            try {
                when (val result = AppUpdateManager.checkForUpdate(context)) {
                    is AppUpdateManager.UpdateCheckResult.UpToDate -> {
                        prefs.knownAvailableAppUpdateVersion = null
                        availableAppUpdateVersion = null
                    }
                    is AppUpdateManager.UpdateCheckResult.Available -> {
                        prefs.knownAvailableAppUpdateVersion = result.update.versionName
                        availableAppUpdateVersion = result.update.versionName
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // 자동 확인은 조용히 실패한다. 수동 확인은 설정 화면에서 언제든 가능하다.
            } finally {
                isSilentAppUpdateChecking = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshKnownAppUpdateVersion()
        checkAppUpdateSilentlyIfDue()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshState()
                refreshKnownAppUpdateVersion()
                checkAppUpdateSilentlyIfDue()
            }
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

    val homeStatus = when {
        uiState.isCscMuted && uiState.hasCscPermission -> HomeStatus.READY
        uiState.hasCscPermission -> HomeStatus.REAPPLY_REQUIRED
        else -> HomeStatus.SETUP_REQUIRED
    }

    val openCamera: () -> Unit = {
        try {
            val cameraIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(cameraIntent)
        } catch (_: Exception) {
            Toast.makeText(context, "기본 카메라 앱을 실행할 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    val reapplyAction: () -> Unit = {
        viewModel.toggleCscMute(true)
    }
    val setupAction: () -> Unit = {
        if (!CscMuteManager.isSamsungDevice()) {
            Toast.makeText(
                context,
                "이 앱은 삼성 갤럭시 전용 앱입니다.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            requestPairingNotification()
        }
    }
    val primaryAction: () -> Unit = when (homeStatus) {
        HomeStatus.READY -> openCamera
        HomeStatus.REAPPLY_REQUIRED -> reapplyAction
        HomeStatus.SETUP_REQUIRED -> setupAction
    }

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
            AppHeader(onSettingsClick = { onItemClick(Settings) })
            Spacer(modifier = Modifier.height(10.dp))

            StatusHeroCard(
                status = homeStatus,
                onPrimaryAction = primaryAction,
                onSecondaryAction = if (homeStatus == HomeStatus.READY) null else openCamera
            )

            if (homeStatus == HomeStatus.SETUP_REQUIRED) {
                Spacer(modifier = Modifier.height(20.dp))
                SetupProgressCard(uiState)
            }

            if (homeStatus == HomeStatus.REAPPLY_REQUIRED) {
                Spacer(modifier = Modifier.height(20.dp))
                RecoveryCard(
                    wirelessDebuggingEnabled = uiState.isWirelessDebuggingEnabled,
                    onReapply = { viewModel.toggleCscMute(true) }
                )
            }

            availableAppUpdateVersion?.let { version ->
                Spacer(modifier = Modifier.height(20.dp))
                UpdateCard(
                    version = version,
                    isPreparing = isAppUpdatePreparing,
                    onUpdate = startHomeAppUpdate
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    if (isAppUpdatePreparing) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("앱 업데이트") },
            text = {
                Text(
                    if (appUpdateProgress > 0) {
                        "업데이트 파일을 확인하고 있습니다. · $appUpdateProgress%"
                    } else {
                        "최신 버전을 확인하고 업데이트를 준비하고 있습니다."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {}, enabled = false) {
                    Text(if (appUpdateProgress > 0) "$appUpdateProgress%" else "준비 중…")
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    appUpdateStatusMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { appUpdateStatusMessage = null },
            title = { Text("앱 업데이트") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { appUpdateStatusMessage = null }) { Text("확인") }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    appUpdateErrorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { appUpdateErrorMessage = null },
            title = { Text("앱 업데이트") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { appUpdateErrorMessage = null }) { Text("확인") }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showWifiRequiredDialog) {
        com.charmingcolor.shuttersoundzero.ui.components.ModernPromptDialog(
            eyebrow = "연결 확인",
            title = "Wi-Fi 연결이 필요해요",
            message = "1회 설정을 진행하려면 Wi-Fi에 연결되어 있어야 합니다.\n\nWi-Fi에 연결한 뒤 다시 [1회 설정 시작]을 눌러 주세요.",
            primaryLabel = "Wi-Fi 설정 열기",
            onPrimary = {
                showWifiRequiredDialog = false
                CscMuteManager.openWifiSettings(context)
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
            title = { Text("무선 디버깅을 켜 주세요") },
            text = {
                Text(
                    "카메라 설정을 바꾸는 동안에만 무선 디버깅이 필요합니다.\n\n" +
                        "무선 디버깅을 켠 뒤 앱으로 돌아와 [다시 적용하기]를 눌러 주세요. 완료 후에는 앱이 다시 끄려고 시도합니다."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissSwitchFailureHelp()
                        CscMuteManager.openWirelessDebuggingOrDevOptions(context)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("무선 디버깅 켜기")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissSwitchFailureHelp() }) {
                    Text("나중에")
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
                    "카메라 무음 설정은 완료됐지만 이 기기에서는 무선 디버깅을 자동으로 끄지 못했습니다.\n\n" +
                        "기기 설정에서 무선 디버깅을 직접 꺼 주세요."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissWirelessDebuggingCleanupHelp()
                        CscMuteManager.openWirelessDebuggingOrDevOptions(context)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("설정 열기")
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

private fun friendlyHomeUpdateError(error: Exception): String {
    return error.message?.trim().takeUnless { it.isNullOrBlank() }
        ?: "업데이트를 준비하지 못했습니다. 네트워크 연결을 확인한 뒤 다시 시도해 주세요."
}

@Composable
private fun AppHeader(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding)
            .padding(top = 14.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "셔터음 제로",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "필요한 상태만 한눈에 확인하세요",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onSettingsClick) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "설정",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusHeroCard(
    status: HomeStatus,
    onPrimaryAction: () -> Unit,
    onSecondaryAction: (() -> Unit)?
) {
    val title = when (status) {
        HomeStatus.READY -> "카메라 무음 설정 완료"
        HomeStatus.REAPPLY_REQUIRED -> "카메라 무음 다시 적용 필요"
        HomeStatus.SETUP_REQUIRED -> "처음 한 번만 설정해 주세요"
    }
    val subtitle = when (status) {
        HomeStatus.READY -> "진동·무음 모드에서 촬영음이 나지 않도록 설정되어 있습니다."
        HomeStatus.REAPPLY_REQUIRED -> "기기 연결은 유지되어 있어 카메라 설정만 다시 적용하면 됩니다."
        HomeStatus.SETUP_REQUIRED -> "3단계 안내에 따라 연결하면 이후에는 앱을 계속 열어둘 필요가 없습니다."
    }
    val badgeText = when (status) {
        HomeStatus.READY -> "정상"
        HomeStatus.REAPPLY_REQUIRED -> "조치 필요"
        HomeStatus.SETUP_REQUIRED -> "설정 필요"
    }
    val badgeColor = when (status) {
        HomeStatus.READY -> StatusGreen
        HomeStatus.REAPPLY_REQUIRED -> StatusAmber
        HomeStatus.SETUP_REQUIRED -> BrandBlueLight
    }
    val primaryLabel = when (status) {
        HomeStatus.READY -> "카메라 열어서 확인"
        HomeStatus.REAPPLY_REQUIRED -> "다시 적용하기"
        HomeStatus.SETUP_REQUIRED -> "1회 설정 시작"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding),
        shape = RoundedCornerShape(CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = badgeColor
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )

            if (status == HomeStatus.READY) {
                Text(
                    text = "앱을 종료해도 설정은 유지됩니다. 소리 모드에서는 시스템 음량에 따라 촬영음이 들릴 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(2.dp))
            Button(
                onClick = onPrimaryAction,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandBlueLight)
            ) {
                Text(
                    text = primaryLabel,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            onSecondaryAction?.let { action ->
                OutlinedButton(
                    onClick = action,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("카메라 열어보기")
                }
            }
        }
    }
}

@Composable
private fun SetupProgressCard(uiState: MainUiState) {
    val wirelessComplete = uiState.isWirelessDebuggingEnabled
    val pairingComplete = uiState.hasCscPermission
    val applyComplete = uiState.isCscMuted

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding),
        shape = RoundedCornerShape(CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "1회 설정 진행",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "지금 필요한 단계만 따라가면 됩니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            SetupStepRow(
                number = 1,
                title = "무선 디버깅 켜기",
                subtitle = "1회 설정 시작을 누르면 필요한 설정 화면을 엽니다.",
                state = when {
                    wirelessComplete -> StepVisualState.COMPLETE
                    else -> StepVisualState.CURRENT
                }
            )
            SetupStepRow(
                number = 2,
                title = "6자리 코드 입력",
                subtitle = "상단 알림의 [코드 입력]에서 화면에 보이는 숫자 6자리를 입력합니다.",
                state = when {
                    pairingComplete -> StepVisualState.COMPLETE
                    wirelessComplete -> StepVisualState.CURRENT
                    else -> StepVisualState.PENDING
                }
            )
            SetupStepRow(
                number = 3,
                title = "카메라 무음 적용",
                subtitle = "연결에 성공하면 앱이 자동으로 적용하고 마무리합니다.",
                state = when {
                    applyComplete -> StepVisualState.COMPLETE
                    pairingComplete -> StepVisualState.CURRENT
                    else -> StepVisualState.PENDING
                }
            )
        }
    }
}

@Composable
private fun SetupStepRow(
    number: Int,
    title: String,
    subtitle: String,
    state: StepVisualState
) {
    val markerColor = when (state) {
        StepVisualState.COMPLETE -> StatusGreen
        StepVisualState.CURRENT -> BrandBlueLight
        StepVisualState.PENDING -> MaterialTheme.colorScheme.surfaceVariant
    }
    val titleColor = when (state) {
        StepVisualState.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Card(
            modifier = Modifier.size(30.dp),
            shape = CircleShape,
            colors = CardDefaults.cardColors(containerColor = markerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (state == StepVisualState.COMPLETE) "✓" else number.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (state == StepVisualState.PENDING) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        Color.White
                    }
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = titleColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = when (state) {
                    StepVisualState.COMPLETE -> "완료"
                    else -> subtitle
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (state == StepVisualState.COMPLETE) {
                    StatusGreen
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun RecoveryCard(
    wirelessDebuggingEnabled: Boolean,
    onReapply: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding),
        shape = RoundedCornerShape(CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "복구 안내",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (wirelessDebuggingEnabled) {
                    "무선 디버깅이 켜져 있습니다. 아래 버튼을 누르면 카메라 무음 설정을 바로 다시 적용합니다."
                } else {
                    "기기 연결은 남아 있습니다. 다시 적용할 때만 무선 디버깅을 잠시 켜면 됩니다."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
            OutlinedButton(
                onClick = onReapply,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("카메라 무음 다시 적용")
            }
        }
    }
}

@Composable
private fun UpdateCard(
    version: String,
    isPreparing: Boolean,
    onUpdate: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding),
        shape = RoundedCornerShape(CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "새 버전 v$version",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "변경 내용을 확인하고 안전하게 업데이트할 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onUpdate,
                enabled = !isPreparing,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (isPreparing) "업데이트 준비 중…" else "업데이트")
            }
        }
    }
}