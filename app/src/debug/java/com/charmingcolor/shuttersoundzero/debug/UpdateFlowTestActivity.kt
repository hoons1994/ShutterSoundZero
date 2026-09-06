package com.charmingcolor.shuttersoundzero.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.charmingcolor.shuttersoundzero.update.AppUpdateManager
import kotlinx.coroutines.launch

class UpdateFlowTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                UpdateFlowTestScreen()
            }
        }
    }

    @Composable
    private fun UpdateFlowTestScreen() {
        val context = this
        val scope = rememberCoroutineScope()

        var updateInfo by remember { mutableStateOf<AppUpdateManager.UpdateInfo?>(null) }
        var verifiedUpdate by remember { mutableStateOf<AppUpdateManager.VerifiedUpdate?>(null) }
        var status by remember { mutableStateOf("테스트 릴리즈를 확인해 주세요.") }
        var progress by remember { mutableStateOf(0) }
        var busy by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "업데이트 설치 흐름 테스트",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "이 화면은 Debug APK에만 포함됩니다. 실제 GitHub 테스트 릴리즈에서 APK를 내려받아 운영 코드와 동일한 SHA-256·패키지·버전·서명 인증서 검증을 수행한 뒤 Android 패키지 설치 화면을 엽니다.",
                style = MaterialTheme.typography.bodyMedium
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("상태", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(status)
                    if (busy && progress > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("다운로드 $progress%")
                    }
                }
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        progress = 0
                        verifiedUpdate = null
                        try {
                            val update = DebugUpdateTestManager.fetchTestUpdate()
                            updateInfo = update
                            status = "테스트 업데이트 v${update.versionName}을 찾았습니다. 이제 APK를 앱 내부에서 다운로드할 수 있습니다."
                        } catch (error: Throwable) {
                            status = error.message ?: "테스트 릴리즈 확인에 실패했습니다."
                        } finally {
                            busy = false
                        }
                    }
                }
            ) {
                Text("1. 테스트 업데이트 확인")
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && updateInfo != null,
                onClick = {
                    val update = updateInfo ?: return@Button
                    scope.launch {
                        busy = true
                        progress = 0
                        verifiedUpdate = null
                        status = "APK를 다운로드하고 검증하고 있습니다."
                        try {
                            val verified = AppUpdateManager.downloadAndVerify(
                                context = context,
                                update = update,
                                onProgress = { value -> progress = value }
                            )
                            verifiedUpdate = verified
                            status = "검증 완료: v${verified.versionName} (${verified.versionCode})\nSHA-256, 패키지명, 버전, 서명 인증서가 모두 일치합니다."
                        } catch (error: Throwable) {
                            status = error.message ?: "APK 다운로드 또는 검증에 실패했습니다."
                        } finally {
                            busy = false
                        }
                    }
                }
            ) {
                Text("2. 앱 내부에서 다운로드·검증")
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && verifiedUpdate != null,
                onClick = {
                    val verified = verifiedUpdate ?: return@Button
                    if (!AppUpdateManager.canRequestPackageInstalls(context)) {
                        status = "Android의 [이 출처 허용]을 켠 뒤 이 화면으로 돌아와 3번 버튼을 다시 눌러 주세요."
                        AppUpdateManager.openInstallPermissionSettings(context)
                    } else if (AppUpdateManager.launchInstaller(context, verified)) {
                        status = "Android 패키지 설치 화면을 열었습니다. [업데이트]를 눌러 실제 교체까지 확인해 주세요."
                    } else {
                        status = "Android 패키지 설치 화면을 열지 못했습니다."
                    }
                }
            ) {
                Text("3. 패키지 설치 앱 열기")
            }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                onClick = { finish() }
            ) {
                Text("닫기")
            }
        }
    }
}
