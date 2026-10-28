package io.github.hoons1994.shuttersoundzero

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import io.github.hoons1994.shuttersoundzero.ui.main.MainScreen
import io.github.hoons1994.shuttersoundzero.ui.main.MainScreenViewModel
import io.github.hoons1994.shuttersoundzero.ui.settings.SettingsScreen
import io.github.hoons1994.shuttersoundzero.ui.settings.CameraSettingsOperationViewModel
import io.github.hoons1994.shuttersoundzero.ui.settings.SettingsPermissionRecovery

@Composable
internal fun MainNavigation(
  mainScreenViewModel: MainScreenViewModel,
  cameraSettingsViewModel: CameraSettingsOperationViewModel
) {
  val permissionRecovery by cameraSettingsViewModel.permissionRecovery.collectAsState()
  val initialEntries = remember {
    if (shouldRestoreSettings(cameraSettingsViewModel.permissionRecovery.value)) {
      arrayOf<NavKey>(Main, Settings)
    } else arrayOf<NavKey>(Main)
  }
  val backStack = rememberNavBackStack(*initialEntries)
  var pairingRecoveryTarget by rememberSaveable { mutableStateOf<Boolean?>(null) }

  LaunchedEffect(permissionRecovery) {
    if (shouldRestoreSettings(permissionRecovery) && backStack.lastOrNull() != Settings) {
      backStack.add(Settings)
    }
  }

  NavDisplay(
    backStack = backStack,
    onBack = {
      if (backStack.lastOrNull() == Settings) cameraSettingsViewModel.cancelPermissionRecovery()
      backStack.removeLastOrNull()
    },
    entryProvider =
      entryProvider {
        entry<Main> {
          MainScreen(
            viewModel = mainScreenViewModel,
            onItemClick = { navKey -> backStack.add(navKey) },
            modifier = Modifier.safeDrawingPadding(),
            pairingRecoveryTarget = pairingRecoveryTarget,
            onPairingRecoveryHandled = { pairingRecoveryTarget = null }
          )
        }
        entry<Settings> {
          SettingsScreen(
            onBackClick = {
              cameraSettingsViewModel.cancelPermissionRecovery()
              backStack.removeLastOrNull()
            },
            modifier = Modifier.safeDrawingPadding(),
            onPairingRecoveryRequested = { targetMuted ->
              pairingRecoveryTarget = targetMuted
              backStack.removeLastOrNull()
            }
          )
        }
      },
  )
}

private fun shouldRestoreSettings(recovery: SettingsPermissionRecovery?): Boolean = when (recovery) {
  SettingsPermissionRecovery.READY_REAPPLY,
  SettingsPermissionRecovery.READY_RESTORE,
  SettingsPermissionRecovery.CONFIRM_RESTORE -> true
  else -> false
}

