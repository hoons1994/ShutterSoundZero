package io.github.hoons1994.shuttersoundzero.ui.components

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
    reconnectLabel: String = "기기 다시 연결"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(failure.title) },
        text = { Text(failure.message) },
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
                    CameraMuteFailure.DISCOVERY -> "무선 디버깅 설정"
                    CameraMuteFailure.LOCAL_NETWORK_PERMISSION -> "앱 권한 설정"
                    CameraMuteFailure.APPLY -> "확인"
                })
            }
        },
        dismissButton = {
            if (failure.canReconnect) {
                TextButton(onClick = {
                    onDismiss()
                    if (failure == CameraMuteFailure.DISCOVERY) onReconnect() else onOpenSettings()
                }) {
                    Text(if (failure == CameraMuteFailure.DISCOVERY) reconnectLabel else "무선 디버깅 설정")
                }
            }
        }
    )
}
