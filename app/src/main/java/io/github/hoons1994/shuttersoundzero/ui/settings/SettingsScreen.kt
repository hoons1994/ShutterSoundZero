package io.github.hoons1994.shuttersoundzero.ui.settings

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.ViewModelProvider
import io.github.hoons1994.shuttersoundzero.R
import io.github.hoons1994.shuttersoundzero.core.CscMuteManager
import io.github.hoons1994.shuttersoundzero.core.DeveloperOptionsManager
import io.github.hoons1994.shuttersoundzero.core.SetupSettingsNavigator
import io.github.hoons1994.shuttersoundzero.core.adb.LocalNetworkAccess
import io.github.hoons1994.shuttersoundzero.core.adb.CameraMuteFailure
import io.github.hoons1994.shuttersoundzero.ui.components.CameraMuteFailureDialog
import io.github.hoons1994.shuttersoundzero.ui.components.DeveloperDonationPromptHost
import io.github.hoons1994.shuttersoundzero.data.PreferencesRepository
import io.github.hoons1994.shuttersoundzero.security.AppLockAuthenticator
import io.github.hoons1994.shuttersoundzero.security.AppLockSession
import io.github.hoons1994.shuttersoundzero.ui.notification.PairingNotificationHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPairingRecoveryRequested: (Boolean) -> Unit = { onBackClick() },
    onIdentityRecoveryRequested: (Boolean) -> Unit = onPairingRecoveryRequested
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settingsLaunchFailureMessage = stringResource(R.string.settings_launch_failed)
    fun handleSettingsLaunch(result: Result<Unit>): Boolean {
        if (result.isFailure) {
            Toast.makeText(context, settingsLaunchFailureMessage, Toast.LENGTH_LONG).show()
        }
        return result.isSuccess
    }
    val prefs = remember { PreferencesRepository.getInstance(context) }
    val versionName = remember(context) { currentVersionName(context) }
    val activityViewModelStoreOwner = remember(context) {
        context.findActivity() as? ViewModelStoreOwner
    }
    val viewModelStoreOwner = activityViewModelStoreOwner ?:
        LocalViewModelStoreOwner.current ?: error("SettingsScreen requires a ViewModelStoreOwner")
    val cameraSettingsViewModel = remember(viewModelStoreOwner) {
        ViewModelProvider(viewModelStoreOwner).get(CameraSettingsOperationViewModel::class.java)
    }
    val operationState by cameraSettingsViewModel.uiState.collectAsState()
    val permissionRecovery by cameraSettingsViewModel.permissionRecovery.collectAsState()

    var isSoftwareUpdateCheck by remember {
        mutableStateOf(prefs.isSoftwareUpdateCheckEnabled)
    }
    var isAppLockEnabled by remember { mutableStateOf(prefs.isAppLockEnabled) }
    var isLockSetupInProgress by remember { mutableStateOf(false) }
    var lockAuthenticationRequest by remember {
        mutableStateOf<AppLockAuthenticator.AuthenticationRequest?>(null)
    }
    DisposableEffect(Unit) {
        onDispose { lockAuthenticationRequest?.cancel() }
    }
    var lockErrorMessage by remember { mutableStateOf<String?>(null) }

    var showDeveloperOptionsConfirm by remember { mutableStateOf(false) }
    var showDeveloperOptionsFallback by remember { mutableStateOf(false) }
    var developerOptionsResultMessage by remember { mutableStateOf<String?>(null) }
    var showLicenseDialog by remember { mutableStateOf(false) }
    var showCameraRoutine by rememberSaveable { mutableStateOf(false) }
    var showLanguagePicker by rememberSaveable { mutableStateOf(false) }

    var showRestoreConfirm by remember { mutableStateOf(false) }
    var showReapplyWirelessDebuggingHelp by remember { mutableStateOf(false) }
    var showRestoreWirelessDebuggingHelp by remember { mutableStateOf(false) }
    val cameraState = rememberCameraSettingsState()
    var restoreResultMessage by rememberSaveable { mutableStateOf<Int?>(null) }
    var cameraMuteFailure by rememberSaveable { mutableStateOf<CameraMuteFailure?>(null) }
    var failedCameraMuteTarget by rememberSaveable { mutableStateOf(true) }
    var deniedLocalNetworkAction by rememberSaveable { mutableStateOf<CameraAction?>(null) }
    val recoveryRestoreConfirm = permissionRecovery == SettingsPermissionRecovery.CONFIRM_RESTORE

    LaunchedEffect(cameraState.wirelessDebuggingEnabled) {
        if (cameraState.wirelessDebuggingEnabled == true) {
            showReapplyWirelessDebuggingHelp = false
            showRestoreWirelessDebuggingHelp = false
        }
    }

    LaunchedEffect(operationState.completionId) {
        if (operationState.completionId > 0 && operationState.resultMessage != null) {
            showRestoreConfirm = false
            if (operationState.appliedMutedState != null || operationState.failure != null) {
                cameraState.isCscMuted = CscMuteManager.readCscMutedState(context)
            }
            cameraMuteFailure = operationState.failure
            failedCameraMuteTarget = operationState.requestedMutedState
            restoreResultMessage = if (cameraMuteFailure == null) operationState.resultMessage else null
            cameraSettingsViewModel.consumeCompletion()
        }
    }

    val runReapply: () -> Unit = {
        if (!operationState.isReapplyInProgress) {
            if (!CscMuteManager.hasWritePermission(context)) {
                restoreResultMessage = R.string.require_first_setup
            } else if (cameraSettingsViewModel.isWirelessDebuggingEnabled(context) == false) {
                showReapplyWirelessDebuggingHelp = true
            } else if (!cameraSettingsViewModel.isLocalNetworkGranted(context)) {
                deniedLocalNetworkAction = CameraAction.REAPPLY
            } else {
                cameraSettingsViewModel.setCameraMute(true)
            }
        }
    }

    val resumeCameraAction: (CameraAction) -> Unit = { action ->
        cameraState.refresh()
        if (!CscMuteManager.hasWritePermission(context)) {
            restoreResultMessage = R.string.require_first_setup
        } else if (cameraSettingsViewModel.isWirelessDebuggingEnabled(context) == false) {
            if (action == CameraAction.REAPPLY) showReapplyWirelessDebuggingHelp = true
            else showRestoreWirelessDebuggingHelp = true
        } else if (!cameraSettingsViewModel.isLocalNetworkGranted(context)) {
            deniedLocalNetworkAction = action
        } else when (action) {
            CameraAction.REAPPLY -> runReapply()
            CameraAction.RESTORE -> cameraSettingsViewModel.requireRestoreConfirmation()
        }
    }

    LaunchedEffect(permissionRecovery) {
        cameraSettingsViewModel.takeReadyPermissionAction()?.let(resumeCameraAction)
    }

    val permissionSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            cameraSettingsViewModel.resumePermissionRecovery()
        }
    }

    val localNetworkPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            cameraSettingsViewModel.resumePermissionRecovery()
        }
    }

    val requestLocalNetworkPermission: (CameraAction) -> Unit = { action ->
        cameraSettingsViewModel.preparePermissionRecovery(action)
        try {
            localNetworkPermissionLauncher.launch(LocalNetworkAccess.PERMISSION)
        } catch (_: Exception) {
            cameraSettingsViewModel.cancelPermissionRecovery()
            Toast.makeText(context, settingsLaunchFailureMessage, Toast.LENGTH_LONG).show()
        }
    }

    DeveloperDonationPromptHost(
        prefs = prefs,
        enabled = !operationState.isReapplyInProgress && !operationState.isRestoreInProgress &&
            operationState.resultMessage == null && restoreResultMessage == null && cameraMuteFailure == null &&
            permissionRecovery == null && deniedLocalNetworkAction == null &&
            !showRestoreConfirm && !recoveryRestoreConfirm && !showReapplyWirelessDebuggingHelp && !showRestoreWirelessDebuggingHelp &&
            !showDeveloperOptionsConfirm && !showDeveloperOptionsFallback && developerOptionsResultMessage == null &&
            !showLicenseDialog && !showCameraRoutine && !showLanguagePicker && !isLockSetupInProgress && lockErrorMessage == null
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.action_settings),
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
                            contentDescription = stringResource(R.string.action_back),
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

            GroupLabel(stringResource(R.string.settings_camera))
            SettingsCard {
                if (cameraState.isCscMuted == null) {
                    ClickableRow(
                        title = stringResource(R.string.csc_state_retry),
                        subtitle = stringResource(R.string.csc_state_unknown_guidance),
                        onClick = { cameraState.isCscMuted = CscMuteManager.readCscMutedState(context) }
                    )
                } else if (cameraState.isCscMuted == true) {
                    ClickableRow(
                        title = stringResource(R.string.settings_restore),
                        subtitle = stringResource(R.string.settings_force_off),
                        onClick = {
                            cameraSettingsViewModel.cancelPermissionRecovery()
                            cameraState.refresh()
                            if (!CscMuteManager.hasWritePermission(context)) {
                                restoreResultMessage = R.string.require_first_setup
                            } else if (cameraSettingsViewModel.isWirelessDebuggingEnabled(context) == false) {
                                showRestoreWirelessDebuggingHelp = true
                            } else if (!cameraSettingsViewModel.isLocalNetworkGranted(context)) {
                                requestLocalNetworkPermission(CameraAction.RESTORE)
                            } else {
                                showRestoreConfirm = true
                            }
                        }
                    )
                } else {
                    ClickableRow(
                        title = if (operationState.isReapplyInProgress) stringResource(R.string.settings_reapplying) else stringResource(R.string.settings_reapply),
                        subtitle = stringResource(R.string.settings_reapply_hint),
                        onClick = {
                            cameraSettingsViewModel.cancelPermissionRecovery()
                            cameraState.refresh()
                            if (!CscMuteManager.hasWritePermission(context)) {
                                restoreResultMessage = R.string.require_first_setup
                            } else if (cameraSettingsViewModel.isWirelessDebuggingEnabled(context) == false) {
                                showReapplyWirelessDebuggingHelp = true
                            } else if (!operationState.isReapplyInProgress) {
                                if (!cameraSettingsViewModel.isLocalNetworkGranted(context)) {
                                    requestLocalNetworkPermission(CameraAction.REAPPLY)
                                } else {
                                    runReapply()
                                }
                            }
                        }
                    )
                }
                RowDivider()
                InfoRow(
                    title = stringResource(R.string.settings_usage),
                    subtitle = stringResource(R.string.settings_wireless_hint)
                )
                if (CscMuteManager.isSamsungDevice()) {
                    RowDivider()
                    ClickableRow(
                        title = stringResource(R.string.camera_routine_title),
                        subtitle = stringResource(R.string.camera_routine_summary),
                        onClick = { showCameraRoutine = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            GroupLabel(stringResource(R.string.app_appearance_title))
            SettingsCard {
                ThemeSettingsSection(prefs)
                RowDivider()
                LanguageSettingsRow(showLanguagePicker) { showLanguagePicker = it }
            }

            Spacer(modifier = Modifier.height(24.dp))

            GroupLabel(stringResource(R.string.settings_security))
            SettingsCard {
                SwitchRow(
                    title = stringResource(R.string.settings_lock),
                    subtitle = stringResource(R.string.settings_lock_summary),
                    checked = isAppLockEnabled,
                    onCheckedChange = { enabled ->
                        if (isLockSetupInProgress) return@SwitchRow

                        val activity = context.findActivity()
                        if (activity == null || !AppLockAuthenticator.canAuthenticate(context)) {
                            lockErrorMessage =
                                resources.getString(R.string.lock_setup_required)
                            return@SwitchRow
                        }

                        isLockSetupInProgress = true
                        lockAuthenticationRequest = AppLockAuthenticator.authenticate(
                            activity = activity,
                            title = if (enabled) resources.getString(R.string.lock_enable_title) else resources.getString(R.string.lock_disable_title),
                            subtitle = if (enabled) {
                                resources.getString(R.string.lock_enable_reason)
                            } else {
                                resources.getString(R.string.lock_disable_reason)
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
                                    resources.getString(R.string.lock_not_available)
                            }
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            GroupLabel(stringResource(R.string.settings_status_updates))
            SettingsCard {
                SwitchRow(
                    title = stringResource(R.string.settings_check_update),
                    subtitle = stringResource(R.string.settings_notify_if_needed),
                    checked = isSoftwareUpdateCheck,
                    onCheckedChange = { enabled ->
                        isSoftwareUpdateCheck = enabled
                        prefs.isSoftwareUpdateCheckEnabled = enabled
                        if (enabled) {
                            prefs.lastSoftwareFingerprint = Build.FINGERPRINT
                        }
                    }
                )
                RowDivider()
                AppUpdateSection(currentVersion = versionName)
            }

            Spacer(modifier = Modifier.height(24.dp))

            GroupLabel(stringResource(R.string.settings_device))
            SettingsCard {
                NotificationPopupSettingsRow(
                    onClick = { PairingNotificationHelper.openNotificationSettings(context) }
                )
                RowDivider()
                ClickableRow(
                    title = stringResource(R.string.settings_disable_developer),
                    subtitle = stringResource(R.string.settings_disable_developer_hint),
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

            IdentityRecoveryRow(
                enabled = !operationState.isReapplyInProgress && !operationState.isRestoreInProgress,
                onConfirmed = { onIdentityRecoveryRequested(prefs.pendingPairingTarget ?: prefs.shouldMuteOnBoot) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            SettingsHelpSection()

            Spacer(modifier = Modifier.height(24.dp))

            GroupLabel(stringResource(R.string.settings_about))
            SettingsCard {
                InfoRow(
                    title = stringResource(R.string.settings_version),
                    subtitle = versionName
                )
                RowDivider()
                InfoRow(
                    title = stringResource(R.string.settings_developer),
                    subtitle = "charmingcolor"
                )
                RowDivider()
                ClickableRow(
                    title = stringResource(R.string.settings_licenses),
                    subtitle = "GNU General Public License v3.0 (GPL-3.0)",
                    onClick = { showLicenseDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        if (showReapplyWirelessDebuggingHelp) {
            AlertDialog(
                onDismissRequest = { showReapplyWirelessDebuggingHelp = false },
                title = { Text(if (cameraState.wirelessDebuggingEnabled == null) {
                    stringResource(R.string.wireless_debugging_state_unknown_title)
                } else stringResource(R.string.wireless_enable_title)) },
                text = {
                    Text(
                        (if (cameraState.wirelessDebuggingEnabled == null) {
                            stringResource(R.string.wireless_debugging_state_unknown_guidance)
                        } else stringResource(R.string.wireless_reapply_help)) +
                            "\n\n" + stringResource(R.string.wireless_debugging_auto_blocker_guidance),
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (handleSettingsLaunch(SetupSettingsNavigator.openWirelessDebuggingOrDevOptions(context))) {
                                showReapplyWirelessDebuggingHelp = false
                            }
                        }
                    ) {
                        Text(stringResource(R.string.action_open_settings))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showReapplyWirelessDebuggingHelp = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        if (showRestoreWirelessDebuggingHelp) {
            AlertDialog(
                onDismissRequest = { showRestoreWirelessDebuggingHelp = false },
                title = { Text(if (cameraState.wirelessDebuggingEnabled == null) {
                    stringResource(R.string.wireless_debugging_state_unknown_title)
                } else stringResource(R.string.wireless_enable_title)) },
                text = {
                    Text(
                        (if (cameraState.wirelessDebuggingEnabled == null) {
                            stringResource(R.string.wireless_debugging_state_unknown_guidance)
                        } else stringResource(R.string.wireless_restore_help)) +
                            "\n\n" + stringResource(R.string.wireless_debugging_auto_blocker_guidance),
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (handleSettingsLaunch(SetupSettingsNavigator.openWirelessDebuggingOrDevOptions(context))) {
                                showRestoreWirelessDebuggingHelp = false
                            }
                        }
                    ) {
                        Text(stringResource(R.string.action_open_settings))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRestoreWirelessDebuggingHelp = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        if (showRestoreConfirm || recoveryRestoreConfirm) {
            AlertDialog(
                onDismissRequest = {
                    if (!operationState.isRestoreInProgress) {
                        showRestoreConfirm = false
                        cameraSettingsViewModel.cancelPermissionRecovery()
                    }
                },
                title = { Text(stringResource(R.string.restore_confirm)) },
                text = {
                    Text(
                        stringResource(R.string.restore_confirm_body)
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = !operationState.isRestoreInProgress,
                        onClick = {
                            if (operationState.isRestoreInProgress) return@TextButton
                            if (!cameraSettingsViewModel.isLocalNetworkGranted(context)) {
                                showRestoreConfirm = false
                                requestLocalNetworkPermission(CameraAction.RESTORE)
                                return@TextButton
                            }
                            showRestoreConfirm = true
                            cameraSettingsViewModel.cancelPermissionRecovery()
                            cameraSettingsViewModel.setCameraMute(false)
                        }
                    ) {
                        Text(if (operationState.isRestoreInProgress) stringResource(R.string.restore_busy) else stringResource(R.string.restore_action))
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !operationState.isRestoreInProgress,
                        onClick = {
                            showRestoreConfirm = false
                            cameraSettingsViewModel.cancelPermissionRecovery()
                        }
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        cameraMuteFailure?.let { failure ->
            CameraMuteFailureDialog(
                failure = failure,
                onDismiss = { cameraMuteFailure = null },
                onReconnect = { onPairingRecoveryRequested(failedCameraMuteTarget) },
                onOpenSettings = {
                    if (failure == CameraMuteFailure.LOCAL_NETWORK_PERMISSION) {
                        handleSettingsLaunch(SetupSettingsNavigator.openAppPermissionSettings(context))
                    } else {
                        handleSettingsLaunch(SetupSettingsNavigator.openWirelessDebuggingOrDevOptions(context))
                    }
                }
            )
        }

        deniedLocalNetworkAction?.let { action ->
            CameraMuteFailureDialog(
                failure = CameraMuteFailure.LOCAL_NETWORK_PERMISSION,
                onDismiss = { deniedLocalNetworkAction = null },
                onReconnect = {},
                onOpenSettings = {
                    cameraSettingsViewModel.preparePermissionRecovery(action)
                    try {
                        permissionSettingsLauncher.launch(SetupSettingsNavigator.appPermissionSettingsIntent(context))
                    } catch (_: Exception) {
                        cameraSettingsViewModel.cancelPermissionRecovery()
                        Toast.makeText(context, settingsLaunchFailureMessage, Toast.LENGTH_LONG).show()
                    }
                }
            )
        }

        restoreResultMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { restoreResultMessage = null },
                title = { Text(stringResource(R.string.settings_camera)) },
                text = { Text(stringResource(message)) },
                confirmButton = {
                    TextButton(onClick = { restoreResultMessage = null }) {
                        Text(stringResource(R.string.action_ok))
                    }
                },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        lockErrorMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { lockErrorMessage = null },
                title = { Text(stringResource(R.string.settings_lock)) },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = { lockErrorMessage = null }) {
                        Text(stringResource(R.string.action_ok))
                    }
                },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        if (showDeveloperOptionsConfirm) {
            AlertDialog(
                onDismissRequest = { showDeveloperOptionsConfirm = false },
                title = { Text(stringResource(R.string.developer_disable_title)) },
                text = {
                    Text(
                        stringResource(R.string.developer_disable_body)
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeveloperOptionsConfirm = false
                            val result = DeveloperOptionsManager.disableDeveloperOptions(context)
                            if (result.isSuccess) {
                                developerOptionsResultMessage =
                                    resources.getString(R.string.developer_disabled)
                            } else {
                                showDeveloperOptionsFallback = true
                            }
                        }
                    ) {
                        Text(stringResource(R.string.action_turn_off))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeveloperOptionsConfirm = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        if (showDeveloperOptionsFallback) {
            AlertDialog(
                onDismissRequest = { showDeveloperOptionsFallback = false },
                title = { Text(stringResource(R.string.developer_disable_title)) },
                text = {
                    Text(
                        stringResource(R.string.developer_manual_body)
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (handleSettingsLaunch(DeveloperOptionsManager.openDeveloperOptions(context))) {
                                showDeveloperOptionsFallback = false
                            }
                        }
                    ) {
                        Text(stringResource(R.string.developer_open))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeveloperOptionsFallback = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        developerOptionsResultMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { developerOptionsResultMessage = null },
                title = { Text(stringResource(R.string.status_done)) },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = { developerOptionsResultMessage = null }) {
                        Text(stringResource(R.string.action_ok))
                    }
                },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        if (showCameraRoutine) {
            CameraRoutineDialog(onDismiss = { showCameraRoutine = false })
        }

        if (showLicenseDialog) {
            AlertDialog(
                onDismissRequest = { showLicenseDialog = false },
                title = {
                    Text(
                        text = stringResource(R.string.settings_licenses),
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
                            text = stringResource(R.string.license_copyright),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.license_terms),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            thickness = 0.5.dp
                        )
                        Text(
                            text = stringResource(R.string.license_libraries),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.license_list),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLicenseDialog = false }) {
                        Text(stringResource(R.string.action_ok))
                    }
                },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}

@Suppress("DEPRECATION")
internal fun currentVersionName(context: Context): String {
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

private fun Context.findActivity(): ComponentActivity? {
    var currentContext: Context = this
    while (currentContext is ContextWrapper) {
        if (currentContext is ComponentActivity) return currentContext
        currentContext = currentContext.baseContext
    }
    return currentContext as? ComponentActivity
}
