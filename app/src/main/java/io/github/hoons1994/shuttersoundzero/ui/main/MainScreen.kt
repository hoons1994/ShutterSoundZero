package io.github.hoons1994.shuttersoundzero.ui.main

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import io.github.hoons1994.shuttersoundzero.R
import io.github.hoons1994.shuttersoundzero.Settings
import io.github.hoons1994.shuttersoundzero.core.CscMuteManager
import io.github.hoons1994.shuttersoundzero.core.DeveloperOptionsManager
import io.github.hoons1994.shuttersoundzero.core.SetupSettingsNavigator
import io.github.hoons1994.shuttersoundzero.core.WifiConnectionStatus
import io.github.hoons1994.shuttersoundzero.core.adb.CameraMuteFailure
import io.github.hoons1994.shuttersoundzero.ui.components.CameraMuteFailureDialog
import io.github.hoons1994.shuttersoundzero.data.SetupIssue
import io.github.hoons1994.shuttersoundzero.theme.BrandBlueLight
import io.github.hoons1994.shuttersoundzero.theme.StatusAmber
import io.github.hoons1994.shuttersoundzero.theme.StatusGreen
import io.github.hoons1994.shuttersoundzero.theme.ShutterSoundZeroTheme
import io.github.hoons1994.shuttersoundzero.ui.notification.PairingNotificationHelper
import kotlin.math.roundToInt

private val CardRadius = 24.dp
private val ScreenPadding = 20.dp
private const val AccessLocalNetworkPermission = "android.permission.ACCESS_LOCAL_NETWORK"

internal enum class HomeStatus {
    APPLYING,
    READY,
    REAPPLY_REQUIRED,
    SETUP_REQUIRED
}

internal enum class StepVisualState {
    COMPLETE,
    CURRENT,
    ERROR,
    PENDING
}

