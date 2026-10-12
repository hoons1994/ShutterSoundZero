package io.github.hoons1994.shuttersoundzero.ui.main

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import androidx.lifecycle.AndroidViewModel
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainScreenViewModel internal constructor(
    application: Application,
    private val changeCameraMute: ChangeCameraMute,
    private val cameraSettings: CameraSettingsRepository
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application, AppDependencies.changeCameraMute(application), AppDependencies.cameraSettings(application)
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
            isWirelessDebuggingEnabled = DeveloperOptionsManager.readWirelessDebuggingEnabled(app),
            setupIssue = prefs.lastSetupIssue,
            systemVolume = readSystemVolume()
        )
    }

    fun refreshState() {
        val app = getApplication<Application>()
        val perm = !prefs.isPermissionRevokedByUser && CscMuteManager.hasWritePermission(app)
        val isMuted = CscMuteManager.readCscMutedState(app)
        val wirelessDebuggingEnabled = DeveloperOptionsManager.readWirelessDebuggingEnabled(app)

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
            _uiState.update { it.copy(systemVolume = volume) }
            return volume.current
        }

        val requested = index.coerceIn(volume.min, volume.max)
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, requested, 0)
        } catch (_: SecurityException) {
            _uiState.update { it.copy(systemVolume = volume.copy(
                error = getApplication<Application>().getString(R.string.system_volume_permission_blocked)
            )) }
            return volume.current
        } catch (_: RuntimeException) {
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

    fun startNotificationPairing(context: Context) {
        val devOptionsOff = !DeveloperOptionsManager.isDeveloperOptionsEnabled(context)
        if (!handleSettingsLaunch(SetupSettingsNavigator.openPairingSetupScreen(context))) return
        prefs.clearTransientAdbConnectionState()
        prefs.lastSetupIssue = null
        adbManager.clearDiscoveredPorts()
        _uiState.update {
            it.copy(
                setupIssue = null,
                cameraMuteFailure = null,
                showSwitchFailureHelp = false,
                showWirelessDebuggingCleanupHelp = false
            )
        }
        PairingForegroundService.start(context, devOptionsOff)
        _uiState.update {
            it.copy(
                infoMessage = context.getString(R.string.main_pairing_code_entry_guidance),
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
        val app = getApplication<Application>()

        if (!CscMuteManager.isSamsungDevice()) {
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

        if (!LocalNetworkAccess.isGranted(app)) {
            _uiState.update {
                it.copy(
                    showSwitchFailureHelp = false,
                    infoMessage = null,
                    errorMessage = app.getString(R.string.main_local_network_permission_required)
                )
            }
            return
        }

        val wirelessDebuggingEnabled = DeveloperOptionsManager.readWirelessDebuggingEnabled(app)
        if (wirelessDebuggingEnabled != true) {
            _uiState.update {
                it.copy(
                    isWirelessDebuggingEnabled = wirelessDebuggingEnabled,
                    showSwitchFailureHelp = wirelessDebuggingEnabled == false,
                    showWirelessDebuggingCleanupHelp = false,
                    cameraMuteFailure = null,
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
                            isWirelessDebuggingEnabled = DeveloperOptionsManager.readWirelessDebuggingEnabled(app),
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
                    _uiState.update {
                        it.copy(
                            showSwitchFailureHelp = false,
                            cameraMuteFailure = CameraMuteFailure.from(adbResult.exceptionOrNull()),
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

    fun dismissCameraMuteFailure() {
        _uiState.update { it.copy(cameraMuteFailure = null) }
    }

    fun dismissSwitchFailureHelp() {
        _uiState.update { it.copy(showSwitchFailureHelp = false) }
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
