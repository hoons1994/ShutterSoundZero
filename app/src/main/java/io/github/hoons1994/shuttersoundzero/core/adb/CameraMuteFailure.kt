package io.github.hoons1994.shuttersoundzero.core.adb

import androidx.annotation.StringRes
import io.github.hoons1994.shuttersoundzero.R

/** Actionable failure categories shared by home, settings and the quick-settings tile. */
enum class CameraMuteFailure(@StringRes val titleRes: Int, @StringRes val messageRes: Int) {
    LOCAL_NETWORK_PERMISSION(
        R.string.failure_permission_title,
        R.string.failure_permission_body
    ),
    DISCOVERY(
        R.string.failure_discovery_title,
        R.string.failure_discovery_body
    ),
    CONNECTION(
        R.string.failure_connection_title,
        R.string.failure_connection_body
    ),
    APPLY(
        R.string.failure_apply_title,
        R.string.failure_apply_body
    );

    val canReconnect: Boolean get() = this == DISCOVERY || this == CONNECTION

    companion object {
        fun from(error: Throwable?): CameraMuteFailure = when (error) {
            is LocalNetworkPermissionRequiredException -> LOCAL_NETWORK_PERMISSION
            is AdbServiceNotFoundException -> DISCOVERY
            is AdbConnectionFailedException -> CONNECTION
            else -> APPLY
        }
    }
}
