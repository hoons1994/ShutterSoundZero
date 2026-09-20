package io.github.hoons1994.shuttersoundzero.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class TileAuthenticationFlowState {
    IDLE,
    AUTHENTICATING,
    RUNNING,
    COMPLETED,
    CANCELLED,
    AUTHENTICATION_UNAVAILABLE,
    AUTHENTICATION_ERROR
}

internal class TileAuthenticationFlow {
    private val mutableState = MutableStateFlow(TileAuthenticationFlowState.IDLE)
    val state: StateFlow<TileAuthenticationFlowState> = mutableState.asStateFlow()

    fun beginAuthentication(): Boolean = mutableState.compareAndSet(
        TileAuthenticationFlowState.IDLE,
        TileAuthenticationFlowState.AUTHENTICATING
    )

    fun beginAction(): Boolean {
        while (true) {
            val current = mutableState.value
            if (
                current != TileAuthenticationFlowState.IDLE &&
                current != TileAuthenticationFlowState.AUTHENTICATING
            ) {
                return false
            }
            if (mutableState.compareAndSet(current, TileAuthenticationFlowState.RUNNING)) {
                return true
            }
        }
    }

    fun complete() {
        mutableState.compareAndSet(
            TileAuthenticationFlowState.RUNNING,
            TileAuthenticationFlowState.COMPLETED
        )
    }

    fun cancel() {
        mutableState.compareAndSet(
            TileAuthenticationFlowState.AUTHENTICATING,
            TileAuthenticationFlowState.CANCELLED
        )
    }

    fun authenticationUnavailable() {
        mutableState.compareAndSet(
            TileAuthenticationFlowState.AUTHENTICATING,
            TileAuthenticationFlowState.AUTHENTICATION_UNAVAILABLE
        )
    }

    fun authenticationError() {
        mutableState.compareAndSet(
            TileAuthenticationFlowState.AUTHENTICATING,
            TileAuthenticationFlowState.AUTHENTICATION_ERROR
        )
    }
}
