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
import androidx.compose.ui.platform.LocalResources
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
import io.github.hoons1994.shuttersoundzero.ui.components.DeveloperDonationPromptHost
import io.github.hoons1994.shuttersoundzero.ui.notification.PairingNotificationHelper
import io.github.hoons1994.shuttersoundzero.service.PairingForegroundService

private const val AccessLocalNetworkPermission = "android.permission.ACCESS_LOCAL_NETWORK"

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onItemClick: (NavKey) -> Unit = {},
    viewModel: MainScreenViewModel = viewModel(),
    pairingRecoveryTarget: Boolean? = null,
    pairingRecoveryResetIdentity: Boolean = false,
    onPairingRecoveryHandled: () -> Unit = {},
    updateNotice: @Composable () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val prefs = remember(context) { PreferencesRepository.getInstance(context) }
    val livePrompt by PairingForegroundService.codePrompt.collectAsStateWithLifecycle()
    val codePrompt = livePrompt?.takeIf {
        it.revision == prefs.pairingSetupRevision && (it.submitting ||
            (uiState.setupIssue != SetupIssue.LOCAL_NETWORK_PERMISSION && uiState.setupIssue != SetupIssue.PAIRING_IDENTITY))
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    var wifiDialogRevision by rememberSaveable { mutableStateOf<Long?>(null) }
    var permissionRequestRevision by rememberSaveable { mutableStateOf<Long?>(null) }

    val startPairing = {
        prefs.pendingPairingSetup()?.let { viewModel.startNotificationPairing(context, it.targetMuted) }
    }

    val permissionSettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            viewModel.resumeAfterPermissionSettings(context)
        }
    }

    val localNetworkPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        val revision = permissionRequestRevision
        permissionRequestRevision = null
        if (revision != prefs.pairingSetupRevision) return@rememberLauncherForActivityResult
        if (LocalNetworkAccess.isGranted(context)) {
            startPairing()
        } else {
            viewModel.reportLocalNetworkPermissionDenied()
            Toast.makeText(
                context,
                resources.getString(R.string.setup_network_permission),
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
            if (permissionRequestRevision == null) {
                permissionRequestRevision = prefs.pairingSetupRevision
                localNetworkPermissionLauncher.launch(AccessLocalNetworkPermission)
            }
        } else {
            startPairing()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val revision = permissionRequestRevision
        permissionRequestRevision = null
        if (revision != prefs.pairingSetupRevision) return@rememberLauncherForActivityResult
        if (isGranted) {
            requestLocalNetworkAccess()
        } else {
            Toast.makeText(
                context,
                resources.getString(R.string.setup_notification_permission),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val requestPairingNotification: () -> Unit = {
        if (!WifiConnectionStatus.isWifiConnected(context)) {
            wifiDialogRevision = prefs.pairingSetupRevision
        } else if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (permissionRequestRevision == null) {
                permissionRequestRevision = prefs.pairingSetupRevision
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else if (!PairingNotificationHelper.areNotificationsEnabled(context)) {
            Toast.makeText(
                context,
                resources.getString(R.string.setup_notifications_off),
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
                viewModel.resumeAfterPermissionSettings(context)
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
            snackbarHostState.showSnackbar(resources.getString(it))
            viewModel.dismissMessages()
        }
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(resources.getString(it))
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
            Toast.makeText(context, resources.getString(R.string.camera_launch_failed), Toast.LENGTH_SHORT).show()
        }
    }

    val reapplyAction: () -> Unit = {
        viewModel.toggleCscMute(true)
    }
    val startSetup: (Boolean, Boolean) -> Unit = { targetMuted, resetIdentity ->
        viewModel.cancelAfterPermissionSettings()
        if (!CscMuteManager.isSamsungDevice()) {
            Toast.makeText(
                context,
                resources.getString(R.string.galaxy_only),
                Toast.LENGTH_LONG
            ).show()
        } else {
            prefs.beginPairingSetup(targetMuted, resetIdentity)
            viewModel.refreshState()
            requestPairingNotification()
        }
    }

    val setupAction: () -> Unit = {
        if (codePrompt != null) {
            if (!codePrompt.submitting && codePrompt == PairingForegroundService.codePrompt.value &&
                codePrompt.revision == prefs.pairingSetupRevision) {
                if (PairingNotificationHelper.areNotificationsEnabled(context)) {
                    runCatching { PairingForegroundService.recoverNotification(context, codePrompt) }
                        .onFailure { viewModel.reportNotificationRecoveryFailure() }
                        .onSuccess {
                            viewModel.handleSettingsLaunch(SetupSettingsNavigator.openWirelessDebuggingOrDevOptions(context))
                        }
                } else {
                    Toast.makeText(context, R.string.setup_notifications_off, Toast.LENGTH_LONG).show()
                    viewModel.handleSettingsLaunch(runCatching { PairingNotificationHelper.openNotificationSettings(context) })
                }
            }
        } else if (prefs.pendingPairingSetup() == null) startSetup(true, false)
        else {
            viewModel.cancelAfterPermissionSettings()
            requestPairingNotification()
        }
    }

    LaunchedEffect(pairingRecoveryTarget) {
        if (pairingRecoveryTarget != null) {
            val resetIdentity = pairingRecoveryResetIdentity
            onPairingRecoveryHandled()
            startSetup(pairingRecoveryTarget, resetIdentity)
        }
    }

    DeveloperDonationPromptHost(
        prefs = prefs,
        enabled = !uiState.isCscChangeInProgress && uiState.infoMessage == null && uiState.errorMessage == null &&
            !uiState.showWirelessDebuggingCleanupHelp && !uiState.showSwitchFailureHelp &&
            uiState.cameraMuteFailure == null && wifiDialogRevision == null
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        HomeContent(
            updateNotice = updateNotice,
            uiState = uiState,
            onSettingsClick = { onItemClick(Settings) },
            onSetup = setupAction,
            codePrompt = codePrompt,
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
                viewModel.prepareAfterPermissionSettings(
                    resumePairing = uiState.setupIssue == SetupIssue.LOCAL_NETWORK_PERMISSION
                )
                try {
                    permissionSettingsLauncher.launch(SetupSettingsNavigator.appPermissionSettingsIntent(context))
                } catch (_: Exception) {
                    viewModel.cancelAfterPermissionSettings()
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

    wifiDialogRevision?.let { revision ->
        val cancelSetup = {
            wifiDialogRevision = null
            prefs.finishPairingSetup(revision)
            viewModel.refreshState()
        }
        io.github.hoons1994.shuttersoundzero.ui.components.ModernPromptDialog(
            eyebrow = stringResource(R.string.connection_check),
            title = stringResource(R.string.wifi_needed),
            message = stringResource(R.string.wifi_setup_message),
            primaryLabel = stringResource(R.string.wifi_open),
            onPrimary = {
                wifiDialogRevision = null
                viewModel.handleSettingsLaunch(SetupSettingsNavigator.openWifiSettings(context))
            },
            secondaryLabel = stringResource(R.string.camera_routine_close),
            onSecondary = cancelSetup,
            onDismissRequest = cancelSetup
        )
    }

    uiState.cameraMuteFailure?.let { failure ->
        val cameraMuteTarget = uiState.pendingCameraMuteTarget
        CameraMuteFailureDialog(
            failure = failure,
            onDismiss = viewModel::dismissCameraMuteFailure,
            onReconnect = { startSetup(cameraMuteTarget ?: true, false) },
            onOpenSettings = {
                if (failure == CameraMuteFailure.LOCAL_NETWORK_PERMISSION) {
                    viewModel.prepareAfterPermissionSettings(cameraTarget = cameraMuteTarget)
                    try {
                        permissionSettingsLauncher.launch(SetupSettingsNavigator.appPermissionSettingsIntent(context))
                    } catch (error: Exception) {
                        viewModel.cancelAfterPermissionSettings()
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
            title = { Text(stringResource(R.string.wireless_enable_title)) },
            text = {
                Text(
                    stringResource(R.string.wireless_during_change) +
                        (if (cameraMuteTarget != null) {
                            stringResource(if (cameraMuteTarget) R.string.wireless_resume_apply else R.string.wireless_resume_restore)
                        } else {
                            stringResource(R.string.wireless_retry_from_home)
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
                    Text(stringResource(R.string.wireless_enable))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissSwitchFailureHelp() }) {
                    Text(stringResource(R.string.action_later))
                }
            }
        )
    }

    if (uiState.showWirelessDebuggingCleanupHelp) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissWirelessDebuggingCleanupHelp() },
            shape = RoundedCornerShape(24.dp),
            title = { Text(stringResource(R.string.wireless_disable_title)) },
            text = {
                Text(
                    stringResource(R.string.wireless_cleanup_failed)
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
                    Text(stringResource(R.string.action_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissWirelessDebuggingCleanupHelp() }) {
                    Text(stringResource(R.string.action_later))
                }
            }
        )
    }
}
