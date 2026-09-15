package com.charmingcolor.shuttersoundzero.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.charmingcolor.shuttersoundzero.compatibility.CompatibilityReportBuilder

@Composable
fun CompatibilityReportRow() {
    val context = LocalContext.current
    var pendingReport by remember { mutableStateOf<CompatibilityReportBuilder.Report?>(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                pendingReport = CompatibilityReportBuilder.build(context)
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
                text = "제출될 기기·앱 정보를 먼저 확인한 뒤 GitHub 호환성 문서에 제보",
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

    pendingReport?.let { report ->
        AlertDialog(
            onDismissRequest = { pendingReport = null },
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
                        text = "아래 정보만 공개 GitHub 이슈로 제출됩니다. GitHub에서 최종 제출하면 문서 반영용 변경이 자동 생성되며, 저장소의 필수 검증을 통과해 병합된 뒤 호환성 문서의 사용자 제출 데이터에 등록됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 19.sp
                    )
                    Text(
                        text = "제출될 정보",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    SelectionContainer {
                        Text(
                            text = report.displayText(),
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 19.sp
                        )
                    }
                    Text(
                        text = "제출되지 않는 정보",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "이름·계정, 전화번호, 사진·미디어, 위치, IMEI, Android ID, 일련번호, Wi-Fi 정보, ADB 페어링 코드·키",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 19.sp
                    )
                    Text(
                        text = "GitHub 계정으로 제출하는 경우 GitHub 사용자명은 공개 이슈 작성자 정보로 표시될 수 있습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 19.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (openCompatibilityIssue(context, report)) {
                            pendingReport = null
                        }
                    }
                ) {
                    Text("예")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingReport = null }) {
                    Text("아니오")
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

private fun openCompatibilityIssue(
    context: Context,
    report: CompatibilityReportBuilder.Report
): Boolean {
    return try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, CompatibilityReportBuilder.buildIssueUri(report))
        )
        true
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(
            context,
            "GitHub 제출 화면을 열 수 있는 앱을 찾지 못했습니다.",
            Toast.LENGTH_LONG
        ).show()
        false
    }
}
