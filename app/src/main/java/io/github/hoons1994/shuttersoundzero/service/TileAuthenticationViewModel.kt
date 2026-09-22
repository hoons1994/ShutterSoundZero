package io.github.hoons1994.shuttersoundzero.service

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal class TileAuthenticationViewModel(application: Application) : AndroidViewModel(application) {
    private val flow = TileAuthenticationFlow()
    val state: StateFlow<TileAuthenticationFlowState> = flow.state

    fun beginAuthentication(): Boolean = flow.beginAuthentication()

    fun runAuthorizedAction(targetMuted: Boolean) {
        if (!flow.beginAction()) return
        viewModelScope.launch {
            try {
                CameraMuteTileAction.executeTarget(getApplication(), targetMuted)
            } finally {
                flow.complete()
            }
        }
    }

    fun cancelAuthentication() = flow.cancel()

    fun authenticationUnavailable() = flow.authenticationUnavailable()

    fun authenticationError() = flow.authenticationError()
}
