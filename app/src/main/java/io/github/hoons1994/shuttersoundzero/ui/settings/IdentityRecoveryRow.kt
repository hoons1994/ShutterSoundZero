package io.github.hoons1994.shuttersoundzero.ui.settings

import androidx.compose.ui.res.stringResource
import io.github.hoons1994.shuttersoundzero.R
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** A separate confirmation is required; an ordinary retry never discards ADB credentials. */
@Composable
internal fun IdentityRecoveryRow(enabled: Boolean, onConfirmed: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    TextButton(enabled = enabled, onClick = { confirm = true }) {
        Text(stringResource(R.string.recovery_reset))
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.recovery_confirm)) },
            text = {
                Text(stringResource(R.string.recovery_reset_detail))
            },
            confirmButton = {
                TextButton(enabled = enabled, onClick = { confirm = false; onConfirmed() }) {
                    Text(stringResource(R.string.recovery_reconnect_after_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}
