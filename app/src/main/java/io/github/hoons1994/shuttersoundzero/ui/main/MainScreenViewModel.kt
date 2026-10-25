package io.github.hoons1994.shuttersoundzero.ui.main

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import io.github.hoons1994.shuttersoundzero.logging.DiagnosticLogger
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.hoons1994.shuttersoundzero.camera.CameraSettingsRepository
import io.github.hoons1994.shuttersoundzero.camera.ChangeCameraMute
import io.github.hoons1994.shuttersoundzero.AppDependencies
import io.github.hoons1994.shuttersoundzero.R
import io.github.hoons1994.shuttersoundzero.core.CscMuteManager
import io.github.hoons1994.shuttersoundzero.core.DeveloperOptionsManager
import io.github.hoons1994.shuttersoundzero.core.SetupSettingsNavigator
import io.github.hoons1994.shuttersoundzero.core.SystemVolumeVerifier
import io.github.hoons1994.shuttersoundzero.core.adb.LocalNetworkAccess
import io.github.hoons1994.shuttersoundzero.core.adb.CameraMuteFailure
import io.github.hoons1994.shuttersoundzero.core.adb.StandaloneAdbManager
import io.github.hoons1994.shuttersoundzero.data.PreferencesRepository
import io.github.hoons1994.shuttersoundzero.data.SetupIssue
import io.github.hoons1994.shuttersoundzero.service.PairingForegroundService
import io.github.hoons1994.shuttersoundzero.security.AppLockSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainScreenViewModel internal constructor(
    application: Application,
    private val changeCameraMute: ChangeCameraMute,
    private val cameraSettings: CameraSettingsRepository,
    private val isSamsungDevice: () -> Boolean = CscMuteManager::isSamsungDevice,
    private val localNetworkGranted: (Context) -> Boolean = LocalNetworkAccess::isGranted,
    private val wirelessDebuggingState: (Context) -> Boolean? = DeveloperOptionsManager::readWirelessDebuggingEnabled,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    constructor(application: Application, savedStateHandle: SavedStateHandle) : this(
        application, AppDependencies.changeCameraMute(application), AppDependencies.cameraSettings(application),
        savedStateHandle = savedStateHandle
    )
    private val prefs = PreferencesRepository.getInstance(application)
    private val adbManager = StandaloneAdbManager.getInstance(application)
    private val audioManager = application.getSystemService(AudioManager::class.java)

    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        prefs.ensureSoftwareUpdateBaseline(android.os.Build.FINGERPRINT)
        refreshState()
    }

    private fun createInitialState(): MainUiState {
        val app = getApplication<Application>()
        val hasPermission = !prefs.isPermissionRevokedByUser && CscMuteManager.hasWritePermission(app)
        val isMuted = CscMuteManager.readCscMutedState(app)
        return MainUiState(
            isCscMuted = isMuted,
            hasCscPermission = hasPermission,
            isDeveloperOptionsEnabled = DeveloperOptionsManager.isDeveloperOptionsEnabled(app),
            isWirelessDebuggingEnabled = wirelessDebuggingState(app),
            setupIssue = prefs.lastSetupIssue,
            systemVolume = readSystemVolume()
        )
    }

    fun refreshState() {
        val app = getApplication<Application>()
        val perm = !prefs.isPermissionRevokedByUser && CscMuteManager.hasWritePermission(app)
        val isMuted = CscMuteManager.readCscMutedState(app)
        val wirelessDebuggingEnabled = wirelessDebuggingState(app)

        if (perm && isMuted == true && prefs.lastSetupIssue != null) {
            prefs.lastSetupIssue = null
        }

        _uiState.update { current ->
            current.withWirelessDebuggingState(wirelessDebuggingEnabled).copy(
                isCscMuted = isMuted,
                hasCscPermission = perm,
                isDeveloperOptionsEnabled = DeveloperOptionsManager.isDeveloperOptionsEnabled(app),
                setupIssue = prefs.lastSetupIssue,
                systemVolume = readSystemVolume().copy(requested = current.systemVolume.requested)
            )
        }
    }

    private fun readSystemVolume(): SystemVolumeUiState = try {
        SystemVolumeUiState(
            current = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM),
            min = audioManager.getStreamMinVolume(AudioManager.STREAM_SYSTEM),
            max = audioManager.getStreamMaxVolume(AudioManager.STREAM_SYSTEM),
            isFixed = audioManager.isVolumeFixed,
            isRingerMuted = audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL,
            isStreamMuted = audioManager.isStreamMute(AudioManager.STREAM_SYSTEM)
        )
    } catch (_: RuntimeException) {
        SystemVolumeUiState(error = getApplication<Application>().getString(R.string.system_volume_read_failed))
    }

    fun setSystemVolume(index: Int): Int? {
        val existing = _uiState.value.systemVolume
        if (existing.requested != null) return existing.requested
        val volume = readSystemVolume()
        if (volume.current == null || volume.isFixed || volume.isRingerMuted || volume.max <= volume.min) {
            DiagnosticLogger.recordSystemVolume(getApplication(), index, volume.current, DiagnosticLogger.Outcome.FAILURE,
                when {
                    volume.current == null -> DiagnosticLogger.VolumeReason.READ_FAILED
                    volume.isFixed -> DiagnosticLogger.VolumeReason.FIXED
                    volume.isRingerMuted -> DiagnosticLogger.VolumeReason.RINGER_MUTED
                    else -> DiagnosticLogger.VolumeReason.NO_RANGE
                })
            _uiState.update { it.copy(systemVolume = volume) }
            return volume.current
        }

        val requested = index.coerceIn(volume.min, volume.max)
        val started = SystemClock.elapsedRealtime()
        DiagnosticLogger.recordSystemVolume(getApplication(), requested, volume.current,
            DiagnosticLogger.Outcome.STARTED, DiagnosticLogger.VolumeReason.REQUESTED)
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, requested, 0)
        } catch (_: SecurityException) {
            DiagnosticLogger.recordSystemVolume(getApplication(), requested, volume.current,
                DiagnosticLogger.Outcome.FAILURE, DiagnosticLogger.VolumeReason.SECURITY_BLOCKED,
                SystemClock.elapsedRealtime() - started)
            _uiState.update { it.copy(systemVolume = volume.copy(
                error = getApplication<Application>().getString(R.string.system_volume_permission_blocked)
            )) }
            return volume.current
        } catch (_: RuntimeException) {
            DiagnosticLogger.recordSystemVolume(getApplication(), requested, volume.current,
                DiagnosticLogger.Outcome.FAILURE, DiagnosticLogger.VolumeReason.CHANGE_FAILED,
                SystemClock.elapsedRealtime() - started)
            _uiState.update { it.copy(systemVolume = volume.copy(
                error = getApplication<Application>().getString(R.string.system_volume_change_failed)
            )) }
            return volume.current
        }

        // Keep the requested thumb position separate from the last confirmed audio-service value.
        _uiState.update { it.copy(systemVolume = volume.copy(requested = requested)) }
        viewModelScope.launch {
            SystemVolumeVerifier.readAfterChange(requested) {
                try {
                    audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM)
                } catch (_: RuntimeException) {
                    null
                }
            }
            val actual = readSystemVolume()
            val reason = when {
                actual.current == null -> DiagnosticLogger.VolumeReason.READ_FAILED
                actual.isRingerMuted -> DiagnosticLogger.VolumeReason.RINGER_MUTED
                actual.current == requested -> DiagnosticLogger.VolumeReason.INDEX_APPLIED
                actual.isStreamMuted && isDoNotDisturbEnabled() -> DiagnosticLogger.VolumeReason.DND_BLOCKED
                else -> DiagnosticLogger.VolumeReason.NOT_APPLIED
            }
            DiagnosticLogger.recordSystemVolume(getApplication(), requested, actual.current,
                if (reason == DiagnosticLogger.VolumeReason.INDEX_APPLIED) DiagnosticLogger.Outcome.SUCCESS
                else DiagnosticLogger.Outcome.FAILURE, reason, SystemClock.elapsedRealtime() - started)
            val updated = if (actual.current != null && actual.current != requested && !actual.isRingerMuted) {
                actual.copy(error = getApplication<Application>().getString(
                    if (actual.isStreamMuted && isDoNotDisturbEnabled()) {
                        R.string.system_volume_dnd_blocked
                    } else {
                        R.string.system_volume_not_applied
                    }
                ))
            } else {
                actual
            }
            _uiState.update { it.copy(systemVolume = updated) }
        }
        return requested
    }

    fun refreshSystemVolume() {
        _uiState.update { current ->
            current.copy(systemVolume = readSystemVolume().copy(requested = current.systemVolume.requested))
        }
    }

    private fun isDoNotDisturbEnabled(): Boolean = try {
        getApplication<Application>().getSystemService(NotificationManager::class.java).currentInterruptionFilter.let {
            it != NotificationManager.INTERRUPTION_FILTER_ALL &&
                it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        }
    } catch (_: RuntimeException) {
        false
    }

    fun startNotificationPairing(context: Context, targetMuted: Boolean = prefs.pendingPairingTarget ?: true) {
        prefs.pendingPairingTarget = targetMuted
        val resumeApply = prefs.lastSetupIssue == SetupIssue.CAMERA_APPLY
        val devOptionsOff = !DeveloperOptionsManager.isDeveloperOptionsEnabled(context)
        if (!handleSettingsLaunch(SetupSettingsNavigator.openPairingSetupScreen(context))) return
        if (!resumeApply) {
            prefs.clearTransientAdbConnectionState()
            prefs.lastSetupIssue = null
            adbManager.clearDiscoveredPorts()
        }
        _uiState.update {
            it.copy(
                setupIssue = if (resumeApply) SetupIssue.CAMERA_APPLY else null,
                cameraMuteFailure = null,
                pendingCameraMuteTarget = null,
                showSwitchFailureHelp = false,
                showWirelessDebuggingCleanupHelp = false
            )
        }
        PairingForegroundService.start(context, devOptionsOff, resumeApply, targetMuted)
        _uiState.update {
            it.copy(
                infoMessage = context.getString(if (resumeApply) R.string.pairing_help_connection_retry
                    else R.string.main_pairing_code_entry_guidance),
                errorMessage = null
            )
        }
    }

    fun reportLocalNetworkPermissionDenied() {
        prefs.lastSetupIssue = SetupIssue.LOCAL_NETWORK_PERMISSION
        _uiState.update { it.copy(setupIssue = SetupIssue.LOCAL_NETWORK_PERMISSION) }
    }

    fun handleSettingsLaunch(result: Result<Unit>): Boolean {
        if (result.isFailure) {
            _uiState.update {
                it.copy(
                    infoMessage = null,
                    errorMessage = getApplication<Application>().getString(R.string.settings_launch_failed)
                )
            }
        }
        return result.isSuccess
    }

    fun toggleCscMute(enableMute: Boolean) {
        if (_uiState.value.isCscChangeInProgress) return
        cancelCameraMuteAfterWirelessSettings()
        val app = getApplication<Application>()
        _uiState.update { it.copy(pendingCameraMuteTarget = null) }

        if (!isSamsungDevice()) {
            _uiState.update {
                it.copy(errorMessage = app.getString(R.string.main_non_samsung_device_error))
            }
            return
        }

        if (!CscMuteManager.hasWritePermission(app)) {
            _uiState.update {
                it.copy(errorMessage = app.getString(R.string.main_write_secure_settings_required))
            }
            return
        }

        if (!localNetworkGranted(app)) {
            reportCameraMutePermissionRequired(enableMute)
            return
        }

        val wirelessDebuggingEnabled = wirelessDebuggingState(app)
        if (wirelessDebuggingEnabled != true) {
            _uiState.update {
                it.copy(
                    isWirelessDebuggingEnabled = wirelessDebuggingEnabled,
                    showSwitchFailureHelp = wirelessDebuggingEnabled == false,
                    showWirelessDebuggingCleanupHelp = false,
                    cameraMuteFailure = null,
                    pendingCameraMuteTarget = enableMute,
                    errorMessage = if (wirelessDebuggingEnabled == null) {
                        app.getString(R.string.wireless_debugging_state_unknown_guidance)
                    } else null,
                    infoMessage = null
                )
            }
            return
        }

        // 실제 CSC 적용을 확인하기 전에는 완료 상태를 표시하지 않는다.
        _uiState.update {
            it.copy(
                isCscChangeInProgress = true,
                cameraMuteFailure = null,
                pendingCameraMuteTarget = null,
                showSwitchFailureHelp = false,
                showWirelessDebuggingCleanupHelp = false
            )
        }

        viewModelScope.launch {
            try {
                val adbResult = changeCameraMute(enableMute)
                if (adbResult.isSuccess) {
                    if (enableMute) {
                        prefs.lastSetupIssue = null
                    }
                    val wirelessCleanup = adbResult.getOrThrow().wirelessCleanup
                    _uiState.update {
                        it.copy(
                            isWirelessDebuggingEnabled = wirelessDebuggingState(app),
                            setupIssue = prefs.lastSetupIssue,
                            infoMessage = when {
                                enableMute && wirelessCleanup.isSuccess ->
                                    app.getString(R.string.main_mute_applied_wireless_disabled)
                                !enableMute && wirelessCleanup.isSuccess ->
                                    app.getString(R.string.main_restore_applied_wireless_disabled)
                                enableMute ->
                                    app.getString(R.string.main_mute_applied_disable_wireless_manually)
                                else ->
                                    app.getString(R.string.main_restore_applied_disable_wireless_manually)
                            },
                            errorMessage = null,
                            showWirelessDebuggingCleanupHelp = wirelessCleanup.isFailure
                        )
                    }
                } else {
                    val failure = CameraMuteFailure.from(adbResult.exceptionOrNull())
                    _uiState.update {
                        if (failure == CameraMuteFailure.LOCAL_NETWORK_PERMISSION) {
                            it.awaitCameraMutePermission(enableMute)
                        } else it.copy(
                            showSwitchFailureHelp = false,
                            cameraMuteFailure = failure,
                            pendingCameraMuteTarget = enableMute,
                            errorMessage = null,
                            infoMessage = null
                        )
                    }
                }
            } finally {
                refreshState()
                _uiState.update { it.copy(isCscChangeInProgress = false) }
            }
        }
    }

    fun reportCameraMutePermissionRequired(enableMute: Boolean) {
        _uiState.update { it.awaitCameraMutePermission(enableMute) }
    }

    fun dismissCameraMuteFailure() {
        _uiState.update { it.copy(cameraMuteFailure = null, pendingCameraMuteTarget = null) }
    }

    fun dismissSwitchFailureHelp() {
        _uiState.update { it.copy(showSwitchFailureHelp = false, pendingCameraMuteTarget = null) }
    }

    fun prepareCameraMuteAfterWirelessSettings(targetMuted: Boolean?) {
        savedStateHandle[WIRELESS_SETTINGS_TARGET] = targetMuted
        dismissSwitchFailureHelp()
    }

    fun cancelCameraMuteAfterWirelessSettings() {
        savedStateHandle.remove<Boolean>(WIRELESS_SETTINGS_TARGET)
    }

    fun resumeCameraMuteAfterWirelessSettings() {
        // ON_RESUME can arrive before the old home composition is removed by app lock.
        // Keep the request pending until authentication, independently of that composition.
        if (prefs.isAppLockEnabled && !AppLockSession.isUnlocked) return
        if (_uiState.value.isCscChangeInProgress) return
        val targetMuted = savedStateHandle.remove<Boolean>(WIRELESS_SETTINGS_TARGET) ?: return
        toggleCscMute(targetMuted)
    }

    private companion object {
        const val WIRELESS_SETTINGS_TARGET = "cameraMuteAfterWirelessSettings"
    }

    fun dismissWirelessDebuggingCleanupHelp() {
        _uiState.update { it.copy(showWirelessDebuggingCleanupHelp = false) }
    }

    fun dismissMessages() {
        _uiState.update { it.copy(infoMessage = null, errorMessage = null) }
    }

    fun resetPermission() {
        val app = getApplication<Application>()
        if (!LocalNetworkAccess.isGranted(app)) {
            _uiState.update {
                it.copy(
                    infoMessage = null,
                    errorMessage = app.getString(R.string.main_reset_local_network_permission_required)
                )
            }
            return
        }

        viewModelScope.launch {
            val result = cameraSettings.revokePermissionViaAdb()
            if (result.isSuccess) {
                prefs.lastSetupIssue = null
            }
            refreshState()
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(
                        setupIssue = null,
                        infoMessage = app.getString(R.string.main_permission_reset_success),
                        errorMessage = null
                    )
                } else {
                    it.copy(
                        infoMessage = null,
                        errorMessage = app.getString(R.string.main_permission_reset_failed)
                    )
                }
            }
        }
    }
}
