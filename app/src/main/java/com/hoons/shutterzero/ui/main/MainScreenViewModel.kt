package com.hoons.shutterzero.ui.main

import android.app.Application
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hoons.shutterzero.core.CscMuteManager
import com.hoons.shutterzero.core.adb.StandaloneAdbManager
import com.hoons.shutterzero.data.PreferencesRepository
import com.hoons.shutterzero.ui.notification.PairingNotificationHelper
import io.github.muntashirakon.adb.android.AdbMdns
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
    val errorMessage: String? = null
)

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PreferencesRepository.getInstance(application)
    private val adbManager = StandaloneAdbManager.getInstance(application)

    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var pairingMdns: AdbMdns? = null
    private var connectMdns: AdbMdns? = null

    init {
        if (prefs.lastFirmwareFingerprint == null) {
            prefs.lastFirmwareFingerprint = android.os.Build.FINGERPRINT
        }
        refreshState()
    }

    override fun onCleared() {
        super.onCleared()
        stopMdnsDiscovery()
    }

    private fun createInitialState(): MainUiState {
        val app = getApplication<Application>()
        return MainUiState(
            isCscMuted = CscMuteManager.isCscShutterSoundMuted(app),
            hasCscPermission = CscMuteManager.hasWritePermission(app),
            isAutoRestoreOnBoot = prefs.isAutoRestoreOnBootEnabled,
            isFirmwareUpdateCheckEnabled = prefs.isFirmwareUpdateCheckEnabled,
            adbGrantCommand = CscMuteManager.getAdbGrantPermissionCommand(app),
            adbDirectSetCommand = CscMuteManager.getAdbDirectCommand(true),
            adbCheckCommand = CscMuteManager.getAdbCheckCommand()
        )
    }

    fun refreshState() {
        val app = getApplication<Application>()
        val cscMuted = CscMuteManager.isCscShutterSoundMuted(app)
        val perm = CscMuteManager.hasWritePermission(app)
        val autoRestore = prefs.isAutoRestoreOnBootEnabled
        val firmwareCheck = prefs.isFirmwareUpdateCheckEnabled

        _uiState.update { current ->
            current.copy(
                isCscMuted = cscMuted,
                hasCscPermission = perm,
                isAutoRestoreOnBoot = autoRestore,
                isFirmwareUpdateCheckEnabled = firmwareCheck,
                adbGrantCommand = CscMuteManager.getAdbGrantPermissionCommand(app),
                adbDirectSetCommand = CscMuteManager.getAdbDirectCommand(true),
                adbCheckCommand = CscMuteManager.getAdbCheckCommand()
            )
        }
    }

    fun startMdnsDiscovery() {
        try {
            stopMdnsDiscovery()
            pairingMdns = adbManager.startMdnsDiscovery(AdbMdns.SERVICE_TYPE_TLS_PAIRING) { _, port ->
                _uiState.update { it.copy(detectedPairingPort = port) }
                try {
                    PairingNotificationHelper.showPairingNotification(getApplication(), port)
                } catch (_: Exception) {}
            }
            connectMdns = adbManager.startMdnsDiscovery(AdbMdns.SERVICE_TYPE_TLS_CONNECT) { _, port ->
                _uiState.update { it.copy(detectedConnectPort = port) }
            }
        } catch (e: Exception) {
            // mDNS might not be supported on some network configurations
        }
    }

    fun startNotificationPairing(context: Context) {
        startMdnsDiscovery()
        PairingNotificationHelper.showPairingNotification(context, _uiState.value.detectedPairingPort)
        openDeveloperOptions(context)
        _uiState.update {
            it.copy(
                infoMessage = "상단바에 페어링 알림이 등록되었습니다! [페어링 코드로 기기 페어링] 화면을 띄운 뒤 상단바를 내려 6자리 코드를 입력해 주세요."
            )
        }
    }

    fun cancelNotificationPairing(context: Context) {
        stopMdnsDiscovery()
        PairingNotificationHelper.cancelNotification(context)
    }

    fun stopMdnsDiscovery() {
        try {
            pairingMdns?.stop()
            connectMdns?.stop()
        } catch (_: Exception) {}
        pairingMdns = null
        connectMdns = null
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
                val errorMsg = pairResult.exceptionOrNull()?.message ?: "페어링에 실패했습니다."
                _uiState.update {
                    it.copy(isWirelessPairingInProgress = false, wirelessPairingError = errorMsg)
                }
                onComplete(false)
                return@launch
            }

            val connectPort = _uiState.value.detectedConnectPort ?: port
            val muteResult = adbManager.applyCameraMuteViaAdb(connectPort)

            if (muteResult.isSuccess) {
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

    fun openDeveloperOptions(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        } catch (e: Exception) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    /**
     * 시스템 일반 CSC 토글
     */
    fun toggleCscMute(enableMute: Boolean) {
        val app = getApplication<Application>()

        if (!CscMuteManager.hasWritePermission(app)) {
            val connectPort = _uiState.value.detectedConnectPort
            if (connectPort != null) {
                viewModelScope.launch {
                    val result = adbManager.applyCameraMuteViaAdb(connectPort)
                    if (result.isSuccess) {
                        prefs.shouldMuteOnBoot = enableMute
                        refreshState()
                        _uiState.update {
                            it.copy(
                                infoMessage = if (enableMute) "셔터음 무음화가 적용되었습니다." else "셔터음이 기본 소리로 복원되었습니다.",
                                errorMessage = null
                            )
                        }
                        return@launch
                    }
                }
            }

            _uiState.update {
                it.copy(
                    errorMessage = "보안 설정 변경 권한이 필요합니다. 아래 [스마트폰 단독 자체 페어링] 또는 PC ADB 가이드를 이용해 주세요."
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

    fun setFirmwareUpdateCheck(enabled: Boolean) {
        prefs.isFirmwareUpdateCheckEnabled = enabled
        _uiState.update { it.copy(isFirmwareUpdateCheckEnabled = enabled) }
    }

    fun dismissMessages() {
        _uiState.update { it.copy(infoMessage = null, errorMessage = null) }
    }
}
