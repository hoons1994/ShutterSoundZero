package io.github.hoons1994.shuttersoundzero.ui.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.hoons1994.shuttersoundzero.AppDependencies
import io.github.hoons1994.shuttersoundzero.camera.ChangeCameraMute
import io.github.hoons1994.shuttersoundzero.core.DeveloperOptionsManager
import io.github.hoons1994.shuttersoundzero.core.adb.CameraMuteFailure
import io.github.hoons1994.shuttersoundzero.core.adb.LocalNetworkAccess
import io.github.hoons1994.shuttersoundzero.data.PreferencesRepository
import io.github.hoons1994.shuttersoundzero.security.AppLockSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class CameraAction { REAPPLY, RESTORE }

internal enum class SettingsPermissionRecovery {
    WAITING_REAPPLY, WAITING_RESTORE, READY_REAPPLY, READY_RESTORE, CONFIRM_RESTORE
}

internal data class CameraSettingsOperationUiState(
    val isReapplyInProgress: Boolean = false,
    val isRestoreInProgress: Boolean = false,
    val completionId: Long = 0,
    val resultMessage: String? = null,
    val appliedMutedState: Boolean? = null,
    val failure: CameraMuteFailure? = null,
    val requestedMutedState: Boolean = true
)

/** Keeps user-started CSC changes alive while the settings destination leaves composition. */
internal class CameraSettingsOperationViewModel(
    application: Application,
    private val changeCameraMute: ChangeCameraMute,
    private val savedStateHandle: SavedStateHandle,
    private val localNetworkGranted: (Context) -> Boolean = LocalNetworkAccess::isGranted,
    private val wirelessDebuggingState: (Context) -> Boolean? = DeveloperOptionsManager::readWirelessDebuggingEnabled
) : AndroidViewModel(application) {
    constructor(application: Application, savedStateHandle: SavedStateHandle) :
        this(application, AppDependencies.changeCameraMute(application), savedStateHandle)

    private val prefs = PreferencesRepository.getInstance(application)
    private val _uiState = MutableStateFlow(CameraSettingsOperationUiState())
    val uiState = _uiState.asStateFlow()
    val permissionRecovery = savedStateHandle.getStateFlow<SettingsPermissionRecovery?>(PERMISSION_RECOVERY, null)

    fun isLocalNetworkGranted(context: Context) = localNetworkGranted(context)

    fun isWirelessDebuggingEnabled(context: Context) = wirelessDebuggingState(context)

    fun preparePermissionRecovery(action: CameraAction) {
        savedStateHandle[PERMISSION_RECOVERY] = when (action) {
            CameraAction.REAPPLY -> SettingsPermissionRecovery.WAITING_REAPPLY
            CameraAction.RESTORE -> SettingsPermissionRecovery.WAITING_RESTORE
        }
    }

    fun resumePermissionRecovery() {
        if (prefs.isAppLockEnabled && !AppLockSession.isUnlocked) return
        val ready = when (permissionRecovery.value) {
            SettingsPermissionRecovery.WAITING_REAPPLY -> SettingsPermissionRecovery.READY_REAPPLY
            SettingsPermissionRecovery.WAITING_RESTORE -> SettingsPermissionRecovery.READY_RESTORE
            else -> return
        }
        savedStateHandle[PERMISSION_RECOVERY] = ready
    }

    fun takeReadyPermissionAction(): CameraAction? {
        if (prefs.isAppLockEnabled && !AppLockSession.isUnlocked) return null
        val action = when (permissionRecovery.value) {
            SettingsPermissionRecovery.READY_REAPPLY -> CameraAction.REAPPLY
            SettingsPermissionRecovery.READY_RESTORE -> CameraAction.RESTORE
            else -> return null
        }
        // Consume before starting an operation so multiple resume callbacks cannot repeat it.
        cancelPermissionRecovery()
        return action
    }

    fun requireRestoreConfirmation() {
        savedStateHandle[PERMISSION_RECOVERY] = SettingsPermissionRecovery.CONFIRM_RESTORE
    }

    fun cancelPermissionRecovery() {
        // Assign null rather than removing the key: existing StateFlow collectors stay attached.
        savedStateHandle[PERMISSION_RECOVERY] = null
    }

    fun setCameraMute(mute: Boolean) {
        synchronized(this) {
            if (_uiState.value.isReapplyInProgress || _uiState.value.isRestoreInProgress) return
            _uiState.update {
                it.copy(
                    isReapplyInProgress = mute,
                    requestedMutedState = mute,
                    isRestoreInProgress = !mute,
                    resultMessage = null,
                    appliedMutedState = null,
                    failure = null
                )
            }
        }

        viewModelScope.launch {
            var resultMessage = "카메라 셔터음 설정을 변경하지 못했습니다. 다시 시도해 주세요."
            var appliedMutedState: Boolean? = null
            var failure: CameraMuteFailure? = null
            try {
                val result = changeCameraMute(mute)

                if (result.isSuccess) {
                    appliedMutedState = mute
                    val cleanup = result.getOrThrow().wirelessCleanup
                    resultMessage = when {
                        mute && cleanup.isSuccess -> "카메라 설정값을 다시 적용했습니다. 실제 촬영음을 확인해 주세요."
                        !mute && cleanup.isSuccess -> "셔터음 강제 설정값을 기본 상태로 복원했습니다."
                        mute -> "카메라 설정값은 적용했습니다. 실제 촬영음을 확인하고 무선 디버깅은 직접 꺼 주세요."
                        else -> "셔터음 강제 설정값은 복원했습니다. 무선 디버깅은 직접 꺼 주세요."
                    }
                } else {
                    failure = CameraMuteFailure.from(result.exceptionOrNull())
                    resultMessage = failure.message
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failure = CameraMuteFailure.from(error)
                resultMessage = failure.message
            } finally {
                _uiState.update {
                    it.copy(
                        isReapplyInProgress = false,
                        isRestoreInProgress = false,
                        completionId = it.completionId + 1,
                        resultMessage = resultMessage,
                        appliedMutedState = appliedMutedState,
                        failure = failure
                    )
                }
            }
        }
    }

    fun consumeCompletion() {
        _uiState.update {
            it.copy(resultMessage = null, appliedMutedState = null, failure = null)
        }
    }

    private companion object {
        const val PERMISSION_RECOVERY = "settingsPermissionRecovery"
    }
}
