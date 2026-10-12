package io.github.hoons1994.shuttersoundzero.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.hoons1994.shuttersoundzero.update.UpdateCheckException
import io.github.hoons1994.shuttersoundzero.update.UpdateCheckFailure
import io.github.hoons1994.shuttersoundzero.update.UpdateCheckResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class AppUpdateUiState(
    val isChecking: Boolean = false,
    val result: UpdateCheckResult? = null,
    val failure: UpdateCheckFailure? = null
)

internal class AppUpdateViewModel(
    private val currentVersion: String,
    private val check: suspend (String) -> UpdateCheckResult
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppUpdateUiState())
    val uiState = _uiState.asStateFlow()

    fun checkForUpdates() {
        if (_uiState.value.isChecking) return
        _uiState.value = AppUpdateUiState(isChecking = true)
        viewModelScope.launch {
            try {
                _uiState.value = AppUpdateUiState(result = check(currentVersion))
            } catch (error: CancellationException) {
                _uiState.value = AppUpdateUiState()
                throw error
            } catch (error: Exception) {
                _uiState.value = AppUpdateUiState(
                    failure = (error as? UpdateCheckException)?.reason ?: UpdateCheckFailure.NETWORK
                )
            }
        }
    }

    companion object {
        fun factory(currentVersion: String, check: suspend (String) -> UpdateCheckResult) =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(AppUpdateViewModel::class.java))
                    @Suppress("UNCHECKED_CAST")
                    return AppUpdateViewModel(currentVersion, check) as T
                }
            }
    }
}
