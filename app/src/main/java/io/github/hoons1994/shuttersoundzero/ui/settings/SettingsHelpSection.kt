package io.github.hoons1994.shuttersoundzero.ui.settings

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import io.github.hoons1994.shuttersoundzero.R
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hoons1994.shuttersoundzero.logging.DiagnosticLogger
import io.github.hoons1994.shuttersoundzero.diagnostics.DiagnosticReportBuilder
import io.github.hoons1994.shuttersoundzero.diagnostics.DiagnosticEmail
import io.github.hoons1994.shuttersoundzero.data.withAppLanguage
import io.github.hoons1994.shuttersoundzero.ui.components.openDeveloperDonation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

private data class HelpQuestion(val id: String, @StringRes val question: Int, @StringRes val answer: Int)

private val helpQuestions = listOf(
    HelpQuestion(
        "camera_behavior",
        R.string.faq_camera_question,
        R.string.faq_camera_answer
    ),
    HelpQuestion(
        "wireless_debugging",
        R.string.faq_wireless_question,
        R.string.faq_wireless_answer
    ),
    HelpQuestion(
        "pairing_code_theme",
        R.string.faq_code_question,
        R.string.faq_code_answer
    ),
    HelpQuestion(
        "software_update",
        R.string.faq_update_question,
        R.string.faq_update_answer
    ),
    HelpQuestion(
        "reboot",
        R.string.faq_reboot_question,
        R.string.faq_reboot_answer
    ),
    HelpQuestion(
        "app_update",
        R.string.faq_app_update_question,
        R.string.faq_app_update_answer
    ),
    HelpQuestion(
        "reapply_vs_setup",
        R.string.faq_reapply_question,
        R.string.faq_reapply_answer
    ),
    HelpQuestion(
        "notifications",
        R.string.faq_notifications_question,
        R.string.faq_notifications_answer
    ),
    HelpQuestion(
        "legal",
        R.string.faq_privacy_question,
        R.string.faq_privacy_answer
    )
)

internal class DiagnosticPreviewViewModel : ViewModel() {
    var report by mutableStateOf<DiagnosticReportBuilder.Report?>(null)
    var isClearing by mutableStateOf(false)
        private set

    fun clearLog(
        clear: suspend () -> Result<Boolean>,
        rebuild: suspend () -> DiagnosticReportBuilder.Report,
        notify: (Int) -> Unit
    ) {
        val previous = report ?: return
        if (isClearing) return
        isClearing = true
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            var cleared = false
            try {
                val deleted = clear().getOrThrow()
                cleared = true
                if (report === previous) {
                    val refreshed = rebuild()
                    if (report === previous) report = refreshed
                }
                notify(if (deleted) R.string.report_logs_deleted else R.string.report_no_logs)
            } catch (error: CancellationException) {
                if (report === previous) report = null
                throw error
            } catch (_: Exception) {
                if (cleared && report === previous) report = null
                notify(if (cleared) R.string.report_reload_failed else R.string.report_delete_failed)
            } finally {
                isClearing = false
            }
        }
    }
}

