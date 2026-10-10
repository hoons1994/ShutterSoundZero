package io.github.hoons1994.shuttersoundzero.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
        HomeStatus.READY -> "카메라 무음 설정 완료"
        HomeStatus.REAPPLY_REQUIRED -> "카메라 무음 다시 적용 필요"
        HomeStatus.STATE_UNKNOWN -> stringResource(R.string.csc_state_unknown_title)
        HomeStatus.SETUP_REQUIRED -> if (hasSetupIssue) {
            "1회 설정을 다시 진행해 주세요"
        } else {
            "처음 한 번만 설정해 주세요"
        }
    }
    val subtitle = when (status) {
        HomeStatus.APPLYING -> "실제 적용 상태를 확인하고 있습니다. 완료될 때까지 기다려 주세요."
        HomeStatus.READY -> "진동·무음 모드에서 촬영음이 나지 않도록 설정되어 있습니다."
        HomeStatus.REAPPLY_REQUIRED -> when (isWirelessDebuggingEnabled) {
            true -> "앱 권한은 유지되어 있습니다. [다시 적용하기]를 눌러 주세요."
            false -> "앱 권한은 유지되어 있습니다. 무선 디버깅을 켠 뒤 [다시 적용하기]를 눌러 주세요."
            null -> stringResource(R.string.wireless_debugging_state_unknown_guidance)
        }
        HomeStatus.STATE_UNKNOWN -> stringResource(R.string.csc_state_unknown_guidance)
        HomeStatus.SETUP_REQUIRED -> if (hasSetupIssue) {
            "문제가 생긴 단계를 아래에 표시했습니다. 해당 단계부터 다시 진행하면 됩니다."
        } else {
            "4단계 안내에 따라 연결하면 이후에는 앱을 계속 열어둘 필요가 없습니다."
        }
    }
    val badgeText = when (status) {
        HomeStatus.APPLYING -> "처리 중"
        HomeStatus.READY -> "정상"
        HomeStatus.REAPPLY_REQUIRED -> "조치 필요"
        HomeStatus.STATE_UNKNOWN -> "확인 필요"
        HomeStatus.SETUP_REQUIRED -> if (hasSetupIssue) "확인 필요" else "설정 필요"
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
        HomeStatus.REAPPLY_REQUIRED -> "다시 적용하기"
        HomeStatus.STATE_UNKNOWN -> stringResource(R.string.csc_state_retry)
        HomeStatus.SETUP_REQUIRED -> if (hasSetupIssue) "1회 설정 다시 시작" else "1회 설정 시작"
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
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )

            if (status == HomeStatus.READY) {
                Text(
                    text = "앱을 종료해도 설정은 유지됩니다. 소리 모드에서는 시스템 음량에 따라 촬영음이 들릴 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                OutlinedButton(
                    onClick = onCameraAction,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("카메라 열어보기")
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
