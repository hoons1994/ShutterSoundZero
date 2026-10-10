package io.github.hoons1994.shuttersoundzero.ui.main

internal enum class HomeStatus {
    APPLYING,
    READY,
    REAPPLY_REQUIRED,
    STATE_UNKNOWN,
    SETUP_REQUIRED
}

internal fun resolveHomeStatus(uiState: MainUiState): HomeStatus = when {
    uiState.isCscChangeInProgress -> HomeStatus.APPLYING
    uiState.isCscMuted == true && uiState.hasCscPermission -> HomeStatus.READY
    uiState.isCscMuted == null && uiState.hasCscPermission -> HomeStatus.STATE_UNKNOWN
    uiState.hasCscPermission -> HomeStatus.REAPPLY_REQUIRED
    else -> HomeStatus.SETUP_REQUIRED
}
