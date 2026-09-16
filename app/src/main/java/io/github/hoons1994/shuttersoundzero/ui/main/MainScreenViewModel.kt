package io.github.hoons1994.shuttersoundzero.ui.main

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
    val showWirelessDebuggingCleanupHelp: Boolean = false
)

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PreferencesRepository.getInstance(application)
    private val adbManager = StandaloneAdbManager.getInstance(application)

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
            adbCheckCommand = CscMuteManager.getAdbCheckCommand()
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
                adbCheckCommand = CscMuteManager.getAdbCheckCommand()
            )
        }
    }

    fun startNotificationPairing(context: Context) {
        prefs.clearTransientAdbConnectionState()
        prefs.lastSetupIssue = null
        adbManager.lastDiscoveredPairingPort = null
        adbManager.lastDiscoveredConnectPort = null
        _uiState.update { it.copy(setupIssue = null) }
        startPairingNow(context)
    }

    private fun startPairingNow(context: Context) {
        val devOptionsOff = !CscMuteManager.isDeveloperOptionsEnabled(context)
        PairingForegroundService.start(context, devOptionsOff)
        CscMuteManager.openPairingSetupScreen(context)
        _uiState.update {
            it.copy(
                infoMessage = "[페어링 코드로 기기 페어링] 화면에서 알림의 [코드 입력]을 사용해 6자리 코드를 입력해 주세요."
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
                it.copy(errorMessage = "이 앱은 삼성 갤럭시 전용 앱입니다. 다른 제조사 기기에서는 사용할 수 없습니다.")
            }
            return
        }

        if (!CscMuteManager.hasWritePermission(app)) {
            _uiState.update {
                it.copy(errorMessage = "보안 설정 변경 권한이 필요합니다. 아래 [1회 설정 시작]을 진행해 주세요.")
            }
            return
        }

        if (!LocalNetworkAccess.isGranted(app)) {
            _uiState.update {
                it.copy(
                    showSwitchFailureHelp = false,
                    infoMessage = null,
                    errorMessage = "Android 17에서 무선 ADB를 사용하려면 로컬 네트워크 권한이 필요합니다. 앱 설정에서 권한을 허용해 주세요."
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
                                "카메라 무음 설정을 적용했고 무선 디버깅도 껐습니다."
                            !enableMute && wirelessCleanup.isSuccess ->
                                "카메라 셔터음을 기본 상태로 복원했고 무선 디버깅도 껐습니다."
                            enableMute ->
                                "카메라 무음 설정은 완료됐습니다. 무선 디버깅은 직접 꺼 주세요."
                            else ->
                                "카메라 셔터음은 기본 상태로 복원됐습니다. 무선 디버깅은 직접 꺼 주세요."
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
                    errorMessage = "Android 17에서 권한 연동을 해제하려면 로컬 네트워크 권한이 필요합니다. 앱 설정에서 권한을 허용해 주세요."
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
                        infoMessage = "권한 연동이 해제되었습니다. 다시 연동하려면 아래 버튼을 눌러주세요.",
                        errorMessage = null
                    )
                } else {
                    it.copy(
                        infoMessage = null,
                        errorMessage = "권한 연동 해제에 실패했습니다. 무선 디버깅을 켠 뒤 다시 시도해 주세요."
                    )
                }
            }
        }
    }
}
