package io.github.hoons1994.shuttersoundzero.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.hoons1994.shuttersoundzero.R
import io.github.hoons1994.shuttersoundzero.data.PreferencesRepository
import io.github.hoons1994.shuttersoundzero.security.AppLockSession

/** Success from either foreground controls or pairing waits for an unlocked, resumed Activity. */
@Composable
internal fun DeveloperDonationPromptHost(prefs: PreferencesRepository, enabled: Boolean) {
    var showPrompt by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    DisposableEffect(prefs, lifecycleOwner, enabled) {
        val handler = Handler(Looper.getMainLooper())
        var active = true
        fun showIfReady() {
            // ON_RESUME can precede removal of the old, now-locked destination.
            if (active && enabled && prefs.hasSeenInitialNotice && !showPrompt &&
                (!prefs.isAppLockEnabled || AppLockSession.isUnlocked) &&
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                prefs.claimDonationPrompt()
            ) showPrompt = true
        }
        val listener = prefs.registerDonationPromptListener { handler.post { showIfReady() } }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) showIfReady()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        showIfReady()
        onDispose {
            active = false
            handler.removeCallbacksAndMessages(null)
            lifecycleOwner.lifecycle.removeObserver(observer)
            prefs.unregisterDonationPromptListener(listener)
        }
    }

    if (showPrompt && enabled && (!prefs.isAppLockEnabled || AppLockSession.isUnlocked)) {
        DeveloperDonationPromptDialog(
            onDismiss = { showPrompt = false },
            onDonate = {
                showPrompt = false
                openDeveloperDonation(context)
            }
        )
    }
}

@Composable
internal fun DeveloperDonationPromptDialog(onDismiss: () -> Unit, onDonate: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.donation_prompt_title)) },
        text = { Text(stringResource(R.string.donation_prompt_message)) },
        confirmButton = {
            TextButton(onClick = onDonate) { Text(stringResource(R.string.donation_prompt_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.donation_prompt_dismiss)) }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

internal fun openDeveloperDonation(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DEVELOPER_DONATION_URL)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "후원 링크를 열 수 있는 앱을 찾지 못했습니다.", Toast.LENGTH_LONG).show()
    }
}

private const val DEVELOPER_DONATION_URL = "https://qr.kakaopay.com/Ej7i4dwyE"
