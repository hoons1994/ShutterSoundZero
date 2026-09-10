package com.charmingcolor.shuttersoundzero.diagnostics

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.charmingcolor.shuttersoundzero.theme.ShutterSoundZeroTheme

class DiagnosticReportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShutterSoundZeroTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DiagnosticReportScreen(
                        onBack = ::finish,
                        onSendEmail = ::sendDiagnosticEmail,
                        onClearLogs = {
                            DiagnosticLogger.clear(this)
                            Toast.makeText(this, "진단 로그를 삭제했습니다.", Toast.LENGTH_SHORT).show()
                        },
                        buildReport = { DiagnosticReportBuilder.build(this) }
                    )
                }
            }
        }
    }

    private fun sendDiagnosticEmail(report: DiagnosticReportBuilder.Report, userDescription: String) {
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
        val intent = Intent(Intent.ACTION_SENDTO, mailUri)
        if (intent.resolveActivity(packageManager) == null) {
            Toast.makeText(
                this,
                "이메일을 보낼 수 있는 앱을 찾지 못했습니다.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        startActivity(Intent.createChooser(intent, "오류 신고 메일 보내기"))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiagnosticReportScreen(
    onBack: () -> Unit,
    onSendEmail: (DiagnosticReportBuilder.Report, String) -> Unit,
    onClearLogs: () -> Unit,
    buildReport: () -> DiagnosticReportBuilder.Report
) {
    var report by remember { mutableStateOf(buildReport()) }
    var userDescription by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("오류 신고", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "오류 상황과 최근 진단 정보를 확인한 뒤 ${DiagnosticReportBuilder.SUPPORT_EMAIL}로 이메일을 보낼 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = "개인정보 보호를 위해 페어링 코드, IP 주소, Wi-Fi 이름, IMEI·일련번호, ADB 키는 진단 로그에 기록하지 않습니다. 아래 내용은 메일 앱이 열리기 전에 직접 확인할 수 있습니다.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = userDescription,
                onValueChange = { userDescription = it.take(2000) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("문제가 발생하기 전에 한 작업") },
                placeholder = {
                    Text("예: 소프트웨어 업데이트 후 1회 설정을 다시 진행했는데 페어링 후 오류가 표시됨")
                },
                minLines = 3,
                maxLines = 6
            )

            Text(
                text = "전송될 진단 정보",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                SelectionContainer {
                    Text(
                        text = report.diagnosticText,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Button(
                onClick = { onSendEmail(report, userDescription) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("이메일로 오류 신고")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = {
                        onClearLogs()
                        report = buildReport()
                    }
                ) {
                    Text("진단 로그 지우기")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
