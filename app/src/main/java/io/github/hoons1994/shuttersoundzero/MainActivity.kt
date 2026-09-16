package io.github.hoons1994.shuttersoundzero

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import io.github.hoons1994.shuttersoundzero.core.CscMuteManager
import io.github.hoons1994.shuttersoundzero.core.adb.LocalNetworkAccess
import io.github.hoons1994.shuttersoundzero.data.PreferencesRepository
import io.github.hoons1994.shuttersoundzero.security.AppLockAuthenticator
import io.github.hoons1994.shuttersoundzero.security.AppLockSession
import io.github.hoons1994.shuttersoundzero.theme.ShutterSoundZeroTheme
import io.github.hoons1994.shuttersoundzero.ui.lock.AppLockScreen
import io.github.hoons1994.shuttersoundzero.ui.onboarding.FirstRunNoticeDialog

class MainActivity : ComponentActivity() {

    private lateinit var prefs: PreferencesRepository
    private var appLockPreferenceListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var isAppUnlocked by mutableStateOf(true)
    private var authenticationInProgress = false
    private var autoPromptPending = false
    private var localNetworkPermissionRequestInProgress = false
    private var localNetworkPermissionDeniedThisSession = false
    private var unlockErrorMessage by mutableStateOf<String?>(null)
    private var showInitialNotice by mutableStateOf(false)

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            requestLocalNetworkPermissionForExistingLinkageIfNeeded()
        }

    private val requestLocalNetworkPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            localNetworkPermissionRequestInProgress = false
            localNetworkPermissionDeniedThisSession = !isGranted
            if (!isGranted) {
                android.widget.Toast.makeText(
                    this,
                    "Android 17에서 무선 ADB 재적용을 사용하려면 로컬 네트워크 권한이 필요합니다. [앱 설정]에서 허용해 주세요.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = PreferencesRepository.getInstance(this)
        appLockPreferenceListener = prefs.registerAppLockChangeListener {
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    updateWindowSecurity()
                }
            }
        }
        prefs.ensureInitialNoticeMigration()
        showInitialNotice = !prefs.hasSeenInitialNotice
        prefs.ensureSoftwareUpdateBaseline(Build.FINGERPRINT)
        isAppUnlocked = !prefs.isAppLockEnabled || AppLockSession.isUnlocked
        autoPromptPending = prefs.isAppLockEnabled && !isAppUnlocked
        updateWindowSecurity()

        // 삼성 갤럭시 기기 여부 확인 - 갤럭시가 아닌 기기일 경우 안내 토스트 표시
        if (!CscMuteManager.isSamsungDevice()) {
            android.widget.Toast.makeText(
                this,
                "⚠️ 이 앱은 삼성 갤럭시 전용 앱입니다. 다른 제조사 기기에서는 사용할 수 없습니다.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }

        if (!prefs.isAppLockEnabled && !showInitialNotice) {
            requestRuntimePermissionsIfNeeded()
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
                        Box(modifier = Modifier.fillMaxSize()) {
                            MainNavigation()
                            if (showInitialNotice) {
                                FirstRunNoticeDialog(
                                    onConfirm = {
                                        prefs.hasSeenInitialNotice = true
                                        showInitialNotice = false
                                        requestRuntimePermissionsIfNeeded()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateWindowSecurity()
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

        if (
            ::prefs.isInitialized &&
            (!prefs.isAppLockEnabled || isAppUnlocked) &&
            !authenticationInProgress
        ) {
            requestLocalNetworkPermissionForExistingLinkageIfNeeded()
        }
    }

    override fun onPause() {
        // 앱 잠금이 방금 켜진 경우에도 최근 앱 스냅샷을 만들기 전에 FLAG_SECURE를 반영한다.
        updateWindowSecurity()
        super.onPause()
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

    override fun onDestroy() {
        if (::prefs.isInitialized) {
            appLockPreferenceListener?.let(prefs::unregisterAppLockChangeListener)
        }
        appLockPreferenceListener = null
        super.onDestroy()
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
                if (!showInitialNotice) {
                    requestRuntimePermissionsIfNeeded()
                }
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

    private fun requestRuntimePermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestLocalNetworkPermissionForExistingLinkageIfNeeded()
        }
    }

    private fun requestLocalNetworkPermissionForExistingLinkageIfNeeded() {
        if (
            !::prefs.isInitialized ||
            localNetworkPermissionRequestInProgress ||
            localNetworkPermissionDeniedThisSession
        ) {
            return
        }

        val permissionGranted = LocalNetworkAccess.isGranted(this)
        val shouldRequest = LocalNetworkAccess.shouldRequestForExistingLinkage(
            sdkInt = Build.VERSION.SDK_INT,
            linkageExpected = CscMuteManager.hasWritePermission(this),
            permissionRevokedByUser = prefs.isPermissionRevokedByUser,
            permissionGranted = permissionGranted
        )
        if (!shouldRequest) return

        localNetworkPermissionRequestInProgress = true
        requestLocalNetworkPermissionLauncher.launch(LocalNetworkAccess.PERMISSION)
    }

    private fun updateWindowSecurity() {
        if (!::prefs.isInitialized) return
        if (prefs.isAppLockEnabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
