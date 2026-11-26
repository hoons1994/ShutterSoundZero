package io.github.hoons1994.shuttersoundzero.ui.components

import androidx.compose.ui.res.stringResource
import io.github.hoons1994.shuttersoundzero.R
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import io.github.hoons1994.shuttersoundzero.core.adb.CameraMuteFailure

@Composable
internal fun CameraMuteFailureDialog(
    failure: CameraMuteFailure,
    onDismiss: () -> Unit,
    onReconnect: () -> Unit,
    onOpenSettings: () -> Unit,
    reconnectLabel: String = stringResource(R.string.recovery_reconnect)
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(failure.titleRes)) },
        text = { Text(stringResource(failure.messageRes)) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                when (failure) {
                    CameraMuteFailure.CONNECTION -> onReconnect()
                    CameraMuteFailure.DISCOVERY, CameraMuteFailure.LOCAL_NETWORK_PERMISSION -> onOpenSettings()
                    CameraMuteFailure.APPLY -> Unit
                }
            }) {
                Text(when (failure) {
                    CameraMuteFailure.CONNECTION -> reconnectLabel
                    CameraMuteFailure.DISCOVERY -> stringResource(R.string.action_wireless_settings)
                    CameraMuteFailure.LOCAL_NETWORK_PERMISSION -> stringResource(R.string.pairing_action_permission_settings)
                    CameraMuteFailure.APPLY -> stringResource(R.string.action_ok)
                })
            }
        },
        dismissButton = {
            if (failure.canReconnect) {
                TextButton(onClick = {
                    onDismiss()
                    if (failure == CameraMuteFailure.DISCOVERY) onReconnect() else onOpenSettings()
                }) {
                    Text(if (failure == CameraMuteFailure.DISCOVERY) reconnectLabel else stringResource(R.string.action_wireless_settings))
                }
            }
        }
    )
}
