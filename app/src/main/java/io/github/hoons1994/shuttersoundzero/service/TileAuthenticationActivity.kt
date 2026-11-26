package io.github.hoons1994.shuttersoundzero.service

import android.content.Context
import io.github.hoons1994.shuttersoundzero.data.applyLegacyLanguage
import androidx.compose.ui.res.stringResource
import io.github.hoons1994.shuttersoundzero.R
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.hoons1994.shuttersoundzero.data.PreferencesRepository
import io.github.hoons1994.shuttersoundzero.security.AppLockAuthenticator
import io.github.hoons1994.shuttersoundzero.ui.AppTheme
import kotlinx.coroutines.launch

/** 앱 잠금 사용자의 빠른 설정 타일 동작을 1회 인증하는 전용 화면. */
class TileAuthenticationActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TARGET_MUTED = "target_muted"
    }

    private lateinit var viewModel: TileAuthenticationViewModel
    private var authenticationRequest: AppLockAuthenticator.AuthenticationRequest? = null
    private var statusMessage by mutableIntStateOf(R.string.tile_auth_pending)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        applyLegacyLanguage()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        viewModel = ViewModelProvider(this)[TileAuthenticationViewModel::class.java]
        observeFlowState()
        setContent {
            AppTheme {
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
                            text = stringResource(statusMessage),
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
        if (viewModel.state.value != TileAuthenticationFlowState.IDLE) return
        authenticateAndRun()
    }

    override fun onDestroy() {
        authenticationRequest?.cancel()
        authenticationRequest = null
        if (::viewModel.isInitialized) {
            if (isChangingConfigurations) viewModel.restartAuthentication()
            else viewModel.cancelAuthentication()
        }
        super.onDestroy()
    }

    private fun authenticateAndRun() {
        if (!intent.hasExtra(EXTRA_TARGET_MUTED)) {
            finish()
            return
        }
        val targetMuted = intent.getBooleanExtra(EXTRA_TARGET_MUTED, false)
        val prefs = PreferencesRepository.getInstance(this)
        if (!prefs.isAppLockEnabled) {
            viewModel.runAuthorizedAction(targetMuted)
            return
        }

        if (!viewModel.beginAuthentication()) return
        if (!AppLockAuthenticator.canAuthenticate(this)) {
            viewModel.authenticationUnavailable()
            return
        }

        authenticationRequest = AppLockAuthenticator.authenticate(
            activity = this,
            title = getString(R.string.tile_auth_title),
            subtitle = getString(R.string.tile_auth_reason),
            onSuccess = { viewModel.runAuthorizedAction(targetMuted) },
            onCancelled = viewModel::cancelAuthentication,
            onError = { viewModel.authenticationError() }
        )
    }

    private fun observeFlowState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    when (state) {
                        TileAuthenticationFlowState.IDLE,
                        TileAuthenticationFlowState.AUTHENTICATING ->
                            statusMessage = R.string.tile_auth_pending
                        TileAuthenticationFlowState.RUNNING ->
                            statusMessage = R.string.tile_change_pending
                        TileAuthenticationFlowState.COMPLETED,
                        TileAuthenticationFlowState.CANCELLED -> finish()
                        TileAuthenticationFlowState.AUTHENTICATION_UNAVAILABLE -> {
                            Toast.makeText(
                                this@TileAuthenticationActivity,
                                getString(R.string.app_lock_unconfigured_message),
                                Toast.LENGTH_LONG
                            ).show()
                            finish()
                        }
                        TileAuthenticationFlowState.AUTHENTICATION_ERROR -> {
                            Toast.makeText(
                                this@TileAuthenticationActivity,
                                getString(R.string.app_lock_authentication_unavailable),
                                Toast.LENGTH_LONG
                            ).show()
                            finish()
                        }
                    }
                }
            }
        }
    }
}
