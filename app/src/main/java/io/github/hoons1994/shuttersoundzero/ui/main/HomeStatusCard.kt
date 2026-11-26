package io.github.hoons1994.shuttersoundzero.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hoons1994.shuttersoundzero.R
import io.github.hoons1994.shuttersoundzero.data.SetupIssue
import io.github.hoons1994.shuttersoundzero.theme.BrandBlueLight
import io.github.hoons1994.shuttersoundzero.theme.StatusAmber
import io.github.hoons1994.shuttersoundzero.theme.StatusGreen

@Composable
internal fun StatusHeroCard(
    status: HomeStatus,
    setupIssue: SetupIssue?,
    isInProgress: Boolean,
    isWirelessDebuggingEnabled: Boolean?,
    onOpenWirelessDebugging: () -> Unit,
    onPrimaryAction: (() -> Unit)?,
    onCameraAction: () -> Unit
) {
    val hasSetupIssue = status == HomeStatus.SETUP_REQUIRED && setupIssue != null
    val title = when (status) {
        HomeStatus.APPLYING -> stringResource(R.string.camera_settings_applying)
        HomeStatus.READY -> stringResource(R.string.home_applied)
        HomeStatus.REAPPLY_REQUIRED -> stringResource(R.string.home_reapply_needed)
        HomeStatus.STATE_UNKNOWN -> stringResource(R.string.csc_state_unknown_title)
        HomeStatus.SETUP_REQUIRED -> if (setupIssue == SetupIssue.CAMERA_APPLY) {
            stringResource(R.string.home_retry_connection)
        } else if (hasSetupIssue) {
            stringResource(R.string.home_restart_setup)
        } else {
            stringResource(R.string.home_first_setup)
        }
    }
    val subtitle = when (status) {
        HomeStatus.APPLYING -> stringResource(R.string.home_wait_apply)
        HomeStatus.READY -> stringResource(R.string.home_ready_description)
        HomeStatus.REAPPLY_REQUIRED -> when (isWirelessDebuggingEnabled) {
            true -> stringResource(R.string.home_reapply_granted)
            false -> stringResource(R.string.home_reapply_enable)
            null -> stringResource(R.string.wireless_debugging_state_unknown_guidance)
        }
        HomeStatus.STATE_UNKNOWN -> stringResource(R.string.csc_state_unknown_guidance)
        HomeStatus.SETUP_REQUIRED -> if (hasSetupIssue) {
            stringResource(R.string.home_retry_step)
        } else {
            stringResource(R.string.home_connect_steps)
        }
    }
    val badgeText = when (status) {
        HomeStatus.APPLYING -> stringResource(R.string.home_busy)
        HomeStatus.READY -> stringResource(R.string.home_verified)
        HomeStatus.REAPPLY_REQUIRED -> stringResource(R.string.home_action_needed)
        HomeStatus.STATE_UNKNOWN -> stringResource(R.string.home_check_needed)
        HomeStatus.SETUP_REQUIRED -> if (hasSetupIssue) stringResource(R.string.home_check_needed) else stringResource(R.string.home_setup_needed)
    }
    val badgeColor = when (status) {
        HomeStatus.APPLYING -> MaterialTheme.colorScheme.primary
        HomeStatus.READY -> StatusGreen
        HomeStatus.REAPPLY_REQUIRED -> StatusAmber
        HomeStatus.STATE_UNKNOWN -> StatusAmber
        HomeStatus.SETUP_REQUIRED -> if (hasSetupIssue) StatusAmber else MaterialTheme.colorScheme.primary
    }
    val primaryLabel = when (status) {
        HomeStatus.APPLYING -> stringResource(R.string.camera_settings_applying)
        HomeStatus.READY -> null
        HomeStatus.REAPPLY_REQUIRED -> stringResource(R.string.home_reapply)
        HomeStatus.STATE_UNKNOWN -> stringResource(R.string.csc_state_retry)
        HomeStatus.SETUP_REQUIRED -> when {
            setupIssue == SetupIssue.CAMERA_APPLY -> stringResource(R.string.pairing_action_retry_apply)
            hasSetupIssue -> stringResource(R.string.home_restart)
            else -> stringResource(R.string.home_start)
        }
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
            modifier = Modifier.padding(if (status == HomeStatus.READY) 24.dp else 22.dp),
            verticalArrangement = Arrangement.spacedBy(if (status == HomeStatus.READY) 18.dp else 12.dp)
        ) {
            if (status == HomeStatus.READY) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        modifier = Modifier.size(42.dp)
                            .background(badgeColor.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = badgeColor, modifier = Modifier.size(22.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(badgeText, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = badgeColor)
                        Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            } else {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = badgeColor
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )

            if (status == HomeStatus.READY) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.padding(top = 2.dp).size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.home_ready_persistence),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
                Button(
                    onClick = onCameraAction,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Icon(painterResource(R.drawable.ic_camera), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(9.dp))
                    Text(stringResource(R.string.home_open_camera), fontWeight = FontWeight.Bold)
                }
            } else {
                Spacer(modifier = Modifier.height(2.dp))
                Button(
                    onClick = { onPrimaryAction?.invoke() },
                    enabled = !isInProgress,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlueLight)
                ) {
                    Text(
                        text = primaryLabel.orEmpty(),
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            if (status == HomeStatus.REAPPLY_REQUIRED || status == HomeStatus.APPLYING) {
                TextButton(
                    onClick = onOpenWirelessDebugging,
                    enabled = !isInProgress
                ) {
                    Text(
                        stringResource(
                            if (isWirelessDebuggingEnabled == false) R.string.main_reenable_wireless_debugging
                            else R.string.main_open_wireless_debugging_settings
                        )
                    )
                }
            }
        }
    }
}
