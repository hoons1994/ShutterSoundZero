package com.hoons.shutterzero.ui.main

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hoons.shutterzero.core.CscMuteManager
import com.hoons.shutterzero.core.ShizukuManager
import com.hoons.shutterzero.data.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

data class MainUiState(
    val isCscMuted: Boolean = false,
    val hasCscPermission: Boolean = false,
    val isAutoRestoreOnBoot: Boolean = true,
    val adbGrantCommand: String = "",
    val adbDirectSetCommand: String = "",
    val adbCheckCommand: String = "",

    // Shizuku 무선 디버깅 연동 상태
    val isShizukuInstalled: Boolean = false,
    val isShizukuRunning: Boolean = false,
    val hasShizukuPermission: Boolean = false,

    val infoMessage: String? = null,
    val errorMessage: String? = null
)

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PreferencesRepository.getInstance(application)
    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == ShizukuManager.SHIZUKU_REQ_CODE) {
            refreshState()
            if (ShizukuManager.hasPermission()) {
                // Shizuku 권한 승인 즉시 셔터음 무음화 ADB 명령어를 백그라운드에서 자동 전송!
                toggleMuteViaShizuku(true)
            } else {
                _uiState.update {
                    it.copy(errorMessage = "Shizuku 권한 요청이 거부되었습니다.")
                }
            }
        }
    }

    private val binderListener = Shizuku.OnBinderReceivedListener {
        refreshState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        refreshState()
    }

    init {
        try {
            Shizuku.addRequestPermissionResultListener(permissionListener)
            Shizuku.addBinderReceivedListenerSticky(binderListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
        } catch (e: Exception) {
            // Shizuku provider may not be initialized in test/preview
        }
        refreshState()
    }

    override fun onCleared() {
        super.onCleared()
        try {
            Shizuku.removeRequestPermissionResultListener(permissionListener)
            Shizuku.removeBinderReceivedListener(binderListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
        } catch (e: Exception) {
            // Ignored
        }
    }

    private fun createInitialState(): MainUiState {
        val app = getApplication<Application>()
        return MainUiState(
            isCscMuted = CscMuteManager.isCscShutterSoundMuted(app),
            hasCscPermission = CscMuteManager.hasWritePermission(app),
            isAutoRestoreOnBoot = prefs.isAutoRestoreOnBootEnabled,
            adbGrantCommand = CscMuteManager.getAdbGrantPermissionCommand(app),
            adbDirectSetCommand = CscMuteManager.getAdbDirectCommand(true),
            adbCheckCommand = CscMuteManager.getAdbCheckCommand(),
            isShizukuInstalled = ShizukuManager.isShizukuInstalled(app),
            isShizukuRunning = ShizukuManager.isShizukuRunning(),
            hasShizukuPermission = ShizukuManager.hasPermission()
        )
    }

    fun refreshState() {
        val app = getApplication<Application>()
        ShizukuManager.init(app)
        val cscMuted = CscMuteManager.isCscShutterSoundMuted(app)
        val perm = CscMuteManager.hasWritePermission(app)
        val autoRestore = prefs.isAutoRestoreOnBootEnabled
        val shizukuInstalled = ShizukuManager.isShizukuInstalled(app)
        val shizukuRunning = ShizukuManager.isShizukuRunning()
        val shizukuPerm = ShizukuManager.hasPermission()

        _uiState.update { current ->
            current.copy(
                isCscMuted = cscMuted,
                hasCscPermission = perm,
                isAutoRestoreOnBoot = autoRestore,
                adbGrantCommand = CscMuteManager.getAdbGrantPermissionCommand(app),
                adbDirectSetCommand = CscMuteManager.getAdbDirectCommand(true),
                adbCheckCommand = CscMuteManager.getAdbCheckCommand(),
                isShizukuInstalled = shizukuInstalled,
                isShizukuRunning = shizukuRunning,
                hasShizukuPermission = shizukuPerm
            )
        }
    }

    /**
     * Shizuku를 통한 원클릭 셔터음 무음화 토글 (PC 불필요)
     */
    fun toggleMuteViaShizuku(enableMute: Boolean) {
        if (!ShizukuManager.isShizukuRunning()) {
            _uiState.update {
                it.copy(
                    errorMessage = "Shizuku 서비스가 실행 중이지 않습니다. 아래 [Shizuku 열기]를 눌러 무선 디버깅을 시작해 주세요."
                )
            }
            return
        }

        if (!ShizukuManager.hasPermission()) {
            ShizukuManager.requestPermission()
            return
        }

        viewModelScope.launch {
            val result = ShizukuManager.setCscMuteViaShizuku(enableMute)
            result.onSuccess {
                prefs.shouldMuteOnBoot = enableMute
                refreshState()
                val cmd = if (enableMute) "settings put system csc_pref_camera_forced_shuttersound_key 0" else "settings put system csc_pref_camera_forced_shuttersound_key 1"
                _uiState.update {
                    it.copy(
                        infoMessage = if (enableMute) {
                            "⚡ Shizuku로 ADB 명령어 전달 완료: 셔터음 무음화 성공!\n[$cmd]"
                        } else {
                            "🔔 Shizuku로 ADB 명령어 전달 완료: 셔터음 기본 소리로 복원되었습니다."
                        },
                        errorMessage = null
                    )
                }
            }.onFailure { error ->
                refreshState()
                _uiState.update {
                    it.copy(errorMessage = "Shizuku 적용 실패: ${error.message}", infoMessage = null)
                }
            }
        }
    }

    fun requestShizukuPermission() {
        ShizukuManager.requestPermission()
    }

    fun openShizukuApp(context: Context) {
        ShizukuManager.openShizukuOrStore(context)
    }

    fun openDeveloperOptions(context: Context) {
        ShizukuManager.openDeveloperOptions(context)
    }

    /**
     * 시스템 일반 CSC 토글
     */
    fun toggleCscMute(enableMute: Boolean) {
        val app = getApplication<Application>()
        if (ShizukuManager.hasPermission()) {
            // Shizuku 권한이 있으면 Shizuku로 자동 실행
            toggleMuteViaShizuku(enableMute)
            return
        }

        if (!CscMuteManager.hasWritePermission(app)) {
            _uiState.update {
                it.copy(
                    errorMessage = "보안 설정 변경 권한이 필요합니다. 아래 [Shizuku 원클릭 무음 적용] 또는 ADB 가이드를 이용해 주세요."
                )
            }
            return
        }

        viewModelScope.launch {
            val result = CscMuteManager.setCscShutterSoundMuted(app, enableMute)
            result.onSuccess {
                prefs.shouldMuteOnBoot = enableMute
                refreshState()
                _uiState.update {
                    it.copy(
                        infoMessage = if (enableMute) {
                            "카메라 셔터음 무음화가 활성화되었습니다. (진동/무음 시 무음)"
                        } else {
                            "카메라 셔터음이 기본 상태(소리 발생)로 복원되었습니다."
                        },
                        errorMessage = null
                    )
                }
            }.onFailure { error ->
                refreshState()
                _uiState.update {
                    it.copy(errorMessage = "설정 변경 실패: ${error.message}", infoMessage = null)
                }
            }
        }
    }

    fun setAutoRestoreOnBoot(enabled: Boolean) {
        prefs.isAutoRestoreOnBootEnabled = enabled
        _uiState.update { it.copy(isAutoRestoreOnBoot = enabled) }
    }

    fun dismissMessages() {
        _uiState.update { it.copy(infoMessage = null, errorMessage = null) }
    }
}
