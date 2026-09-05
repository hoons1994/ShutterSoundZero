package com.charmingcolor.shuttersoundzero

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.charmingcolor.shuttersoundzero.data.PreferencesRepository
import com.charmingcolor.shuttersoundzero.security.AppLockAuthenticator
import com.charmingcolor.shuttersoundzero.security.AppLockSession
import com.charmingcolor.shuttersoundzero.theme.ShutterSoundZeroTheme
import com.charmingcolor.shuttersoundzero.ui.lock.AppLockScreen

class MainActivity : ComponentActivity() {

    private lateinit var prefs: PreferencesRepository
    private var isAppUnlocked by mutableStateOf(true)
    private var authenticationInProgress = false
    private var autoPromptPending = false
    private var unlockErrorMessage by mutableStateOf<String?>(null)

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // 알림 권한 허용 여부 처리 (필요시 추가 콜백)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = PreferencesRepository.getInstance(this)
        prefs.ensureSoftwareUpdateBaseline(Build.FINGERPRINT)
        isAppUnlocked = !prefs.isAppLockEnabled || AppLockSession.isUnlocked
        autoPromptPending = prefs.isAppLockEnabled && !isAppUnlocked

        // 삼성 갤럭시 기기 여부 확인 - 갤럭시가 아닌 기기일 경우 안내 토스트 표시
        if (!com.charmingcolor.shuttersoundzero.core.CscMuteManager.isSamsungDevice()) {
            android.widget.Toast.makeText(
                this,
                "⚠️ 이 앱은 삼성 갤럭시 전용 앱입니다. 다른 제조사 기기에서는 사용할 수 없습니다.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }

        if (!prefs.isAppLockEnabled) {
            requestNotificationPermissionIfNeeded()
        }

        enableEdgeToEdge()
        setContent {
            ShutterSoundZeroTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (prefs.isAppLockEnabled && !isAppUnlocked) {
                        AppLockScreen(
                            onUnlockClick = ::requestAppUnlock,
                            onExitClick = ::finishAndRemoveTask,
                            errorMessage = unlockErrorMessage
                        )
                    } else {
                        MainNavigation()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (
            ::prefs.isInitialized &&
            autoPromptPending &&
            prefs.isAppLockEnabled &&
            !isAppUnlocked &&
            !authenticationInProgress
        ) {
            autoPromptPending = false
            window.decorView.post {
                if (!isFinishing && prefs.isAppLockEnabled && !isAppUnlocked) {
                    requestAppUnlock()
                }
            }
        }
    }

    override fun onStop() {
        if (
            ::prefs.isInitialized &&
            prefs.isAppLockEnabled &&
            !authenticationInProgress &&
            !isChangingConfigurations
        ) {
            AppLockSession.lock()
            isAppUnlocked = false
            autoPromptPending = true
            unlockErrorMessage = null
        }
        super.onStop()
    }

    private fun requestAppUnlock() {
        if (!prefs.isAppLockEnabled) {
            AppLockSession.unlock()
            isAppUnlocked = true
            return
        }
        if (authenticationInProgress) return

        unlockErrorMessage = null
        if (!AppLockAuthenticator.canAuthenticate(this)) {
            unlockErrorMessage = "기기에 지문 또는 PIN·패턴·비밀번호를 설정한 뒤 다시 시도해 주세요."
            return
        }

        authenticationInProgress = true
        AppLockAuthenticator.authenticate(
            activity = this,
            title = "ShutterSoundZero 잠금 해제",
            subtitle = "지문 또는 화면 잠금으로 확인해 주세요.",
            onSuccess = {
                authenticationInProgress = false
                unlockErrorMessage = null
                AppLockSession.unlock()
                isAppUnlocked = true
                requestNotificationPermissionIfNeeded()
            },
            onCancelled = {
                authenticationInProgress = false
            },
            onError = {
                authenticationInProgress = false
                unlockErrorMessage = "인증을 완료할 수 없습니다. 잠금 방식을 확인한 뒤 다시 시도해 주세요."
            }
        )
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
