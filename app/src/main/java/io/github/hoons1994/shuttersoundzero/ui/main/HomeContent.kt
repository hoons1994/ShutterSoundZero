package io.github.hoons1994.shuttersoundzero.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hoons1994.shuttersoundzero.R
import io.github.hoons1994.shuttersoundzero.Settings

internal val CardRadius = 24.dp
internal val ScreenPadding = 20.dp

@Composable
internal fun HomeContent(
    uiState: MainUiState,
    onSettingsClick: () -> Unit = {},
    onSetup: () -> Unit = {},
    onReapply: () -> Unit = {},
    onOpenCamera: () -> Unit = {},
    onOpenSoftwareInfo: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
    onSystemVolumeChange: (Int) -> Int? = { null },
    modifier: Modifier = Modifier,
    onOpenSoundSettings: () -> Unit = {},
    onOpenWirelessDebugging: () -> Unit = {},
    onRefreshCscState: () -> Unit = {}
) {
    val homeStatus = resolveHomeStatus(uiState)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        AppHeader(onSettingsClick = onSettingsClick)
        Spacer(modifier = Modifier.height(10.dp))

        StatusHeroCard(
            status = homeStatus,
            setupIssue = uiState.setupIssue,
            isInProgress = uiState.isCscChangeInProgress,
            isWirelessDebuggingEnabled = uiState.isWirelessDebuggingEnabled,
            onOpenWirelessDebugging = onOpenWirelessDebugging,
            onPrimaryAction = when (homeStatus) {
                HomeStatus.APPLYING -> null
                HomeStatus.READY -> null
                HomeStatus.REAPPLY_REQUIRED -> onReapply
                HomeStatus.STATE_UNKNOWN -> onRefreshCscState
                HomeStatus.SETUP_REQUIRED -> onSetup
            },
            onCameraAction = onOpenCamera
        )

        if (homeStatus == HomeStatus.SETUP_REQUIRED) {
            Spacer(modifier = Modifier.height(20.dp))
            SetupProgressCard(uiState, onOpenSoftwareInfo, onOpenAppSettings)
        }

        Spacer(modifier = Modifier.height(20.dp))
        SystemVolumeCard(uiState.systemVolume, onSystemVolumeChange, onOpenSoundSettings)

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun AppHeader(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding)
            .padding(top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "필요한 상태만 한눈에 확인하세요",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onSettingsClick) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "설정",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
