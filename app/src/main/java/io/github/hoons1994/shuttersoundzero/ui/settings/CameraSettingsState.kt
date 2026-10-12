package io.github.hoons1994.shuttersoundzero.ui.settings

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.hoons1994.shuttersoundzero.core.CscMuteManager
import io.github.hoons1994.shuttersoundzero.core.DeveloperOptionsManager

internal class CameraSettingsState(context: Context) {
    private val context = context.applicationContext
    var isCscMuted by mutableStateOf(CscMuteManager.readCscMutedState(context))
    var wirelessDebuggingEnabled by mutableStateOf(DeveloperOptionsManager.readWirelessDebuggingEnabled(context))
        private set

    fun refresh() {
        isCscMuted = CscMuteManager.readCscMutedState(context)
        wirelessDebuggingEnabled = DeveloperOptionsManager.readWirelessDebuggingEnabled(context)
    }
}

/** Observes device settings only while the settings screen is present. */
@Composable
internal fun rememberCameraSettingsState(): CameraSettingsState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state = remember(context) { CameraSettingsState(context) }
    DisposableEffect(lifecycleOwner, context, state) {
        val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = state.refresh()
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.refresh()
        }
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(CscMuteManager.CSC_KEY), false, settingsObserver
        )
        // Provider support differs across devices; ON_RESUME remains a fallback.
        runCatching {
            context.contentResolver.registerContentObserver(
                DeveloperOptionsManager.wirelessDebuggingUri, false, settingsObserver
            )
        }.onFailure {
            android.util.Log.w("SettingsScreen", "Unable to observe wireless debugging (${it.javaClass.simpleName})")
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        state.refresh()
        onDispose {
            context.contentResolver.unregisterContentObserver(settingsObserver)
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }
    return state
}