internal fun resolveHomeStatus(uiState: MainUiState): HomeStatus = when {
    uiState.isCscChangeInProgress -> HomeStatus.APPLYING
    uiState.isCscMuted && uiState.hasCscPermission -> HomeStatus.READY
    uiState.hasCscPermission -> HomeStatus.REAPPLY_REQUIRED
    else -> HomeStatus.SETUP_REQUIRED
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onItemClick: (NavKey) -> Unit = {},
    viewModel: MainScreenViewModel = viewModel(),
    pairingRecoveryRequested: Boolean = false,
    onPairingRecoveryHandled: () -> Unit = {}
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
            viewModel.reportLocalNetworkPermissionDenied()
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
        if (!WifiConnectionStatus.isWifiConnected(context)) {
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

    DisposableEffect(lifecycleOwner, context, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshState()
            }
        }
        val volumeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                viewModel.refreshSystemVolume()
            }
        }
        // Opening Quick Settings does not necessarily pause/resume this Activity.
        // Observe the actual settings changed by a tile instead of waiting for ON_RESUME.
        val stateObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                viewModel.refreshState()
            }
        }
        val ringerReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                viewModel.refreshSystemVolume()
            }
        }
        context.contentResolver.registerContentObserver(android.provider.Settings.System.CONTENT_URI, true, volumeObserver)
        context.contentResolver.registerContentObserver(
            android.provider.Settings.System.getUriFor(CscMuteManager.CSC_KEY),
            false,
            stateObserver
        )
        context.contentResolver.registerContentObserver(
            DeveloperOptionsManager.wirelessDebuggingUri,
            false,
            stateObserver
        )
        context.contentResolver.registerContentObserver(
            android.provider.Settings.Global.getUriFor(android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED),
            false,
            stateObserver
        )
        ContextCompat.registerReceiver(
            context,
            ringerReceiver,
            IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION).apply {
                addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            context.contentResolver.unregisterContentObserver(volumeObserver)
            context.contentResolver.unregisterContentObserver(stateObserver)
            context.unregisterReceiver(ringerReceiver)
        }
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

    LaunchedEffect(pairingRecoveryRequested) {
        if (pairingRecoveryRequested) {
            onPairingRecoveryHandled()
            setupAction()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        HomeContent(
            uiState = uiState,
            onSettingsClick = { onItemClick(Settings) },
            onSetup = setupAction,
            onReapply = reapplyAction,
            onOpenCamera = openCamera,
            onOpenWirelessDebugging = { SetupSettingsNavigator.openPairingSetupScreen(context) },
            onOpenSoftwareInfo = { SetupSettingsNavigator.openSoftwareInfoSettings(context) },
            onOpenAppSettings = {
                context.startActivity(
                    Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.fromParts("package", context.packageName, null)
                    )
                )
            },
            onSystemVolumeChange = viewModel::setSystemVolume,
            onOpenSoundSettings = {
                try {
                    context.startActivity(Intent(android.provider.Settings.ACTION_SOUND_SETTINGS))
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.system_volume_settings_unavailable, Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.padding(innerPadding)
        )
    }

    if (showWifiRequiredDialog) {
        io.github.hoons1994.shuttersoundzero.ui.components.ModernPromptDialog(
            eyebrow = "연결 확인",
            title = "Wi-Fi 연결이 필요해요",
            message = "1회 설정을 진행하려면 Wi-Fi에 연결되어 있어야 합니다.\n\nWi-Fi에 연결한 뒤 다시 [1회 설정 시작]을 눌러 주세요.",
            primaryLabel = "Wi-Fi 설정 열기",
            onPrimary = {
                showWifiRequiredDialog = false
                SetupSettingsNavigator.openWifiSettings(context)
            },
            secondaryLabel = "닫기",
            onSecondary = { showWifiRequiredDialog = false },
            onDismissRequest = { showWifiRequiredDialog = false }
        )
    }

    uiState.cameraMuteFailure?.let { failure ->
        CameraMuteFailureDialog(
            failure = failure,
            onDismiss = viewModel::dismissCameraMuteFailure,
            onReconnect = setupAction,
            onOpenSettings = {
                if (failure == CameraMuteFailure.LOCAL_NETWORK_PERMISSION) {
                    context.startActivity(Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.fromParts("package", context.packageName, null)
                    ))
                } else {
                    SetupSettingsNavigator.openWirelessDebuggingOrDevOptions(context)
                }
            }
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
                        SetupSettingsNavigator.openWirelessDebuggingOrDevOptions(context)
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
                        SetupSettingsNavigator.openWirelessDebuggingOrDevOptions(context)
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

@Composable
internal fun HomeContent(
    uiState: MainUiState,
    onSettingsClick: () -> Unit = {},
    onSetup: () -> Unit = {},
    onReapply: () -> Unit = {},
    onOpenCamera: () -> Unit = {},
    onOpenSoftwareInfo: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
    onSystemVolumeChange: (Int) -> Int? = { null },
    modifier: Modifier = Modifier,
    onOpenSoundSettings: () -> Unit = {},
    onOpenWirelessDebugging: () -> Unit = {}
) {
    val homeStatus = resolveHomeStatus(uiState)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        AppHeader(onSettingsClick = onSettingsClick)
        Spacer(modifier = Modifier.height(10.dp))

        StatusHeroCard(
            status = homeStatus,
            setupIssue = uiState.setupIssue,
            isInProgress = uiState.isCscChangeInProgress,
            isWirelessDebuggingEnabled = uiState.isWirelessDebuggingEnabled,
            onOpenWirelessDebugging = onOpenWirelessDebugging,
            onPrimaryAction = when (homeStatus) {
                HomeStatus.APPLYING -> null
                HomeStatus.READY -> null
                HomeStatus.REAPPLY_REQUIRED -> onReapply
                HomeStatus.SETUP_REQUIRED -> onSetup
            },
            onCameraAction = onOpenCamera
        )

        if (homeStatus == HomeStatus.SETUP_REQUIRED) {
            Spacer(modifier = Modifier.height(20.dp))
            SetupProgressCard(uiState, onOpenSoftwareInfo, onOpenAppSettings)
        }

        Spacer(modifier = Modifier.height(20.dp))
        SystemVolumeCard(uiState.systemVolume, onSystemVolumeChange, onOpenSoundSettings)

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SystemVolumeCard(
    volume: SystemVolumeUiState,
    onVolumeChange: (Int) -> Int?,
    onOpenSoundSettings: () -> Unit
) {
    val current = volume.current
    val canAdjust = current != null && !volume.isFixed && volume.max > volume.min
    val sliderEnabled = canAdjust && !volume.isRingerMuted && volume.requested == null
    val colorScheme = MaterialTheme.colorScheme
    val sliderColors = SliderDefaults.colors(
        activeTrackColor = colorScheme.primary,
        inactiveTrackColor = colorScheme.primaryContainer,
        disabledActiveTrackColor = colorScheme.onSurface.copy(alpha = 0.28f),
        disabledInactiveTrackColor = colorScheme.surfaceContainerHigh
    )
    var selectedVolume by remember(current, volume.requested, volume.error) {
        mutableFloatStateOf((volume.requested ?: current ?: volume.min).toFloat())
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding),
        shape = RoundedCornerShape(CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val stackLevel = maxWidth / LocalDensity.current.fontScale < 220.dp
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = colorScheme.primaryContainer,
                            contentColor = colorScheme.onPrimaryContainer
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_system_volume),
                                contentDescription = null,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.system_volume_title),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colorScheme.onSurface
                        )
                        if (current != null && !stackLevel) {
                            SystemVolumeLevelBadge(selectedVolume.roundToInt(), volume.max)
                        }
                    }
                    if (current != null && stackLevel) {
                        SystemVolumeLevelBadge(
                            level = selectedVolume.roundToInt(),
                            max = volume.max,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }

            if (canAdjust) {
                Slider(
                    enabled = sliderEnabled,
                    value = selectedVolume,
                    onValueChange = { selectedVolume = it },
                    onValueChangeFinished = {
                        selectedVolume = (
                            onVolumeChange(selectedVolume.roundToInt()) ?: current
                        ).toFloat()
                    },
                    valueRange = volume.min.toFloat()..volume.max.toFloat(),
                    steps = (volume.max - volume.min - 1).coerceAtLeast(0),
                    colors = sliderColors,
                    thumb = {
                        Surface(
                            modifier = Modifier.width(10.dp).height(28.dp),
                            shape = RoundedCornerShape(50),
                            color = if (sliderEnabled) colorScheme.onPrimary else colorScheme.surface,
                            border = BorderStroke(
                                2.dp,
                                if (sliderEnabled) colorScheme.primary
                                else colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        ) {}
                    },
                    track = { sliderState ->
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            modifier = Modifier.height(28.dp),
                            enabled = sliderEnabled,
                            colors = sliderColors,
                            drawStopIndicator = null,
                            drawTick = { _, _ -> },
                            thumbTrackGapSize = 0.dp,
                            trackInsideCornerSize = 0.dp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "시스템 음량 조절" }
                )
            } else {
                Text(
                    text = if (volume.isFixed || (volume.max <= volume.min && current != null)) {
                        stringResource(R.string.system_volume_fixed)
                    } else {
                        volume.error ?: stringResource(R.string.system_volume_loading)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (canAdjust) {
                Text(
                    text = when {
                        volume.isRingerMuted -> stringResource(R.string.system_volume_ringer_muted)
                        volume.requested != null -> stringResource(R.string.system_volume_applying)
                        else -> volume.error ?: stringResource(R.string.system_volume_description)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 18.sp,
                    color = if (volume.error != null || volume.isRingerMuted) StatusAmber else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(
                onClick = onOpenSoundSettings,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.system_volume_open_settings))
            }
        }
    }
}

@Composable
private fun SystemVolumeLevelBadge(level: Int, max: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Text(
            text = "$level / $max",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum"
            ),
            maxLines = 1
        )
    }
}

@Preview(name = "시스템 음량 · 라이트", widthDp = 360)
@Preview(name = "시스템 음량 · 다크", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "시스템 음량 · 큰 글자", widthDp = 320, fontScale = 2f)
@Composable
private fun SystemVolumeCardPreview() {
    ShutterSoundZeroTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(vertical = 20.dp)) {
                SystemVolumeCard(
                    volume = SystemVolumeUiState(current = 7, max = 15),
                    onVolumeChange = { it },
                    onOpenSoundSettings = {}
                )
            }
        }
    }
}

