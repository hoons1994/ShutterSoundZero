package io.github.hoons1994.shuttersoundzero.ui.main

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.hoons1994.shuttersoundzero.R
import io.github.hoons1994.shuttersoundzero.core.CscMuteManager
import io.github.hoons1994.shuttersoundzero.core.CscStateVerifier
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

data class MainUiState(
    val isCscMuted: Boolean = false,
    val hasCscPermission: Boolean = false,
    val isDeveloperOptionsEnabled: Boolean = false,
    val isWirelessDebuggingEnabled: Boolean = false,
    val setupIssue: SetupIssue? = null,
    val adbGrantCommand: String = "",
    val adbDirectSetCommand: String = "",
    val adbCheckCommand: String = "",
    val infoMessage: String? = null,
    val errorMessage: String? = null,
    val showSwitchFailureHelp: Boolean = false,
    val cameraMuteFailure: CameraMuteFailure? = null,
    val isCscChangeInProgress: Boolean = false,
    val showWirelessDebuggingCleanupHelp: Boolean = false,
    val systemVolume: SystemVolumeUiState = SystemVolumeUiState()
)

data class SystemVolumeUiState(
    val current: Int? = null,
    val min: Int = 0,
    val max: Int = 0,
    val isFixed: Boolean = false,
    val error: String? = null,
    val isRingerMuted: Boolean = false,
    val isStreamMuted: Boolean = false,
    val requested: Int? = null
)

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {
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
        val isMuted = CscMuteManager.isCscShutterSoundMuted(app)
        return MainUiState(
            isCscMuted = isMuted,
            hasCscPermission = hasPermission,
            isDeveloperOptionsEnabled = DeveloperOptionsManager.isDeveloperOptionsEnabled(app),
            isWirelessDebuggingEnabled = DeveloperOptionsManager.isWirelessDebuggingEnabled(app),
            setupIssue = prefs.lastSetupIssue,
            adbGrantCommand = CscMuteManager.getAdbGrantPermissionCommand(app),
            adbDirectSetCommand = CscMuteManager.getAdbDirectCommand(true),
            adbCheckCommand = CscMuteManager.getAdbCheckCommand(),
            systemVolume = readSystemVolume()
        )
    }

    fun refreshState() {
        val app = getApplication<Application>()
        val perm = !prefs.isPermissionRevokedByUser && CscMuteManager.hasWritePermission(app)
        val isMuted = CscMuteManager.isCscShutterSoundMuted(app)

        if (perm && isMuted && prefs.lastSetupIssue != null) {
            prefs.lastSetupIssue = null
        }

        _uiState.update { current ->
            current.copy(
                isCscMuted = isMuted,
                hasCscPermission = perm,
                isDeveloperOptionsEnabled = DeveloperOptionsManager.isDeveloperOptionsEnabled(app),
                isWirelessDebuggingEnabled = DeveloperOptionsManager.isWirelessDebuggingEnabled(app),
                setupIssue = prefs.lastSetupIssue,
                adbGrantCommand = CscMuteManager.getAdbGrantPermissionCommand(app),
                adbDirectSetCommand = CscMuteManager.getAdbDirectCommand(true),
                adbCheckCommand = CscMuteManager.getAdbCheckCommand(),
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
        prefs.clearTransientAdbConnectionState()
        prefs.lastSetupIssue = null
        adbManager.clearDiscoveredPorts()
        _uiState.update { it.copy(setupIssue = null, cameraMuteFailure = null) }
        startPairingNow(context)
    }

    fun reportLocalNetworkPermissionDenied() {
        prefs.lastSetupIssue = SetupIssue.LOCAL_NETWORK_PERMISSION
        _uiState.update { it.copy(setupIssue = SetupIssue.LOCAL_NETWORK_PERMISSION) }
    }

    private fun startPairingNow(context: Context) {
        val devOptionsOff = !DeveloperOptionsManager.isDeveloperOptionsEnabled(context)
        PairingForegroundService.start(context, devOptionsOff)
        SetupSettingsNavigator.openPairingSetupScreen(context)
        _uiState.update {
            it.copy(
                infoMessage = context.getString(R.string.main_pairing_code_entry_guidance)
            )
        }
    }

    fun cancelNotificationPairing(context: Context) {
        PairingForegroundService.stop(context)
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

        if (!DeveloperOptionsManager.isWirelessDebuggingEnabled(app)) {
            _uiState.update {
                it.copy(
                    isWirelessDebuggingEnabled = false,
                    showSwitchFailureHelp = true,
                    errorMessage = null,
                    infoMessage = null
                )
            }
            return
        }

        // 실제 CSC 적용을 확인하기 전에는 완료 상태를 표시하지 않는다.
        _uiState.update { it.copy(isCscChangeInProgress = true, cameraMuteFailure = null) }

        viewModelScope.launch {
            try {
                val adbResult = adbManager.setCameraMute(enableMute)
                val actualStateMatchesRequest = adbResult.isSuccess && CscStateVerifier.waitFor(enableMute) {
                    CscMuteManager.isCscShutterSoundMuted(app)
                }
                refreshState()

                if (actualStateMatchesRequest) {
                    if (enableMute) {
                        prefs.lastSetupIssue = null
                    }
                    val wirelessCleanup = DeveloperOptionsManager.disableWirelessDebugging(app)
                    _uiState.update {
                        it.copy(
                            isWirelessDebuggingEnabled = DeveloperOptionsManager.isWirelessDebuggingEnabled(app),
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
            val result = adbManager.revokePermissionViaAdb()
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
