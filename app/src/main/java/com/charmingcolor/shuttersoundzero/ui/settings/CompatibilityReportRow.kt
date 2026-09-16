package com.charmingcolor.shuttersoundzero.ui.settings

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.charmingcolor.shuttersoundzero.compatibility.CompatibilityReportBuilder
import com.charmingcolor.shuttersoundzero.compatibility.CompatibilityReportEmail

@Composable
fun CompatibilityReportRow() {
    val context = LocalContext.current
    // Do not save consent or restore a pending launch after process recreation.
    var pendingDraft by remember { mutableStateOf<CompatibilityReportEmail.Draft?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }

    fun dismissDraft() {
        pendingDraft = null
        emailError = null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                emailError = null
                pendingDraft = CompatibilityReportEmail.prepare(CompatibilityReportBuilder.build(context))
            }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "호환성 데이터 제출",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "제출될 기기·앱 정보를 확인한 뒤 이메일로 간편 제보",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 19.sp
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "호환성 데이터 제출 정보 보기",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    pendingDraft?.let { draft ->
        AlertDialog(
            onDismissRequest = { dismissDraft() },
            title = { Text("호환성 데이터를 제출하시겠습니까?") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "아래 내용을 개발자에게 이메일로 제보합니다. 예를 누르면 이메일 작성 화면이 열리며, 실제 전송은 이메일 앱에서 보내기를 눌러야 완료됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 19.sp
                    )
                    SelectionContainer {
                        Text(
                            text = "받는 사람: ${CompatibilityReportEmail.RECIPIENT}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        text = "제목: ${draft.subject}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "제출될 정보",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    SelectionContainer {
                        Text(
                            text = draft.body,
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 19.sp
                        )
                    }
                    Text(
                        text = "앱이 메일에 넣지 않는 정보",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "계정 정보, 전화번호, 사진·미디어, 위치, IMEI, Android ID, 일련번호, 전체 빌드 지문, Wi-Fi 정보, ADB 페어링 코드·키, 진단 로그",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 19.sp
                    )
                    Text(
                        text = "이메일 주소는 수신자에게 전달되며, 메일 계정의 표시 이름·자동 서명도 함께 전달될 수 있습니다. 보내기 전에 이메일 앱에서 확인해 주세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 19.sp
                    )
                    Text(
                        text = "기기·앱 상태는 검토 후 공개 GitHub 호환성 문서에 수동 반영될 수 있습니다. 이메일 주소·표시 이름·서명은 문서에 공개하지 않습니다. 현재 설정 상태만으로 실제 촬영음이나 전체 호환성을 검증한 것은 아닙니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 19.sp
                    )
                    emailError?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Ignore a stale second click after this draft was dismissed.
                        if (pendingDraft !== draft) return@TextButton
                        emailError = null
                        when (CompatibilityReportEmail.open(context, draft)) {
                            CompatibilityReportEmail.OpenResult.OPENED -> dismissDraft()
                            CompatibilityReportEmail.OpenResult.NO_EMAIL_APP -> {
                                emailError = "이메일을 보낼 수 있는 앱을 찾지 못했습니다. 이메일 앱을 설치하거나 설정한 뒤 다시 시도해 주세요."
                            }
                            CompatibilityReportEmail.OpenResult.BLOCKED -> {
                                emailError = "기기 정책으로 이메일 작성 화면을 열 수 없습니다. 이메일 앱과 기기 설정을 확인해 주세요."
                            }
                        }
                    }
                ) {
                    Text("예")
                }
            },
            dismissButton = {
                TextButton(onClick = { dismissDraft() }) {
                    Text("아니오")
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}
