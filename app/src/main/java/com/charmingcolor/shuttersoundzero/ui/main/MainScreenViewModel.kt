package com.charmingcolor.shuttersoundzero.ui.main

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.charmingcolor.shuttersoundzero.core.CscMuteManager
import com.charmingcolor.shuttersoundzero.core.adb.StandaloneAdbManager
import com.charmingcolor.shuttersoundzero.data.PreferencesRepository
import com.charmingcolor.shuttersoundzero.service.PairingForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val isCscMuted: Boolean = false,
    val hasCscPermission: Boolean = false,
    val isAutoRestoreOnBoot: Boolean = true,
    val isFirmwareUpdateCheckEnabled: Boolean = true,
    val adbGrantCommand: String = "",
    val adbDirectSetCommand: String = "",
    val adbCheckCommand: String = "",

    // 자체 무선 디버깅 페어링 상태
    val detectedPairingPort: Int? = null,
    val detectedConnectPort: Int? = null,
    val isWirelessPairingInProgress: Boolean = false,
    val wirelessPairingError: String? = null,

    val infoMessage: String? = null,
    val errorMessage: String? = null,
    val showSwitchFailureHelp: Boolean = false
)

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PreferencesRepository.getInstance(application)
    private val adbManager = StandaloneAdbManager.getInstance(application)

    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        if (prefs.lastFirmwareFingerprint == null) {
            prefs.lastFirmwareFingerprint = android.os.Build.FINGERPRINT
        }
        refreshState()
    }

    private fun createInitialState(): MainUiState {
        val app = getApplication<Application>()
        val hasPermission = !prefs.isPermissionRevokedByUser && CscMuteManager.hasWritePermission(app)
        val isMuted = if (!hasPermission) false else prefs.shouldMuteOnBoot
        return MainUiState(
            isCscMuted = isMuted,
            hasCscPermission = hasPermission,
            isAutoRestoreOnBoot = prefs.isAutoRestoreOnBootEnabled,
            isFirmwareUpdateCheckEnabled = prefs.isFirmwareUpdateCheckEnabled,
            adbGrantCommand = CscMuteManager.getAdbGrantPermissionCommand(app),
            adbDirectSetCommand = CscMuteManager.getAdbDirectCommand(true),
            adbCheckCommand = CscMuteManager.getAdbCheckCommand()
        )
    }

    fun refreshState() {
        val app = getApplication<Application>()
        val perm = !prefs.isPermissionRevokedByUser && CscMuteManager.hasWritePermission(app)
        val isMuted = if (!perm) false else prefs.shouldMuteOnBoot
        val autoRestore = prefs.isAutoRestoreOnBootEnabled
        val firmwareCheck = prefs.isFirmwareUpdateCheckEnabled

        _uiState.update { current ->
            current.copy(
                isCscMuted = isMuted,
                hasCscPermission = perm,
                isAutoRestoreOnBoot = autoRestore,
                isFirmwareUpdateCheckEnabled = firmwareCheck,
                adbGrantCommand = CscMuteManager.getAdbGrantPermissionCommand(app),
                adbDirectSetCommand = CscMuteManager.getAdbDirectCommand(true),
                adbCheckCommand = CscMuteManager.getAdbCheckCommand()
            )
        }
    }

    fun startNotificationPairing(context: Context) {
        prefs.isPermissionRevokedByUser = false
        val devOptionsOff = !CscMuteManager.isDeveloperOptionsEnabled(context)
        PairingForegroundService.start(context, devOptionsOff)
        CscMuteManager.openPairingSetupScreen(context)
        _uiState.update {
            it.copy(
                infoMessage = "상단바에 페어링 알림이 등록되었습니다! [페어링 코드로 기기 페어링] 화면을 띄운 뒤 상단바를 내려 6자리 코드를 입력해 주세요."
            )
        }
    }

    fun cancelNotificationPairing(context: Context) {
        PairingForegroundService.stop(context)
    }

    fun stopMdnsDiscovery() {
        PairingForegroundService.stop(getApplication())
    }

    /**
     * 6자리 페어링 코드로 로컬 페어링 후 CSC 무음화 명령 자동 실행
     */
    fun pairAndApplyMute(port: Int, pairingCode: String, onComplete: (Boolean) -> Unit) {
        _uiState.update {
            it.copy(isWirelessPairingInProgress = true, wirelessPairingError = null)
        }

        viewModelScope.launch {
            val pairResult = adbManager.pairLocal(port, pairingCode)
            if (pairResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isWirelessPairingInProgress = false,
                        wirelessPairingError = "페어링에 실패했습니다. 무선 디버깅 상태와 6자리 코드를 확인해 다시 시도해 주세요."
                    )
                }
                onComplete(false)
                return@launch
            }

            val connectPort = _uiState.value.detectedConnectPort
            val muteResult = adbManager.applyCameraMuteViaAdb(connectPort)

            if (muteResult.isSuccess) {
                stopMdnsDiscovery()
                prefs.shouldMuteOnBoot = true
                refreshState()
                _uiState.update {
                    it.copy(
                        isWirelessPairingInProgress = false,
                        wirelessPairingError = null,
                        infoMessage = "✨ 자체 무선 페어링 완료! 카메라 셔터음 무음화가 적용되었습니다."
                    )
                }
                onComplete(true)
            } else {
                refreshState()
                _uiState.update {
                    it.copy(
                        isWirelessPairingInProgress = false,
                        wirelessPairingError = null,
                        infoMessage = "✅ 기기 페어링이 완료되었습니다! 셔터음 무음 스위치를 켜주세요."
                    )
                }
                onComplete(true)
            }
        }
    }

    /**
     * 시스템 일반 CSC 토글
     */
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
                it.copy(errorMessage = "보안 설정 변경 권한이 필요합니다. 아래 [권한 설정]을 진행해 주세요.")
            }
            return
        }

        // 스위치 UI 즉시 반응 (낙관적 업데이트)
        prefs.shouldMuteOnBoot = enableMute
        refreshState()

        viewModelScope.launch {
            val adbResult = adbManager.setCameraMute(enableMute)
            if (adbResult.isSuccess) {
                _uiState.update {
                    it.copy(
                        infoMessage = if (enableMute) "카메라 셔터음 무음화가 활성화되었습니다. (진동/무음 시 무음)"
                        else "카메라 셔터음이 기본 상태(소리 발생)로 복원되었습니다.",
                        errorMessage = null
                    )
                }
            } else {
                // 실패 시 스위치 원복 및 무선 디버깅 친절 안내 다이얼로그 플래그 활성화
                prefs.shouldMuteOnBoot = !enableMute
                refreshState()
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

    fun setAutoRestoreOnBoot(enabled: Boolean) {
        prefs.isAutoRestoreOnBootEnabled = enabled
        _uiState.update { it.copy(isAutoRestoreOnBoot = enabled) }
    }

    fun setFirmwareUpdateCheck(enabled: Boolean) {
        prefs.isFirmwareUpdateCheckEnabled = enabled
        _uiState.update { it.copy(isFirmwareUpdateCheckEnabled = enabled) }
    }

    fun dismissMessages() {
        _uiState.update { it.copy(infoMessage = null, errorMessage = null) }
    }

    /**
     * 권한 연동 해제 및 초기화
     */
    fun resetPermission() {
        viewModelScope.launch {
            val result = adbManager.revokePermissionViaAdb()
            refreshState()
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(
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

