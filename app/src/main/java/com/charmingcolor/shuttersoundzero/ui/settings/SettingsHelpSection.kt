package com.charmingcolor.shuttersoundzero.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.charmingcolor.shuttersoundzero.diagnostics.DiagnosticLogger
import com.charmingcolor.shuttersoundzero.diagnostics.DiagnosticReportBuilder
import com.charmingcolor.shuttersoundzero.ui.notification.PairingNotificationHelper

private data class HelpQuestion(val id: String, val question: String, val answer: String)

private val helpQuestions = listOf(
    HelpQuestion(
        "camera_behavior",
        "카메라 무음 설정은 어떻게 동작하나요?",
        "카메라가 휴대전화의 소리 상태를 따르도록 설정합니다. 진동·무음 모드에서는 촬영음이 나지 않고, 소리 모드에서는 촬영음이 들릴 수 있습니다. 다만 소리 모드여도 설정의 [소리 및 진동 → 음량 → 시스템] 음량이 0이면 촬영음이 들리지 않을 수 있습니다."
    ),
    HelpQuestion(
        "wireless_debugging",
        "왜 무선 디버깅을 잠깐 켜야 하나요?",
        "처음 기기를 연결하거나 카메라 설정을 바꿀 때만 시스템 설정에 접근하기 위해 필요합니다. 설정이 끝나면 계속 켜둘 필요가 없으며, 앱은 작업이 끝난 뒤 무선 디버깅을 다시 끄려고 시도합니다."
    ),
    HelpQuestion(
        "software_update",
        "One UI·Android 업데이트 후 다시 설정해야 하나요?",
        "대부분은 그대로 사용할 수 있습니다. 업데이트 후 카메라 설정만 초기화되면 [카메라 무음 다시 적용]만 하면 되고, 기기 연결까지 풀린 경우에만 [1회 설정 시작]을 다시 안내합니다. 정상 상태라면 아무 작업도 요구하지 않습니다."
    ),
    HelpQuestion(
        "reboot",
        "휴대전화를 재부팅하면 설정이 유지되나요?",
        "일반적인 재부팅에서는 설정이 유지됩니다. 앱은 재부팅 후 실제 상태를 확인하고, 다시 설정이 필요한 경우에만 안내합니다."
    ),
    HelpQuestion(
        "app_update",
        "앱을 업데이트하면 다시 연결해야 하나요?",
        "정상적인 앱 업데이트에서는 기존 연결과 앱 설정이 유지됩니다. 예상과 다르게 연결 권한이 사라진 경우에만 [1회 설정 시작]을 다시 안내합니다."
    ),
    HelpQuestion(
        "reapply_vs_setup",
        "[다시 적용]과 [1회 설정 시작]은 무엇이 다른가요?",
        "기기 연결은 남아 있고 카메라 설정만 풀린 경우에는 [다시 적용]만 하면 됩니다. 기기 연결 자체가 없을 때만 [1회 설정 시작]으로 처음 연결부터 진행합니다."
    ),
    HelpQuestion(
        "notifications",
        "알림은 언제 표시되나요?",
        "사용자가 조치해야 할 때만 안내합니다. 예를 들어 소프트웨어 업데이트 뒤 카메라 설정을 다시 적용해야 하거나, 앱 업데이트 뒤 기존 연결에 이상이 발견된 경우입니다. 정상 상태에서는 별도 조치 알림을 표시하지 않습니다."
    ),
    HelpQuestion(
        "legal",
        "촬영 시 주의할 점이 있나요?",
        "이 앱은 정숙이 필요한 장소나 반려동물·아기 촬영처럼 정당한 편의를 위한 도구입니다. 타인의 의사에 반하는 불법촬영, 성적 수치심을 유발하는 촬영, 사생활 침해 목적으로 사용할 수 없으며 모든 촬영 행위에 대한 법적 책임은 사용자 본인에게 있습니다."
    )
)