@Composable
private fun AppHeader(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding)
            .padding(top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
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
        Spacer(modifier = Modifier.width(8.dp))
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
    setupIssue: SetupIssue?,
    isInProgress: Boolean,
    isWirelessDebuggingEnabled: Boolean,
    onOpenWirelessDebugging: () -> Unit,
    onPrimaryAction: (() -> Unit)?,
    onCameraAction: () -> Unit
) {
    val hasSetupIssue = status == HomeStatus.SETUP_REQUIRED && setupIssue != null
    val title = when (status) {
        HomeStatus.APPLYING -> stringResource(R.string.camera_settings_applying)
        HomeStatus.READY -> "카메라 무음 설정 완료"
        HomeStatus.REAPPLY_REQUIRED -> "카메라 무음 다시 적용 필요"
        HomeStatus.SETUP_REQUIRED -> if (hasSetupIssue) {
            "1회 설정을 다시 진행해 주세요"
        } else {
            "처음 한 번만 설정해 주세요"
        }
    }
    val subtitle = when (status) {
        HomeStatus.APPLYING -> "실제 적용 상태를 확인하고 있습니다. 완료될 때까지 기다려 주세요."
        HomeStatus.READY -> "진동·무음 모드에서 촬영음이 나지 않도록 설정되어 있습니다."
        HomeStatus.REAPPLY_REQUIRED -> "앱 권한은 유지되어 있습니다. 무선 디버깅을 켠 뒤 [다시 적용하기]를 눌러 주세요."
        HomeStatus.SETUP_REQUIRED -> if (hasSetupIssue) {
            "문제가 생긴 단계를 아래에 표시했습니다. 해당 단계부터 다시 진행하면 됩니다."
        } else {
            "4단계 안내에 따라 연결하면 이후에는 앱을 계속 열어둘 필요가 없습니다."
        }
    }
    val badgeText = when (status) {
        HomeStatus.APPLYING -> "처리 중"
        HomeStatus.READY -> "정상"
        HomeStatus.REAPPLY_REQUIRED -> "조치 필요"
        HomeStatus.SETUP_REQUIRED -> if (hasSetupIssue) "확인 필요" else "설정 필요"
    }
    val badgeColor = when (status) {
        HomeStatus.APPLYING -> BrandBlueLight
        HomeStatus.READY -> StatusGreen
        HomeStatus.REAPPLY_REQUIRED -> StatusAmber
        HomeStatus.SETUP_REQUIRED -> if (hasSetupIssue) StatusAmber else BrandBlueLight
    }
    val primaryLabel = when (status) {
        HomeStatus.APPLYING -> stringResource(R.string.camera_settings_applying)
        HomeStatus.READY -> null
        HomeStatus.REAPPLY_REQUIRED -> "다시 적용하기"
        HomeStatus.SETUP_REQUIRED -> if (hasSetupIssue) "1회 설정 다시 시작" else "1회 설정 시작"
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
                Spacer(modifier = Modifier.height(2.dp))
                OutlinedButton(
                    onClick = onCameraAction,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("카메라 열어보기")
                }
            } else {
                Spacer(modifier = Modifier.height(2.dp))
                Button(
                    onClick = { onPrimaryAction?.invoke() },
                    enabled = !isInProgress,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlueLight)
                ) {
                    Text(
                        text = primaryLabel.orEmpty(),
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            if (status != HomeStatus.SETUP_REQUIRED && status != HomeStatus.READY) {
                TextButton(
                    onClick = onOpenWirelessDebugging,
                    enabled = !isInProgress
                ) {
                    Text(
                        stringResource(
                            if (isWirelessDebuggingEnabled) R.string.main_open_wireless_debugging_settings
                            else R.string.main_reenable_wireless_debugging
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupProgressCard(
    uiState: MainUiState,
    onOpenSoftwareInfo: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    val context = LocalContext.current
    val issue = uiState.setupIssue
    val developerOptionsComplete = uiState.isDeveloperOptionsEnabled
    val wirelessComplete = developerOptionsComplete &&
        (uiState.isWirelessDebuggingEnabled || (issue != null && issue != SetupIssue.LOCAL_NETWORK_PERMISSION))
    val pairingComplete = uiState.hasCscPermission || issue == SetupIssue.CAMERA_APPLY
    val applyComplete = uiState.isCscMuted

    val step1State = if (developerOptionsComplete) StepVisualState.COMPLETE else StepVisualState.CURRENT
    val step2State = when {
        wirelessComplete -> StepVisualState.COMPLETE
        developerOptionsComplete -> StepVisualState.CURRENT
        else -> StepVisualState.PENDING
    }
    val step3State = when {
        !developerOptionsComplete -> StepVisualState.PENDING
        issue != null && issue != SetupIssue.CAMERA_APPLY && issue != SetupIssue.LOCAL_NETWORK_PERMISSION -> StepVisualState.ERROR
        pairingComplete -> StepVisualState.COMPLETE
        wirelessComplete -> StepVisualState.CURRENT
        else -> StepVisualState.PENDING
    }
    val step4State = when {
        !developerOptionsComplete -> StepVisualState.PENDING
        issue == SetupIssue.CAMERA_APPLY -> StepVisualState.ERROR
        applyComplete -> StepVisualState.COMPLETE
        pairingComplete -> StepVisualState.CURRENT
        else -> StepVisualState.PENDING
    }

    val pairingErrorText = when (issue) {
        SetupIssue.PAIRING_DISCOVERY ->
            "페어링 연결 화면을 찾지 못했습니다. Wi-Fi와 무선 디버깅을 확인하고 [페어링 코드로 기기 페어링] 화면을 다시 열어 둔 채 시도해 주세요."
        SetupIssue.PAIRING_CODE ->
            "숫자 6자리를 정확히 입력해 주세요. 연결 화면의 코드가 바뀌었다면 새 코드를 사용해 주세요."
        SetupIssue.PAIRING_CONNECTION ->
            "기기에 연결하지 못했습니다. 페어링 창이 닫혔거나 연결 정보가 바뀌었을 수 있습니다. 창을 다시 열고 새 6자리 코드로 시도해 주세요."
        SetupIssue.PAIRING_TIMEOUT ->
            "기기 응답 시간이 초과되었습니다. Wi-Fi와 무선 디버깅을 확인한 뒤 페어링 창을 다시 열고 새 코드로 시도해 주세요."
        else -> null
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
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "1회 설정 진행",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (issue == null) {
                    "지금 필요한 단계만 따라가면 됩니다."
                } else {
                    "문제가 생긴 단계부터 다시 진행해 주세요."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (issue == SetupIssue.LOCAL_NETWORK_PERMISSION) {
                Text(
                    text = "Android 17에서 기기를 찾으려면 로컬 네트워크 권한이 필요합니다. 앱 설정에서 권한을 허용한 뒤 1회 설정을 다시 시작해 주세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    lineHeight = 18.sp
                )
                OutlinedButton(onClick = onOpenAppSettings, shape = RoundedCornerShape(12.dp)) {
                    Text("앱 권한 설정 열기")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            SetupStepRow(
                number = 1,
                title = "개발자 옵션 켜기",
                subtitle = "휴대전화 정보 → 소프트웨어 정보 → 빌드번호를 7번 누릅니다. 화면 잠금 인증이 필요할 수 있습니다.",
                state = step1State,
                actionLabel = if (developerOptionsComplete) null else "휴대전화 정보 열기",
                onAction = onOpenSoftwareInfo
            )
            SetupStepRow(
                number = 2,
                title = "무선 디버깅 켜기",
                subtitle = "[1회 설정 시작]을 누르면 개발자 옵션의 무선 디버깅 화면을 엽니다.",
                state = step2State
            )
            SetupStepRow(
                number = 3,
                title = "6자리 코드 입력",
                subtitle = "상단 알림의 [코드 입력]에서 화면에 보이는 숫자 6자리를 입력합니다.",
                state = step3State,
                errorText = pairingErrorText
            )
            if (wirelessComplete && !pairingComplete && issue != SetupIssue.LOCAL_NETWORK_PERMISSION) {
                NotificationPopupStyleHint(
                    onOpenSettings = {
                        PairingNotificationHelper.openNotificationSettings(context)
                    }
                )
            }
            SetupStepRow(
                number = 4,
                title = "카메라 무음 적용",
                subtitle = "연결에 성공하면 앱이 자동으로 적용하고 마무리합니다.",
                state = step4State,
                errorText = if (issue == SetupIssue.CAMERA_APPLY) {
                    "기기 연결은 됐지만 카메라 설정을 적용하지 못했습니다. 무선 디버깅을 켠 상태에서 다시 시도해 주세요."
                } else {
                    null
                }
            )
        }
    }
}

@Composable
private fun NotificationPopupStyleHint(
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 42.dp, top = 2.dp, bottom = 6.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "간략한 팝업을 사용 중인가요?",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "ShutterSoundZero만 [자세한 팝업]으로 바꾸면 6자리 [코드 입력] 버튼을 바로 사용할 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("알림 팝업 설정 열기")
            }
        }
    }
}

@Composable
private fun SetupStepRow(
    number: Int,
    title: String,
    subtitle: String,
    state: StepVisualState,
    errorText: String? = null,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    val markerColor = when (state) {
        StepVisualState.COMPLETE -> StatusGreen
        StepVisualState.CURRENT -> BrandBlueLight
        StepVisualState.ERROR -> MaterialTheme.colorScheme.error
        StepVisualState.PENDING -> MaterialTheme.colorScheme.surfaceVariant
    }
    val titleColor = when (state) {
        StepVisualState.ERROR -> MaterialTheme.colorScheme.error
        StepVisualState.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    val detailText = when (state) {
        StepVisualState.COMPLETE -> "완료"
        StepVisualState.ERROR -> errorText ?: "이 단계를 다시 확인해 주세요."
        else -> subtitle
    }
    val detailColor = when (state) {
        StepVisualState.COMPLETE -> StatusGreen
        StepVisualState.ERROR -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
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
                    text = when (state) {
                        StepVisualState.COMPLETE -> "✓"
                        StepVisualState.ERROR -> "!"
                        else -> number.toString()
                    },
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
                text = detailText,
                style = MaterialTheme.typography.bodySmall,
                color = detailColor,
                lineHeight = 18.sp
            )
            if (actionLabel != null && state == StepVisualState.CURRENT) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}
