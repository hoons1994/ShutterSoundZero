package io.github.hoons1994.shuttersoundzero.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.hoons1994.shuttersoundzero.AppDependencies
import io.github.hoons1994.shuttersoundzero.camera.ChangeCameraMute
import io.github.hoons1994.shuttersoundzero.core.adb.CameraMuteFailure
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
    val appliedMutedState: Boolean? = null,
    val failure: CameraMuteFailure? = null
)

/** Keeps user-started CSC changes alive while the settings destination leaves composition. */
internal class CameraSettingsOperationViewModel(
    application: Application,
    private val changeCameraMute: ChangeCameraMute
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, AppDependencies.changeCameraMute(application))
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
                        mute && cleanup.isSuccess -> "카메라 무음 설정을 다시 적용했습니다."
                        !mute && cleanup.isSuccess -> "카메라 셔터음을 기본 상태로 복원했습니다."
                        mute -> "카메라 무음 설정은 적용했습니다. 무선 디버깅은 기기 설정에서 직접 꺼 주세요."
                        else -> "카메라 셔터음은 복원했습니다. 무선 디버깅은 기기 설정에서 직접 꺼 주세요."
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

}