@Composable
fun SettingsHelpSection(
    buildDiagnosticReport: suspend (Context) -> DiagnosticReportBuilder.Report = DiagnosticReportBuilder::build,
    clearDiagnosticLog: suspend (Context) -> Result<Boolean> = DiagnosticLogger::clear
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var expandedQuestionId by remember { mutableStateOf<String?>(null) }
    val preview: DiagnosticPreviewViewModel = viewModel()
    var userDescription by rememberSaveable { mutableStateOf("") }
    var isPreparingReport by remember { mutableStateOf(false) }

    SectionLabel(stringResource(R.string.faq_title))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            helpQuestions.forEachIndexed { index, item ->
                HelpAccordionItem(
                    item = item,
                    expanded = expandedQuestionId == item.id,
                    onToggle = {
                        expandedQuestionId = if (expandedQuestionId == item.id) null else item.id
                    }
                )
                if (index < helpQuestions.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    SectionLabel(stringResource(R.string.support_title))
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            OpenChatSupportRow(onClick = { openDeveloperOpenChat(context) })
            HorizontalDivider(
                modifier = Modifier.padding(start = 20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                thickness = 0.5.dp
            )
            SupportRow(enabled = !isPreparingReport && !preview.isClearing, isLoading = isPreparingReport, onClick = {
                if (isPreparingReport || preview.isClearing) return@SupportRow
                isPreparingReport = true
                scope.launch {
                    try {
                        preview.report = buildDiagnosticReport(context.applicationContext)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        Toast.makeText(context, resources.getString(R.string.report_load_failed), Toast.LENGTH_SHORT).show()
                    } finally {
                        isPreparingReport = false
                    }
                }
            })
            HorizontalDivider(
                modifier = Modifier.padding(start = 20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                thickness = 0.5.dp
            )
            DeveloperDonationRow(onClick = { openDeveloperDonation(context) })
        }
    }

    preview.report?.let { report ->
        AlertDialog(
            onDismissRequest = { preview.report = null },
            title = { Text(stringResource(R.string.report_error)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.report_instructions),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = stringResource(R.string.report_privacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = userDescription,
                        onValueChange = { userDescription = it.take(DiagnosticEmail.MAX_DESCRIPTION_CHARS) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.report_description)) },
                        placeholder = {
                            Text(stringResource(R.string.report_example))
                        },
                        minLines = 3,
                        maxLines = 6
                    )

                    Text(
                        text = stringResource(R.string.report_preview),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    SelectionContainer {
                        Text(
                            text = report.diagnosticText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    TextButton(
                        enabled = !preview.isClearing,
                        onClick = {
                            val app = context.applicationContext
                            preview.clearLog(
                                clear = { clearDiagnosticLog(app) },
                                rebuild = { buildDiagnosticReport(app) },
                                notify = { Toast.makeText(app.withAppLanguage(), it, Toast.LENGTH_SHORT).show() }
                            )
                        }
                    ) {
                        Text(stringResource(if (preview.isClearing) R.string.report_deleting else R.string.report_delete))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !preview.isClearing,
                    onClick = {
                        if (!preview.isClearing && preview.report === report) {
                            sendDiagnosticEmail(context, report, userDescription)
                        }
                    }
                ) {
                    Text(stringResource(R.string.report_send))
                }
            },
            dismissButton = {
                TextButton(onClick = { preview.report = null }) {
                    Text(stringResource(R.string.camera_routine_close))
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
internal fun NotificationPopupSettingsRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.support_popup_title),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.support_popup_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 19.sp
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.accessibility_app_notifications),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OpenChatSupportRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.support_contact),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.support_contact_chat),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.accessibility_open_chat),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun openDeveloperOpenChat(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DEVELOPER_OPEN_CHAT_URL)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(
            context,
            context.getString(R.string.support_chat_unavailable),
            Toast.LENGTH_LONG
        ).show()
    }
}

private const val DEVELOPER_OPEN_CHAT_URL = "https://open.kakao.com/o/sxO88pOi"

@Composable
private fun DeveloperDonationRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.support_donate),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.support_donate_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.accessibility_donate),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SupportRow(enabled: Boolean, isLoading: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.support_unresolved),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isLoading) stringResource(R.string.report_loading) else stringResource(R.string.report_invitation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.accessibility_report),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun sendDiagnosticEmail(
    context: Context,
    report: DiagnosticReportBuilder.Report,
    userDescription: String
) {
    try {
        context.startActivity(
            Intent.createChooser(
                DiagnosticEmail.intent(context, report, userDescription),
                context.getString(R.string.report_send_chooser)
            )
        )
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(
            context,
            context.getString(R.string.report_no_email),
            Toast.LENGTH_LONG
        ).show()
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.1.sp
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, bottom = 6.dp)
    )
}

@Composable
private fun HelpAccordionItem(item: HelpQuestion, expanded: Boolean, onToggle: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(item.question),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = if (expanded) {
                    Icons.Default.KeyboardArrowUp
                } else {
                    Icons.Default.KeyboardArrowDown
                },
                contentDescription = if (expanded) stringResource(R.string.accessibility_collapse) else stringResource(R.string.accessibility_expand),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (expanded) {
            Text(
                text = stringResource(item.answer),
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 19.sp
            )
        }
    }
}
