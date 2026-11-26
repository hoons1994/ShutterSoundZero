package io.github.hoons1994.shuttersoundzero.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hoons1994.shuttersoundzero.R
import io.github.hoons1994.shuttersoundzero.data.SetupIssue
import io.github.hoons1994.shuttersoundzero.data.isSetupTargetApplied
import io.github.hoons1994.shuttersoundzero.theme.BrandBlueLight
import io.github.hoons1994.shuttersoundzero.theme.StatusGreen
import io.github.hoons1994.shuttersoundzero.ui.notification.PairingNotificationHelper

internal enum class StepVisualState {
    COMPLETE,
    CURRENT,
    ERROR,
    PENDING
}

@Composable
internal fun SetupProgressCard(
    uiState: MainUiState,
    onOpenSoftwareInfo: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    val context = LocalContext.current
    val issue = uiState.setupIssue
    val developerOptionsComplete = uiState.isDeveloperOptionsEnabled
    val wirelessComplete = developerOptionsComplete && uiState.isWirelessDebuggingEnabled != null &&
        (uiState.isWirelessDebuggingEnabled == true || (issue != null && issue != SetupIssue.LOCAL_NETWORK_PERMISSION))
    val pairingComplete = uiState.hasCscPermission || issue == SetupIssue.CAMERA_APPLY
    val targetMuted = uiState.pendingPairingTarget ?: true
    val applyComplete = isSetupTargetApplied(
        uiState.hasCscPermission, uiState.isCscMuted, targetMuted
    )

    val step1State = if (developerOptionsComplete) StepVisualState.COMPLETE else StepVisualState.CURRENT
    val step2State = when {
        developerOptionsComplete && uiState.isWirelessDebuggingEnabled == null -> StepVisualState.ERROR
        wirelessComplete -> StepVisualState.COMPLETE
        developerOptionsComplete -> StepVisualState.CURRENT
        else -> StepVisualState.PENDING
    }
    val step3State = when {
        !developerOptionsComplete -> StepVisualState.PENDING
        issue != null && issue != SetupIssue.CAMERA_APPLY && issue != SetupIssue.LOCAL_NETWORK_PERMISSION -> StepVisualState.ERROR
        pairingComplete -> StepVisualState.COMPLETE
        wirelessComplete -> StepVisualState.CURRENT
        else -> StepVisualState.PENDING
    }
    val step4State = when {
        !developerOptionsComplete -> StepVisualState.PENDING
        issue == SetupIssue.CAMERA_APPLY -> StepVisualState.ERROR
        applyComplete -> StepVisualState.COMPLETE
        pairingComplete -> StepVisualState.CURRENT
        else -> StepVisualState.PENDING
    }

    val pairingErrorText = when (issue) {
        SetupIssue.PAIRING_DISCOVERY ->
            stringResource(R.string.step_discovery_failed)
        SetupIssue.PAIRING_CODE ->
            stringResource(R.string.step_code_invalid)
        SetupIssue.PAIRING_CONNECTION ->
            stringResource(R.string.step_connection_failed)
        SetupIssue.PAIRING_IDENTITY ->
            stringResource(R.string.pairing_help_identity)
        SetupIssue.PAIRING_TLS ->
            stringResource(R.string.pairing_help_tls)
        SetupIssue.PAIRING_AUTHENTICATION ->
            stringResource(R.string.pairing_help_authentication)
        SetupIssue.PAIRING_TIMEOUT ->
            stringResource(R.string.step_timeout)
        else -> null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding),
        shape = RoundedCornerShape(CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.pairing_title_setup_in_progress),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (issue == null) {
                    stringResource(R.string.setup_follow_steps)
                } else {
                    stringResource(R.string.setup_resume_step)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (issue == SetupIssue.LOCAL_NETWORK_PERMISSION) {
                Text(
                    text = stringResource(R.string.setup_allow_network),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    lineHeight = 18.sp
                )
                OutlinedButton(onClick = onOpenAppSettings, shape = RoundedCornerShape(12.dp)) {
                    Text(stringResource(R.string.setup_permissions_open))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            SetupStepRow(
                number = 1,
                title = stringResource(R.string.setup_enable_developer),
                subtitle = stringResource(R.string.setup_build_number),
                state = step1State,
                actionLabel = if (developerOptionsComplete) null else stringResource(R.string.setup_open_device_info),
                onAction = onOpenSoftwareInfo
            )
            SetupStepRow(
                number = 2,
                title = if (uiState.isWirelessDebuggingEnabled == null) {
                    stringResource(R.string.wireless_debugging_state_unknown_title)
                } else stringResource(R.string.wireless_enable),
                subtitle = if (uiState.isWirelessDebuggingEnabled == null) {
                    stringResource(R.string.wireless_debugging_state_unknown_guidance)
                } else stringResource(R.string.setup_open_wireless),
                state = step2State
            )
            SetupStepRow(
                number = 3,
                title = stringResource(R.string.pairing_title_code_entry),
                subtitle = stringResource(R.string.setup_use_code),
                state = step3State,
                errorText = pairingErrorText
            )
            if (wirelessComplete && !pairingComplete && issue != SetupIssue.LOCAL_NETWORK_PERMISSION) {
                NotificationPopupStyleHint(
                    onOpenSettings = {
                        PairingNotificationHelper.openNotificationSettings(context)
                    }
                )
            }
            SetupStepRow(
                number = 4,
                title = stringResource(if (targetMuted) R.string.setup_apply_camera else R.string.settings_restore),
                subtitle = stringResource(R.string.setup_apply_automatic),
                state = step4State,
                errorText = if (issue == SetupIssue.CAMERA_APPLY) {
                    stringResource(R.string.setup_code_accepted)
                } else {
                    null
                }
            )
        }
    }
}

@Composable
private fun NotificationPopupStyleHint(
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 42.dp, top = 2.dp, bottom = 6.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.setup_popup_question),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.setup_popup_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.setup_open_popup))
            }
        }
    }
}

@Composable
private fun SetupStepRow(
    number: Int,
    title: String,
    subtitle: String,
    state: StepVisualState,
    errorText: String? = null,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    val markerColor = when (state) {
        StepVisualState.COMPLETE -> StatusGreen
        StepVisualState.CURRENT -> BrandBlueLight
        StepVisualState.ERROR -> MaterialTheme.colorScheme.error
        StepVisualState.PENDING -> MaterialTheme.colorScheme.surfaceVariant
    }
    val titleColor = when (state) {
        StepVisualState.ERROR -> MaterialTheme.colorScheme.error
        StepVisualState.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    val detailText = when (state) {
        StepVisualState.COMPLETE -> stringResource(R.string.status_done)
        StepVisualState.ERROR -> errorText ?: stringResource(R.string.step_check)
        else -> subtitle
    }
    val detailColor = when (state) {
        StepVisualState.COMPLETE -> StatusGreen
        StepVisualState.ERROR -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Card(
            modifier = Modifier.size(30.dp),
            shape = CircleShape,
            colors = CardDefaults.cardColors(containerColor = markerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = when (state) {
                        StepVisualState.COMPLETE -> "V"
                        StepVisualState.ERROR -> "!"
                        else -> number.toString()
                    },
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (state == StepVisualState.PENDING) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        Color.White
                    }
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = titleColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = detailText,
                style = MaterialTheme.typography.bodySmall,
                color = detailColor,
                lineHeight = 18.sp
            )
            if (actionLabel != null && state == StepVisualState.CURRENT) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}
