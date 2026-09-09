package com.charmingcolor.shuttersoundzero.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class HelpQuestion(val id: String, val question: String, val answer: String)

private val helpQuestions = listOf(
    HelpQuestion(
        "csc",
        "ShutterSoundZero는 정확히 무엇을 바꾸나요?",
        "삼성 갤럭시의 시스템 설정에 있는 카메라 셔터음 강제 값을 변경합니다. 앱을 적용하면 강제 셔터음이 해제되어 휴대전화의 소리·진동·무음 모드에 따라 카메라 촬영음이 동작합니다. 따라서 휴대전화가 소리 모드라면 촬영음이 들릴 수 있습니다."
    ),
    HelpQuestion(
        "wireless_debugging",
        "왜 무선 디버깅이 필요한가요?",
        "카메라 셔터음 강제 값은 일반 앱이 바로 변경할 수 없는 시스템 영역에 있습니다. ShutterSoundZero는 무선 디버깅을 통해 이 기기의 로컬 ADB에 연결해 최초 권한 연동을 하고, 카메라 값을 실제로 변경할 때 셸 명령을 실행합니다. 설정이 끝난 뒤에는 계속 켜둘 필요가 없습니다."
    ),
    HelpQuestion(
        "software_update",
        "One UI·Android 업데이트 후 다시 설정해야 하나요?",
        "항상 다시 설정할 필요는 없습니다. 소프트웨어 업데이트 후 카메라 셔터음 값이 그대로 유지될 수도 있고 기본값으로 초기화될 수도 있습니다. 앱은 실제 상태를 확인해 그대로 유지되면 아무 작업도 요구하지 않습니다. 값만 초기화되고 권한이 남아 있으면 [카메라 무음 다시 적용], 권한까지 사라졌다면 [1회 설정 시작]을 안내합니다."
    ),
    HelpQuestion(
        "reboot",
        "휴대전화를 재부팅하면 설정이 유지되나요?",
        "일반적인 재부팅에서는 카메라 셔터음 설정이 유지됩니다. ShutterSoundZero는 혹시 모를 상태 변화를 확인하기 위해 재부팅 후 실제 셔터음 설정값을 확인하며, 그대로 유지되어 있으면 아무 작업도 하지 않습니다."
    ),
    HelpQuestion(
        "app_update",
        "ShutterSoundZero 앱을 업데이트하면 다시 연동해야 하나요?",
        "정상적인 앱 업데이트에서는 기존 권한 연동과 앱 설정이 유지됩니다. 앱 업데이트 직후에도 기존 보안 설정 권한이 실제로 유지됐는지 확인하며, 예상과 다르게 권한이 사라진 경우에만 [1회 설정 시작]을 다시 진행하도록 안내합니다."
    ),
    HelpQuestion(
        "reapply_vs_setup",
        "[카메라 무음 다시 적용]과 [1회 설정 시작]은 무엇이 다른가요?",
        "기존 권한 연동은 남아 있는데 카메라 셔터음 값만 기본 상태로 돌아온 경우에는 [카메라 무음 다시 적용]만 하면 됩니다. WRITE_SECURE_SETTINGS 권한 자체가 없거나 사용자가 권한 연동을 해제했다면 [1회 설정 시작]으로 기기 연동부터 다시 진행해야 합니다."
    ),
    HelpQuestion(
        "notifications",
        "알림은 언제 표시되나요?",
        "One UI·Android 업데이트 후 카메라 셔터음 설정을 다시 적용해야 할 때, 또는 앱 업데이트 후 기존 권한 연동에 이상이 발견됐을 때처럼 사용자의 조치가 필요한 경우에 안내합니다. 정상 상태라면 별도의 조치 알림을 표시하지 않습니다."
    ),
    HelpQuestion(
        "developer_options",
        "개발자 옵션과 무선 디버깅을 계속 켜둬야 하나요?",
        "아닙니다. 최초 연동이 끝난 뒤에는 무선 디버깅을 꺼도 이미 부여된 권한은 유지됩니다. 카메라 무음을 다시 적용하거나 원래대로 복원하는 등 시스템 값을 실제로 변경할 때만 잠시 켜면 됩니다. 작업이 끝나면 앱이 무선 디버깅을 다시 끄려고 시도합니다."
    )
)

@Composable
fun SettingsHelpSection() {
    var expandedQuestionId by remember { mutableStateOf<String?>(null) }

    Text(
        text = "도움말",
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.1.sp
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, bottom = 6.dp)
    )

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            helpQuestions.forEachIndexed { index, item ->
                HelpAccordionItem(
                    item = item,
                    expanded = expandedQuestionId == item.id,
                    onToggle = {
                        expandedQuestionId = if (expandedQuestionId == item.id) null else item.id
                    }
                )
                if (index != helpQuestions.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun HelpAccordionItem(item: HelpQuestion, expanded: Boolean, onToggle: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = item.question,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "답변 접기" else "답변 펼치기",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (expanded) {
            Text(
                text = item.answer,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 19.sp
            )
        }
    }
}
