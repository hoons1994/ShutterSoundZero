package io.github.hoons1994.shuttersoundzero.ui.onboarding

import androidx.compose.ui.res.stringResource
import io.github.hoons1994.shuttersoundzero.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FirstRunNoticeDialog(onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                text = stringResource(R.string.first_run_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.first_run_purpose),
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )
                Text(
                    text = stringResource(R.string.first_run_wireless),
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )
                Text(
                    text = stringResource(R.string.first_run_updates),
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )
                Text(
                    text = stringResource(R.string.first_run_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.first_run_confirm))
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}
