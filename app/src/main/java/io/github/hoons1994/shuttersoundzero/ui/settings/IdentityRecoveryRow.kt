package io.github.hoons1994.shuttersoundzero.ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** A separate confirmation is required; an ordinary retry never discards ADB credentials. */
@Composable
internal fun IdentityRecoveryRow(enabled: Boolean, onConfirmed: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    TextButton(enabled = enabled, onClick = { confirm = true }) {
        Text("연결 정보 재설정")
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("연결 정보를 새로 만들까요?") },
            text = {
                Text("기기 날짜와 시간을 확인하고 다시 연결해도 인증 정보 오류가 반복될 때 사용하세요. " +
                    "새 코드로 연결을 시작할 때 기존 ADB 연결 키를 지우고 새로 만듭니다. " +
                    "6자리 코드로 다시 페어링해야 하며, 연결 후 이전에 요청한 촬영음 설정을 적용합니다. 앱 설정은 유지됩니다.")
            },
            confirmButton = {
                TextButton(enabled = enabled, onClick = { confirm = false; onConfirmed() }) {
                    Text("재설정 후 다시 연결")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("취소") }
            }
        )
    }
}
