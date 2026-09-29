package io.github.hoons1994.shuttersoundzero

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import io.github.hoons1994.shuttersoundzero.ui.main.MainScreen
import io.github.hoons1994.shuttersoundzero.ui.settings.SettingsScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)
  var pairingRecoveryRequested by rememberSaveable { mutableStateOf(false) }

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> {
          MainScreen(
            onItemClick = { navKey -> backStack.add(navKey) },
            modifier = Modifier.safeDrawingPadding(),
            pairingRecoveryRequested = pairingRecoveryRequested,
            onPairingRecoveryHandled = { pairingRecoveryRequested = false }
          )
        }
        entry<Settings> {
          SettingsScreen(
            onBackClick = { backStack.removeLastOrNull() },
            modifier = Modifier.safeDrawingPadding(),
            onPairingRecoveryRequested = {
              pairingRecoveryRequested = true
              backStack.removeLastOrNull()
            }
          )
        }
      },
  )
}

