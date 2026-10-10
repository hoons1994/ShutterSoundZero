package io.github.hoons1994.shuttersoundzero.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.hoons1994.shuttersoundzero.data.PreferencesRepository
import io.github.hoons1994.shuttersoundzero.data.model.AppThemeMode
import io.github.hoons1994.shuttersoundzero.theme.ShutterSoundZeroTheme

@Composable
internal fun rememberAppThemeMode(prefs: PreferencesRepository): State<AppThemeMode> {
    val state = remember(prefs) { mutableStateOf(prefs.appThemeMode) }
    DisposableEffect(prefs) {
        val listener = prefs.registerThemeChangeListener { state.value = it }
        state.value = prefs.appThemeMode
        onDispose { prefs.unregisterThemeChangeListener(listener) }
    }
    return state
}

/** Application preference binding is kept outside the reusable theme. */
@Composable
fun AppTheme(darkTheme: Boolean? = null, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { PreferencesRepository.getInstance(context) }
    val mode by rememberAppThemeMode(prefs)
    ShutterSoundZeroTheme(darkTheme = darkTheme, mode = mode, content = content)
}
