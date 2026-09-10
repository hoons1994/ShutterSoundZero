package com.charmingcolor.shuttersoundzero.ui.main

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.charmingcolor.shuttersoundzero.core.CscMuteManager
import com.charmingcolor.shuttersoundzero.core.CscStateVerifier
import com.charmingcolor.shuttersoundzero.core.CscTogglePersistencePolicy
import com.charmingcolor.shuttersoundzero.core.DeveloperOptionsManager
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
            adbGrantCommand = CscMuteManager.getAdbGrantPermissionCommand(app),
            adbDirectSetCommand = CscMuteManager.getAdbDirectCommand(true),
            adbCheckCommand = CscMuteManager.getAdbCheckCommand()
        )
    }

    fun refreshState() {
        val app = getApplication<Application>()
        val perm = !prefs.isPermissionRevokedByUser && CscMuteManager.hasWritePermission(app)
        val isMuted = CscMuteManager.isCscShutterSoundMuted(app)

        _uiState.update { current ->
            current.copy(
                isCscMuted = isMuted,
                hasCscPermission = perm,
                adbGrantCommand = CscMuteManager.getAdbGrantPermissionCommand(app),
                adbDirectSetCommand = CscMuteManager.getAdbDirectCommand(true),
                adbCheckCommand = CscMuteManager.getAdbCheckCommand()
            )
        }
    }

    fun startNotificationPairing(context: Context) {
        // 사용자가 설정을 시작한 것만으로 권한 연동 상태를 성공으로 바꾸지 않는다.
        // 실제 pm grant와 CSC 적용이 완료된 뒤 StandaloneAdbManager가 이 값을 갱신한다.
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
                it.copy(errorMessage = "보안 설정 변경 권한이 필요합니다. 아래 [1회 설정 시작]을 진행해 주세요.")
            }
            return
        }

        if (!DeveloperOptionsManager.isWirelessDebuggingEnabled(app)) {
            _uiState.update {
                it.copy(
                    showSwitchFailureHelp = true,
                    errorMessage = null,
                    infoMessage = null
                )
            }
            return
        }

        val previousDesiredMute = prefs.shouldMuteOnBoot

        // 사용자가 누른 즉시 반응하되, 작업 완료 후에는 반드시 실제 CSC 값을 다시 읽어 확정한다.
        prefs.shouldMuteOnBoot = enableMute
        _uiState.update { it.copy(isCscMuted = enableMute) }

        viewModelScope.launch {
            val adbResult = adbManager.setCameraMute(enableMute)
            val actualStateMatchesRequest = adbResult.isSuccess && CscStateVerifier.waitFor(enableMute) {
                CscMuteManager.isCscShutterSoundMuted(app)
            }
            refreshState()
            prefs.shouldMuteOnBoot = CscTogglePersistencePolicy.resolve(
                previousDesiredMute = previousDesiredMute,
                targetMuted = enableMute,
                stateApplied = actualStateMatchesRequest
            )

            if (actualStateMatchesRequest) {
                val wirelessCleanup = DeveloperOptionsManager.disableWirelessDebugging(app)
                _uiState.update {
                    it.copy(
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
                // 실패 시 사용자의 기존 무음 사용 의도는 보존하고, UI는 실제 CSC 값 그대로 유지한다.
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
