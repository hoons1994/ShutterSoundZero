package com.charmingcolor.shuttersoundzero.ui.onboarding

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
                text = "사용 전에 꼭 확인해 주세요",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "ShutterSoundZero는 삼성 갤럭시의 카메라 셔터음 강제 설정을 변경합니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )
                Text(
                    text = "• 처음 한 번 무선 디버깅으로 기기 연동이 필요합니다. 설정이 끝나면 무선 디버깅을 계속 켜둘 필요는 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )
                Text(
                    text = "• One UI·Android 업데이트나 재부팅 뒤 카메라 설정이 초기화될 수 있습니다. 매번 다시 설정하는 것은 아니며, 필요한 경우에만 앱이 알려드립니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )
                Text(
                    text = "자세한 원리와 상황별 조치는 설정 → 도움말에서 언제든 확인할 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
                Text(
                    text = "다음 단계에서 알림을 허용하면 다시 적용이나 권한 확인이 필요한 상황을 안내받을 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("확인하고 시작하기")
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}
