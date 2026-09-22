package io.github.hoons1994.shuttersoundzero.service

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import io.github.hoons1994.shuttersoundzero.data.PreferencesRepository
import io.github.hoons1994.shuttersoundzero.security.AppLockAuthenticator
import io.github.hoons1994.shuttersoundzero.theme.ShutterSoundZeroTheme
import kotlinx.coroutines.launch

/** 앱 잠금 사용자의 빠른 설정 타일 동작을 1회 인증하는 전용 화면. */
class TileAuthenticationActivity : ComponentActivity() {
    private var flowStarted = false
    private var statusMessage by mutableStateOf("빠른 설정 타일을 인증하는 중입니다.")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            ShutterSoundZeroTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = statusMessage,
                            modifier = Modifier.padding(top = 20.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        if (flowStarted) return
        flowStarted = true
        authenticateAndRun()
    }

    private fun authenticateAndRun() {
        val prefs = PreferencesRepository.getInstance(this)
        if (!prefs.isAppLockEnabled) {
            runAuthorizedAction()
            return
        }

        if (!AppLockAuthenticator.canAuthenticate(this)) {
            Toast.makeText(
                this,
                "기기에 지문 또는 PIN·패턴·비밀번호를 설정한 뒤 다시 시도해 주세요.",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        AppLockAuthenticator.authenticate(
            activity = this,
            title = "빠른 설정 타일 인증",
            subtitle = "카메라 셔터음 설정을 변경하려면 본인 확인해 주세요.",
            onSuccess = ::runAuthorizedAction,
            onCancelled = ::finish,
            onError = {
                Toast.makeText(
                    this,
                    "인증을 완료할 수 없습니다. 잠금 방식을 확인한 뒤 다시 시도해 주세요.",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        )
    }

    private fun runAuthorizedAction() {
        statusMessage = "카메라 셔터음 설정을 변경하는 중입니다."
        lifecycleScope.launch {
            CameraMuteTileAction.execute(applicationContext)
            finish()
        }
    }
}