@Composable
fun SettingsHelpSection() {
    val context = LocalContext.current
    var expandedQuestionId by remember { mutableStateOf<String?>(null) }
    var diagnosticReport by remember { mutableStateOf<DiagnosticReportBuilder.Report?>(null) }
    var userDescription by rememberSaveable { mutableStateOf("") }

    SectionLabel("도움말 및 지원")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            NotificationPopupSettingsRow(
                onClick = { PairingNotificationHelper.openNotificationSettings(context) }
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                thickness = 0.5.dp
            )

            helpQuestions.forEach { item ->
                HelpAccordionItem(
                    item = item,
                    expanded = expandedQuestionId == item.id,
                    onToggle = {
                        expandedQuestionId = if (expandedQuestionId == item.id) null else item.id
                    }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    thickness = 0.5.dp
                )
            }

            SupportRow(onClick = {
                diagnosticReport = DiagnosticReportBuilder.build(context)
            })
        }
    }

    diagnosticReport?.let { report ->
        AlertDialog(
            onDismissRequest = { diagnosticReport = null },
            title = { Text("오류 신고") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "문제가 생기기 전에 한 작업을 적고, 아래 진단 정보를 확인한 뒤 이메일로 보낼 수 있습니다. 페어링 코드, Wi-Fi 이름·주소, 기기 식별 번호, 연결용 키는 진단 기록에 저장하지 않습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = userDescription,
                        onValueChange = { userDescription = it.take(2000) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("문제가 발생하기 전에 한 작업") },
                        placeholder = {
                            Text("예: 소프트웨어 업데이트 후 1회 설정을 다시 진행했는데 오류가 표시됨")
                        },
                        minLines = 3,
                        maxLines = 6
                    )

                    Text(
                        text = "전송될 진단 정보",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    SelectionContainer {
                        Text(
                            text = report.diagnosticText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    TextButton(
                        onClick = {
                            DiagnosticLogger.clear(context)
                            diagnosticReport = DiagnosticReportBuilder.build(context)
                            Toast.makeText(context, "진단 로그를 삭제했습니다.", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("진단 로그 지우기")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        sendDiagnosticEmail(context, report, userDescription)
                    }
                ) {
                    Text("이메일로 보내기")
                }
            },
            dismissButton = {
                TextButton(onClick = { diagnosticReport = null }) {
                    Text("닫기")
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
private fun NotificationPopupSettingsRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "알림 팝업 자세히 보기",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "간략한 팝업을 사용 중이라면 ShutterSoundZero만 자세한 팝업으로 바꿔 6자리 코드 입력을 더 편하게 사용할 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 19.sp
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "앱 알림 설정 열기",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SupportRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "문제가 해결되지 않나요?",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "진단 정보를 확인하고 오류 신고 이메일 보내기",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "오류 신고 열기",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun sendDiagnosticEmail(
    context: Context,
    report: DiagnosticReportBuilder.Report,
    userDescription: String
) {
    val body = buildString {
        appendLine("안녕하세요. ShutterSoundZero 사용 중 오류가 발생해 진단 정보를 보냅니다.")
        appendLine()
        appendLine("[사용자 설명]")
        appendLine(userDescription.trim().ifBlank { "작성하지 않음" })
        appendLine()
        appendLine(report.diagnosticText)
    }
    val mailUri = Uri.parse(
        "mailto:${Uri.encode(DiagnosticReportBuilder.SUPPORT_EMAIL)}" +
            "?subject=${Uri.encode(report.subject)}" +
            "&body=${Uri.encode(body)}"
    )

    try {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SENDTO, mailUri),
                "오류 신고 메일 보내기"
            )
        )
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(
            context,
            "이메일을 보낼 수 있는 앱을 찾지 못했습니다.",
            Toast.LENGTH_LONG
        ).show()
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.1.sp
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, bottom = 6.dp)
    )
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
                imageVector = if (expanded) {
                    Icons.Default.KeyboardArrowUp
                } else {
                    Icons.Default.KeyboardArrowDown
                },
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
