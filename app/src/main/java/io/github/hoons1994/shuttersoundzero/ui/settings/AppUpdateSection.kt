package io.github.hoons1994.shuttersoundzero.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.hoons1994.shuttersoundzero.AppDependencies
import io.github.hoons1994.shuttersoundzero.R
import io.github.hoons1994.shuttersoundzero.update.RELEASES_PAGE
import io.github.hoons1994.shuttersoundzero.update.UpdateCheckFailure
import io.github.hoons1994.shuttersoundzero.update.UpdateStatus

@Composable
internal fun AppUpdateSection(currentVersion: String) {
    val context = LocalContext.current
    val factory = remember(currentVersion) {
        AppUpdateViewModel.factory(currentVersion, AppDependencies.appUpdates()::check)
    }
    val viewModel: AppUpdateViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AppUpdateContent(currentVersion, state, viewModel::checkForUpdates) { url ->
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(context, R.string.app_update_open_failed, Toast.LENGTH_SHORT).show()
        }
    }
}

/** Rendering and user actions are separate from network and version-comparison policy. */
@Composable
internal fun AppUpdateContent(
    currentVersion: String,
    state: AppUpdateUiState,
    onCheck: () -> Unit,
    onOpenRelease: (String) -> Unit
) {
    val result = state.result
    val statusMessage = when {
        state.isChecking -> stringResource(R.string.app_update_checking)
        state.failure != null -> stringResource(when (state.failure) {
            UpdateCheckFailure.NETWORK -> R.string.app_update_network_failed
            UpdateCheckFailure.RATE_LIMITED -> R.string.app_update_rate_limited
            UpdateCheckFailure.NO_RELEASE -> R.string.app_update_no_release
            UpdateCheckFailure.SERVER -> R.string.app_update_server_failed
            UpdateCheckFailure.INVALID_RESPONSE -> R.string.app_update_invalid_response
            UpdateCheckFailure.INSTALLED_VERSION -> R.string.app_update_installed_unknown
        })
        result == null -> stringResource(R.string.app_update_idle)
        result.status == UpdateStatus.AVAILABLE -> stringResource(R.string.app_update_available, result.release.tag)
        result.status == UpdateStatus.UP_TO_DATE -> stringResource(R.string.app_update_latest, result.release.tag)
        else -> stringResource(R.string.app_update_installed_newer, result.release.tag)
    }
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.app_update_title), style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(R.string.app_update_current_version, currentVersion),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            statusMessage,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodySmall,
            color = if (state.failure != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = onCheck, enabled = !state.isChecking) {
            Text(stringResource(if (state.isChecking) R.string.app_update_checking else R.string.app_update_check))
        }
        TextButton(onClick = { onOpenRelease(result?.release?.pageUrl ?: RELEASES_PAGE) }) {
            Text(stringResource(if (result?.status == UpdateStatus.AVAILABLE)
                R.string.app_update_view_release else R.string.app_update_open_releases))
        }
    }
}
