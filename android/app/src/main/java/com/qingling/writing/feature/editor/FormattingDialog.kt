package com.qingling.writing.feature.editor
import com.qingling.writing.core.design.QuietTextButton

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qingling.writing.R
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.design.BackButton
import com.qingling.writing.core.model.FormatOptions
import com.qingling.writing.core.model.ParagraphSpacing
import com.qingling.writing.feature.settings.ChoiceRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FormattingDialog(source: FormattingSource, onDismiss: () -> Unit,
    onApply: suspend (String) -> FormatApplyResult, onApplied: () -> Unit) {
    val settings = LocalAppContainer.current.settingsStore
    val saved by produceState<FormatOptions?>(null) { value = settings.formatOptions.first() }
    val initial = saved ?: return
    var options by remember(initial) { mutableStateOf(initial) }
    val computed by produceState<Pair<FormatOptions, String>?>(null, source, options) {
        value = withContext(Dispatchers.Default) { options to ChapterFormatter.format(source.body, options) }
    }
    val preview = computed?.takeIf { it.first == options }?.second
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    com.qingling.writing.core.design.WritingSheet(stringResource(R.string.format_title),onDismiss=onDismiss,canDismiss=!busy,footer={
        Button(enabled=!busy&&preview!=null&&preview!=source.body,onClick={
            val result=preview ?: return@Button
                        scope.launch {
                            busy = true
                            error = null
                            try {
                                // Persist rules before changing text; failed settings writes leave the text intact.
                                settings.setFormatOptions(options)
                                when (onApply(result)) {
                                    FormatApplyResult.APPLIED -> onApplied()
                                    FormatApplyResult.STALE -> error = R.string.format_stale
                                    FormatApplyResult.SAVE_FAILED -> error = R.string.format_save_failed
                                }
                            } catch (failure: CancellationException) { throw failure
                            } catch (_: Exception) { error = R.string.format_save_failed
                            } finally { busy = false }
                        }
        },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(stringResource(R.string.format_apply))}
    }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.format_scope), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium)
                FormatSwitch(stringResource(R.string.format_indent), options.indentTwoCharacters, !busy) {
                    options = options.copy(indentTwoCharacters = it)
                }
                FormatSwitch(stringResource(R.string.format_trim), options.trimLineEnds, !busy) {
                    options = options.copy(trimLineEnds = it)
                }
                Text(stringResource(R.string.format_paragraphs), style = MaterialTheme.typography.titleMedium)
                ParagraphSpacing.entries.forEach { spacing ->
                    ChoiceRow(stringResource(when (spacing) {
                        ParagraphSpacing.KEEP -> R.string.format_keep
                        ParagraphSpacing.COMPACT -> R.string.format_compact
                        ParagraphSpacing.ONE_BLANK_LINE -> R.string.format_blank
                    }), options.spacing == spacing) { if (!busy) options = options.copy(spacing = spacing) }
                }
                HorizontalDivider()
                Text(stringResource(R.string.format_preview), style = MaterialTheme.typography.titleMedium)
                if (preview == null || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (preview == source.body) Text(stringResource(R.string.format_unchanged), style = MaterialTheme.typography.bodySmall)
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                // Limit preview rendering, not the operation itself, for long chapters.
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                    Text(preview?.take(4000).orEmpty(), Modifier.fillMaxWidth().padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge)
                }
                if ((preview?.length ?: 0) > 4000) Text(stringResource(R.string.format_preview_limit), style = MaterialTheme.typography.bodySmall)
            }
    }
}

@Composable
private fun FormatSwitch(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onCheckedChange = onChange, enabled = enabled)
    }
}
