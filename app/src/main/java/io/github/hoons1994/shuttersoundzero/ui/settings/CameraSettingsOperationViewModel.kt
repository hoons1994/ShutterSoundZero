package io.github.hoons1994.shuttersoundzero.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.hoons1994.shuttersoundzero.core.CscMuteManager
import io.github.hoons1994.shuttersoundzero.core.CscStateVerifier
import io.github.hoons1994.shuttersoundzero.core.DeveloperOptionsManager
import io.github.hoons1994.shuttersoundzero.core.adb.LocalNetworkPermissionRequiredException
import io.github.hoons1994.shuttersoundzero.core.adb.StandaloneAdbManager
import io.github.hoons1994.shuttersoundzero.data.PreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class CameraSettingsOperationUiState(
    val isReapplyInProgress: Boolean = false,
    val isRestoreInProgress: Boolean = false,
    val completionId: Long = 0,
    val resultMessage: String? = null,
    val appliedMutedState: Boolean? = null
)

/** Keeps user-started CSC changes alive while the settings destination leaves composition. */
internal class CameraSettingsOperationViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(CameraSettingsOperationUiState())
    val uiState = _uiState.asStateFlow()

    fun setCameraMute(mute: Boolean) {
        synchronized(this) {
            if (_uiState.value.isReapplyInProgress || _uiState.value.isRestoreInProgress) return
            _uiState.update {
                it.copy(
                    isReapplyInProgress = mute,
                    isRestoreInProgress = !mute,
                    resultMessage = null,
                    appliedMutedState = null
                )
            }
        }

        viewModelScope.launch {
            var resultMessage = "카메라 셔터음 설정을 변경하지 못했습니다. 다시 시도해 주세요."
            var appliedMutedState: Boolean? = null
            try {
                val context = getApplication<Application>()
                val result = StandaloneAdbManager.getInstance(context).setCameraMute(mute)
                val stateApplied = result.isSuccess && CscStateVerifier.waitFor(mute) {
                    CscMuteManager.isCscShutterSoundMuted(context)
                }

                if (stateApplied) {
                    PreferencesRepository.getInstance(context).shouldMuteOnBoot = mute
                    appliedMutedState = mute
                    val cleanup = DeveloperOptionsManager.disableWirelessDebugging(context)
                    resultMessage = when {
                        mute && cleanup.isSuccess -> "카메라 무음 설정을 다시 적용했습니다."
                        !mute && cleanup.isSuccess -> "카메라 셔터음을 기본 상태로 복원했습니다."
                        mute -> "카메라 무음 설정은 적용했습니다. 무선 디버깅은 기기 설정에서 직접 꺼 주세요."
                        else -> "카메라 셔터음은 복원했습니다. 무선 디버깅은 기기 설정에서 직접 꺼 주세요."
                    }
                } else {
                    val error = result.exceptionOrNull()
                    resultMessage = if (error is LocalNetworkPermissionRequiredException) {
                        LOCAL_NETWORK_PERMISSION_MESSAGE
                    } else if (mute) {
                        "다시 적용하지 못했습니다. 무선 디버깅이 켜져 있는지 확인한 뒤 다시 시도해 주세요."
                    } else {
                        "카메라 셔터음을 복원하지 못했습니다. 무선 디버깅이 켜져 있는지 확인한 뒤 다시 시도해 주세요."
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                resultMessage = if (error is LocalNetworkPermissionRequiredException) {
                    LOCAL_NETWORK_PERMISSION_MESSAGE
                } else if (mute) {
                    "다시 적용하지 못했습니다. 무선 디버깅이 켜져 있는지 확인한 뒤 다시 시도해 주세요."
                } else {
                    "카메라 셔터음을 복원하지 못했습니다. 무선 디버깅이 켜져 있는지 확인한 뒤 다시 시도해 주세요."
                }
            } finally {
                _uiState.update {
                    it.copy(
                        isReapplyInProgress = false,
                        isRestoreInProgress = false,
                        completionId = it.completionId + 1,
                        resultMessage = resultMessage,
                        appliedMutedState = appliedMutedState
                    )
                }
            }
        }
    }

    fun consumeCompletion() {
        _uiState.update {
            it.copy(resultMessage = null, appliedMutedState = null)
        }
    }

    private companion object {
        const val LOCAL_NETWORK_PERMISSION_MESSAGE =
            "카메라 설정을 바꾸려면 로컬 네트워크 권한이 필요합니다. 앱 설정에서 권한을 허용한 뒤 다시 시도해 주세요."
    }
}
