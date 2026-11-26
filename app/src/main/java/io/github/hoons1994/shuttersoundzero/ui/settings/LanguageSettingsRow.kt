package io.github.hoons1994.shuttersoundzero.ui.settings

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.hoons1994.shuttersoundzero.R
import io.github.hoons1994.shuttersoundzero.data.AppLanguage

@Composable
internal fun LanguageSettingsRow(showPicker: Boolean, onShowPicker: (Boolean) -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var selected by remember(context) { mutableStateOf(AppLanguage.current(context)) }
    LifecycleResumeEffect(context) {
        selected = AppLanguage.current(context)
        onPauseOrDispose { }
    }
    ClickableRow(
        title = stringResource(R.string.language_title),
        subtitle = stringResource(selected.label),
        onClick = { onShowPicker(true) }
    )
    if (showPicker) {
        AlertDialog(
            onDismissRequest = { onShowPicker(false) },
            title = { Text(stringResource(R.string.language_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                    AppLanguage.entries.forEach { option ->
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .selectable(selected = selected == option, role = Role.RadioButton) {
                                    onShowPicker(false)
                                    AppLanguage.select(context, option)
                                    if (selected != option && Build.VERSION.SDK_INT < 33) activity?.recreate()
                                    selected = option
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selected == option, onClick = null)
                            Text(stringResource(option.label), Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onShowPicker(false) }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}
