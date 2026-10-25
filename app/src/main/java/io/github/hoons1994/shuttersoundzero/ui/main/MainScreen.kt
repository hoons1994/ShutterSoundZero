package io.github.hoons1994.shuttersoundzero.ui.main

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import io.github.hoons1994.shuttersoundzero.core.adb.LocalNetworkAccess
import io.github.hoons1994.shuttersoundzero.data.SetupIssue
import io.github.hoons1994.shuttersoundzero.data.PreferencesRepository
import io.github.hoons1994.shuttersoundzero.ui.components.CameraMuteFailureDialog
import io.github.hoons1994.shuttersoundzero.ui.notification.PairingNotificationHelper

private const val AccessLocalNetworkPermission = "android.permission.ACCESS_LOCAL_NETWORK"

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onItemClick: (NavKey) -> Unit = {},
    viewModel: MainScreenViewModel = viewModel(),
    pairingRecoveryTarget: Boolean? = null,
    onPairingRecoveryHandled: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val prefs = remember(context) { PreferencesRepository.getInstance(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    var showWifiRequiredDialog by remember { mutableStateOf(false) }

    val startPairing = {
        viewModel.startNotificationPairing(context, prefs.pendingPairingTarget ?: true)
    }

    var resumePairingAfterPermissionSettings by rememberSaveable { mutableStateOf(false) }
    var cameraMuteAfterPermissionSettings by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val permissionSettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val cameraMuteTarget = cameraMuteAfterPermissionSettings
        cameraMuteAfterPermissionSettings = null
        val resume = resumePairingAfterPermissionSettings
        resumePairingAfterPermissionSettings = false
        if (cameraMuteTarget != null) {
            // Recheck current permission and wireless state; never replace a restore with pairing.
            viewModel.toggleCscMute(cameraMuteTarget)
        } else if (resume) {
            if (LocalNetworkAccess.isGranted(context)) startPairing()
            else viewModel.reportLocalNetworkPermissionDenied()
        }
    }

    val localNetworkPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        if (LocalNetworkAccess.isGranted(context)) {
            startPairing()
        } else {
            viewModel.reportLocalNetworkPermissionDenied()
            Toast.makeText(
                context,
                "기기 연결을 위해 [앱 설정 → 권한 → 근처 기기]를 허용해 주세요. 기기에 따라 [로컬 네트워크]로 표시될 수 있습니다.",
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
                viewModel.resumeCameraMuteAfterWirelessSettings()
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
    val startSetup: (Boolean) -> Unit = { targetMuted ->
        if (!CscMuteManager.isSamsungDevice()) {
            Toast.makeText(
                context,
                "이 앱은 삼성 갤럭시 전용 앱입니다.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            prefs.pendingPairingTarget = targetMuted
            requestPairingNotification()
        }
    }

    val setupAction: () -> Unit = { startSetup(prefs.pendingPairingTarget ?: true) }

    LaunchedEffect(pairingRecoveryTarget) {
        if (pairingRecoveryTarget != null) {
            onPairingRecoveryHandled()
            startSetup(pairingRecoveryTarget)
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
            onRefreshCscState = viewModel::refreshState,
            onOpenCamera = openCamera,
            onOpenWirelessDebugging = {
                viewModel.handleSettingsLaunch(SetupSettingsNavigator.openPairingSetupScreen(context))
            },
            onOpenSoftwareInfo = {
                viewModel.handleSettingsLaunch(SetupSettingsNavigator.openSoftwareInfoSettings(context))
            },
            onOpenAppSettings = {
                cameraMuteAfterPermissionSettings = null
                viewModel.cancelCameraMuteAfterWirelessSettings()
                resumePairingAfterPermissionSettings = uiState.setupIssue == SetupIssue.LOCAL_NETWORK_PERMISSION
                try {
                    permissionSettingsLauncher.launch(SetupSettingsNavigator.appPermissionSettingsIntent(context))
                } catch (_: Exception) {
                    resumePairingAfterPermissionSettings = false
                    viewModel.handleSettingsLaunch(Result.failure(IllegalStateException()))
                }
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
                viewModel.handleSettingsLaunch(SetupSettingsNavigator.openWifiSettings(context))
            },
            secondaryLabel = "닫기",
            onSecondary = { showWifiRequiredDialog = false },
            onDismissRequest = { showWifiRequiredDialog = false }
        )
    }

    uiState.cameraMuteFailure?.let { failure ->
        val cameraMuteTarget = uiState.pendingCameraMuteTarget
        CameraMuteFailureDialog(
            failure = failure,
            onDismiss = viewModel::dismissCameraMuteFailure,
            onReconnect = { startSetup(cameraMuteTarget ?: true) },
            onOpenSettings = {
                if (failure == CameraMuteFailure.LOCAL_NETWORK_PERMISSION) {
                    resumePairingAfterPermissionSettings = false
                    viewModel.cancelCameraMuteAfterWirelessSettings()
                    cameraMuteAfterPermissionSettings = cameraMuteTarget
                    try {
                        permissionSettingsLauncher.launch(SetupSettingsNavigator.appPermissionSettingsIntent(context))
                    } catch (error: Exception) {
                        cameraMuteAfterPermissionSettings = null
                        cameraMuteTarget?.let(viewModel::reportCameraMutePermissionRequired)
                        viewModel.handleSettingsLaunch(Result.failure(error))
                    }
                } else {
                    viewModel.handleSettingsLaunch(SetupSettingsNavigator.openWirelessDebuggingOrDevOptions(context))
                }
            }
        )
    }

    if (uiState.showSwitchFailureHelp) {
        val cameraMuteTarget = uiState.pendingCameraMuteTarget
        AlertDialog(
            onDismissRequest = { viewModel.dismissSwitchFailureHelp() },
            shape = RoundedCornerShape(24.dp),
            title = { Text("무선 디버깅을 켜 주세요") },
            text = {
                Text(
                    "카메라 설정을 바꾸는 동안에만 무선 디버깅이 필요합니다.\n\n" +
                        (if (cameraMuteTarget != null) {
                            "무선 디버깅을 켠 뒤 앱으로 돌아오면 " +
                                (if (cameraMuteTarget) "다시 적용을" else "원래대로 복원을") +
                                " 계속합니다. 완료 후에는 앱이 다시 끄려고 시도합니다."
                        } else {
                            "무선 디버깅을 켠 뒤 앱으로 돌아와 [다시 적용하기]를 눌러 주세요. 완료 후에는 앱이 다시 끄려고 시도합니다."
                        }) + "\n\n" + stringResource(R.string.wireless_debugging_auto_blocker_guidance),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.prepareCameraMuteAfterWirelessSettings(cameraMuteTarget)
                        if (!viewModel.handleSettingsLaunch(SetupSettingsNavigator.openWirelessDebuggingOrDevOptions(context))) {
                            viewModel.cancelCameraMuteAfterWirelessSettings()
                        }
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
                        viewModel.handleSettingsLaunch(SetupSettingsNavigator.openWirelessDebuggingOrDevOptions(context))
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
