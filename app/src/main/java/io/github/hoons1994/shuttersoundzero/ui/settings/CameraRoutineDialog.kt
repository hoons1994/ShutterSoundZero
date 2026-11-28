package io.github.hoons1994.shuttersoundzero.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.AtomicFile
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.github.hoons1994.shuttersoundzero.R
import java.io.File
import java.io.IOException

private const val ROUTINES_PACKAGE = "com.samsung.android.app.routines"

private enum class RoutineScreen { Import, Routines, Settings, Manual }

private enum class CameraSoundMode(val label: Int, val template: Int) {
    Silent(R.string.camera_routine_silent, R.raw.camera_silent),
    Vibrate(R.string.camera_routine_vibrate, R.raw.camera_vibrate)
}

private fun Context.openRoutineScreen(screen: RoutineScreen, mode: CameraSoundMode): Boolean {
    try {
        val intent = when (screen) {
            RoutineScreen.Import -> {
                val folder = File(cacheDir, "routine-templates")
                if (!folder.isDirectory && !folder.mkdirs()) return false
                val file = File(folder, resources.getResourceEntryName(mode.template) + ".rtn")
                val template = AtomicFile(file)
                val output = template.startWrite()
                try {
                    resources.openRawResource(mode.template).use { it.copyTo(output) }
                    template.finishWrite(output)
                } catch (e: IOException) {
                    template.failWrite(output)
                    throw e
                }
                val uri = FileProvider.getUriForFile(this, "${packageName}.routine-template", file)
                Intent(Intent.ACTION_VIEW).setPackage(ROUTINES_PACKAGE)
                    .setDataAndType(uri, "application/vnd.samsung.routines")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            RoutineScreen.Routines -> {
                val tab = Intent("com.samsung.android.app.routines.action.LAUNCH_ROUTINE_TAB").setPackage(ROUTINES_PACKAGE)
                if (tab.resolveActivity(packageManager) != null) tab
                else packageManager.getLaunchIntentForPackage(ROUTINES_PACKAGE) ?: return false
            }
            RoutineScreen.Settings -> Intent(Settings.ACTION_SETTINGS)
            RoutineScreen.Manual -> return false
        }
        startActivity(intent)
        return true
    } catch (_: ActivityNotFoundException) {
        return false
    } catch (_: SecurityException) {
        return false
    } catch (_: IOException) {
        return false
    }
}

@Composable
internal fun CameraRoutineDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(RoutineScreen.Import) }
    var mode by rememberSaveable { mutableStateOf(CameraSoundMode.Silent) }
    var importOpened by rememberSaveable { mutableStateOf(false) }
    val guide = stringResource(R.string.camera_routine_steps, stringResource(mode.label))
    val message = when (screen) {
        RoutineScreen.Import -> stringResource(if (importOpened) R.string.camera_routine_import_opened else R.string.camera_routine_import_description)
        RoutineScreen.Routines -> guide
        else -> stringResource(R.string.camera_routine_manual_path) + "\n\n" + guide
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.camera_routine_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (screen == RoutineScreen.Import) {
                    Text(stringResource(R.string.camera_routine_mode_question))
                    Row(modifier = Modifier.selectableGroup()) {
                        CameraSoundMode.entries.forEach { option ->
                            Row(
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                    .selectable(
                                        selected = option == mode,
                                        role = Role.RadioButton,
                                        onClick = { mode = option }
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = option == mode, onClick = null)
                                Text(stringResource(option.label), modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
                Text(message)
                if (screen == RoutineScreen.Import) {
                    TextButton(onClick = { screen = RoutineScreen.Routines }) {
                        Text(stringResource(R.string.camera_routine_manual))
                    }
                }
            }
        },
        confirmButton = {
            if (screen != RoutineScreen.Manual) {
                TextButton(onClick = {
                    if (context.openRoutineScreen(screen, mode)) {
                        if (screen == RoutineScreen.Import) importOpened = true else onDismiss()
                    }
                    else screen = RoutineScreen.entries[screen.ordinal + 1]
                }) {
                    Text(stringResource(when (screen) {
                        RoutineScreen.Import -> R.string.camera_routine_add
                        RoutineScreen.Routines -> R.string.camera_routine_open
                        else -> R.string.camera_routine_open_settings
                    }))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.camera_routine_close)) } }
    )
}
