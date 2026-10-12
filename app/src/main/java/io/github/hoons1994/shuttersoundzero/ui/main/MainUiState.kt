package io.github.hoons1994.shuttersoundzero.ui.main

import io.github.hoons1994.shuttersoundzero.core.adb.CameraMuteFailure
import io.github.hoons1994.shuttersoundzero.data.SetupIssue

data class MainUiState(
    val isCscMuted: Boolean? = null,
    val hasCscPermission: Boolean = false,
    val isDeveloperOptionsEnabled: Boolean = false,
    val isWirelessDebuggingEnabled: Boolean? = null,
    val setupIssue: SetupIssue? = null,
    val infoMessage: String? = null,
    val errorMessage: String? = null,
    val showSwitchFailureHelp: Boolean = false,
    val cameraMuteFailure: CameraMuteFailure? = null,
    val isCscChangeInProgress: Boolean = false,
    val showWirelessDebuggingCleanupHelp: Boolean = false,
    val systemVolume: SystemVolumeUiState = SystemVolumeUiState()
)

data class SystemVolumeUiState(
    val current: Int? = null,
    val min: Int = 0,
    val max: Int = 0,
    val isFixed: Boolean = false,
    val error: String? = null,
    val isRingerMuted: Boolean = false,
    val isStreamMuted: Boolean = false,
    val requested: Int? = null
)

internal fun MainUiState.withWirelessDebuggingState(enabled: Boolean?): MainUiState = copy(
    isWirelessDebuggingEnabled = enabled,
    showSwitchFailureHelp = showSwitchFailureHelp && enabled == false,
    showWirelessDebuggingCleanupHelp = showWirelessDebuggingCleanupHelp && enabled != false
)
