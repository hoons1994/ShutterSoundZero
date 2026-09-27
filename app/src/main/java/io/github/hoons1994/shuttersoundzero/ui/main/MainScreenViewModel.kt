package io.github.hoons1994.shuttersoundzero.ui.main

import android.app.Application
import android.content.Context
import android.media.AudioManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.hoons1994.shuttersoundzero.R
import io.github.hoons1994.shuttersoundzero.core.CscMuteManager
import io.github.hoons1994.shuttersoundzero.core.CscStateVerifier
import io.github.hoons1994.shuttersoundzero.core.DeveloperOptionsManager
import io.github.hoons1994.shuttersoundzero.core.adb.LocalNetworkAccess
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
    val isWirelessDebuggingEnabled: Boolean = false,
    val setupIssue: SetupIssue? = null,
    val adbGrantCommand: String = "",
    val adbDirectSetCommand: String = "",
    val adbCheckCommand: String = "",
    val infoMessage: String? = null,
    val errorMessage: String? = null,
    val showSwitchFailureHelp: Boolean = false,
    val showWirelessDebuggingCleanupHelp: Boolean = false,
    val systemVolume: SystemVolumeUiState = SystemVolumeUiState()
)

data class SystemVolumeUiState(
    val current: Int? = null,
    val min: Int = 0,
    val max: Int = 0,
    val isFixed: Boolean = false,
    val error: String? = null
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
                isWirelessDebuggingEnabled = DeveloperOptionsManager.isWirelessDebuggingEnabled(app),
                setupIssue = prefs.lastSetupIssue,
                adbGrantCommand = CscMuteManager.getAdbGrantPermissionCommand(app),
                adbDirectSetCommand = CscMuteManager.getAdbDirectCommand(true),
                adbCheckCommand = CscMuteManager.getAdbCheckCommand(),
                systemVolume = readSystemVolume()
            )
        }
    }

    private fun readSystemVolume(): SystemVolumeUiState = try {
        SystemVolumeUiState(
            current = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM),
            min = audioManager.getStreamMinVolume(AudioManager.STREAM_SYSTEM),
            max = audioManager.getStreamMaxVolume(AudioManager.STREAM_SYSTEM),
            isFixed = audioManager.isVolumeFixed
        )
    } catch (_: RuntimeException) {
        SystemVolumeUiState(error = "시스템 음량을 확인할 수 없습니다.")
    }

    fun setSystemVolume(index: Int): Int? {
        val volume = _uiState.value.systemVolume
        if (volume.current == null || volume.isFixed || volume.max <= volume.min) return volume.current

        val requested = index.coerceIn(volume.min, volume.max)
        val updated = try {
            audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, requested, 0)
            readSystemVolume().let { actual ->
                if (actual.current != null && actual.current != requested) {
                    actual.copy(error = "기기에서 요청한 시스템 음량을 적용하지 않았습니다.")
                } else {
                    actual
                }
            }
        } catch (_: SecurityException) {
            volume.copy(error = "이 기기에서는 시스템 음량 변경이 허용되지 않습니다.")
        } catch (_: RuntimeException) {
            volume.copy(error = "시스템 음량을 변경하지 못했습니다.")
        }
        _uiState.update { it.copy(systemVolume = updated) }
        return updated.current
    }

    fun startNotificationPairing(context: Context) {
        prefs.clearTransientAdbConnectionState()
        prefs.lastSetupIssue = null
        adbManager.clearDiscoveredPorts()
        _uiState.update { it.copy(setupIssue = null) }
        startPairingNow(context)
    }

    private fun startPairingNow(context: Context) {
        val devOptionsOff = !CscMuteManager.isDeveloperOptionsEnabled(context)
        PairingForegroundService.start(context, devOptionsOff)
        CscMuteManager.openPairingSetupScreen(context)
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

        // UI만 낙관적으로 갱신한다. 영구 사용자 의도는 실제 CSC 적용을 확인한
        // StandaloneAdbManager만 변경해 교차 진입점의 stale write를 막는다.
        _uiState.update { it.copy(isCscMuted = enableMute) }

        viewModelScope.launch {
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
                        showSwitchFailureHelp = true,
                        errorMessage = null,
                        infoMessage = null
                    )
                }
            }
        }
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
