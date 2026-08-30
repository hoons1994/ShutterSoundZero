package com.hoons.shuttersoundzero.ui.dialog

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hoons.shuttersoundzero.theme.BrandBlueLight

@Composable
fun WirelessPairingDialog(
    detectedPairingPort: Int?,
    isPairing: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onPair: (port: Int, code: String) -> Unit
) {
    val context = LocalContext.current
    var pairingCode by remember { mutableStateOf("") }
    var portInput by remember(detectedPairingPort) {
        mutableStateOf(detectedPairingPort?.toString() ?: "")
    }

    Dialog(
        onDismissRequest = { if (!isPairing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 상단 헤더
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "무선 페어링 코드 입력",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    )
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isPairing
                    ) {
                        Text("닫기", fontWeight = FontWeight.SemiBold)
                    }
                }

                Text(
                    text = "PC 연결 없이 개발자 옵션의 6자리 페어링 코드로 셔터음을 무음화합니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                // 1단계: 개발자 옵션
                StepCard(
                    stepNumber = "1",
                    title = "무선 디버깅 켜기",
                    description = "Wi-Fi에 연결된 상태에서 개발자 옵션의 [무선 디버깅]을 활성화해 주세요."
                ) {
                    OutlinedButton(
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                            } catch (e: Exception) {
                                context.startActivity(Intent(Settings.ACTION_SETTINGS))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("개발자 옵션 바로가기")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 2단계: 페어링 코드 화면 진입
                StepCard(
                    stepNumber = "2",
                    title = "페어링 코드로 기기 페어링",
                    description = "[무선 디버깅] 글자를 누르고 [페어링 코드로 기기 페어링]을 터치합니다.\n(창이 닫히지 않도록 팝업 화면이나 분할 화면으로 열어두면 편리합니다)"
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 3단계: 코드 및 포트 입력
                StepCard(
                    stepNumber = "3",
                    title = "페어링 코드 & 포트 입력",
                    description = "화면에 표시된 6자리 페어링 코드와 포트 5자리를 입력해 주세요."
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = pairingCode,
                            onValueChange = { if (it.length <= 6) pairingCode = it },
                            label = { Text("6자리 페어링 코드") },
                            placeholder = { Text("예: 123456") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = portInput,
                            onValueChange = { if (it.length <= 5) portInput = it },
                            label = {
                                Text(
                                    if (detectedPairingPort != null) "페어링 포트 (mDNS 자동 감지됨)"
                                    else "페어링 포트 (IP 뒤 5자리)"
                                )
                            },
                            placeholder = { Text("예: 38492") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 페어링 실행 버튼
                Button(
                    onClick = {
                        val port = portInput.toIntOrNull()
                        if (port != null && pairingCode.isNotBlank()) {
                            onPair(port, pairingCode)
                        }
                    },
                    enabled = !isPairing && pairingCode.length >= 6 && portInput.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlueLight)
                ) {
                    if (isPairing) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("페어링 및 무음화 적용 중...")
                    } else {
                        Text(
                            text = "페어링 및 무음 설정 적용",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCard(
    stepNumber: String,
    title: String,
    description: String,
    content: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(BrandBlueLight, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stepNumber,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (content != null) {
                Spacer(modifier = Modifier.height(10.dp))
                content()
            }
        }
    }
}
