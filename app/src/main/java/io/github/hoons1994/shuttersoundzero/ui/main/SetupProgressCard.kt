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
    val applyComplete = uiState.isCscMuted == true

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
            "페어링 연결 화면을 찾지 못했습니다. Wi-Fi와 무선 디버깅을 확인하고 [페어링 코드로 기기 페어링] 화면을 다시 열어 둔 채 시도해 주세요."
        SetupIssue.PAIRING_CODE ->
            "숫자 6자리를 정확히 입력해 주세요. 연결 화면의 코드가 바뀌었다면 새 코드를 사용해 주세요."
        SetupIssue.PAIRING_CONNECTION ->
            "기기에 연결하지 못했습니다. 페어링 창이 닫혔거나 연결 정보가 바뀌었을 수 있습니다. 창을 다시 열고 새 6자리 코드로 시도해 주세요."
        SetupIssue.PAIRING_TIMEOUT ->
            "기기 응답 시간이 초과되었습니다. Wi-Fi와 무선 디버깅을 확인한 뒤 페어링 창을 다시 열고 새 코드로 시도해 주세요."
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
                text = "1회 설정 진행",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (issue == null) {
                    "지금 필요한 단계만 따라가면 됩니다."
                } else {
                    "문제가 생긴 단계부터 다시 진행해 주세요."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (issue == SetupIssue.LOCAL_NETWORK_PERMISSION) {
                Text(
                    text = "Android 17에서 기기를 찾으려면 로컬 네트워크 권한이 필요합니다. 앱 설정에서 권한을 허용한 뒤 1회 설정을 다시 시작해 주세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    lineHeight = 18.sp
                )
                OutlinedButton(onClick = onOpenAppSettings, shape = RoundedCornerShape(12.dp)) {
                    Text("앱 권한 설정 열기")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            SetupStepRow(
                number = 1,
                title = "개발자 옵션 켜기",
                subtitle = "휴대전화 정보 → 소프트웨어 정보 → 빌드번호를 7번 누릅니다. 화면 잠금 인증이 필요할 수 있습니다.",
                state = step1State,
                actionLabel = if (developerOptionsComplete) null else "휴대전화 정보 열기",
                onAction = onOpenSoftwareInfo
            )
            SetupStepRow(
                number = 2,
                title = if (uiState.isWirelessDebuggingEnabled == null) {
                    stringResource(R.string.wireless_debugging_state_unknown_title)
                } else "무선 디버깅 켜기",
                subtitle = if (uiState.isWirelessDebuggingEnabled == null) {
                    stringResource(R.string.wireless_debugging_state_unknown_guidance)
                } else "[1회 설정 시작]을 누르면 개발자 옵션의 무선 디버깅 화면을 엽니다.",
                state = step2State
            )
            SetupStepRow(
                number = 3,
                title = "6자리 코드 입력",
                subtitle = "상단 알림의 [코드 입력]에서 화면에 보이는 숫자 6자리를 입력합니다.",
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
                title = "카메라 무음 적용",
                subtitle = "연결에 성공하면 앱이 자동으로 적용하고 마무리합니다.",
                state = step4State,
                errorText = if (issue == SetupIssue.CAMERA_APPLY) {
                    "기기 연결은 됐지만 카메라 설정을 적용하지 못했습니다. 무선 디버깅을 켠 상태에서 다시 시도해 주세요."
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
                text = "간략한 팝업을 사용 중인가요?",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "ShutterSoundZero만 [자세한 팝업]으로 바꾸면 6자리 [코드 입력] 버튼을 바로 사용할 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("알림 팝업 설정 열기")
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
        StepVisualState.COMPLETE -> "완료"
        StepVisualState.ERROR -> errorText ?: "이 단계를 다시 확인해 주세요."
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
                        StepVisualState.COMPLETE -> "✓"
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
