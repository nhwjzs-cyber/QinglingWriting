package com.qingling.writing.feature.editor
import com.qingling.writing.core.design.QuietTextButton
import com.qingling.writing.core.design.ConfirmAction
import com.qingling.writing.core.design.AppTextField

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qingling.writing.R
import com.qingling.writing.app.LocalAppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ReplacementPreview(val source: FormattingSource, val result: String, val count: Int,
    val before: String, val after: String)

@Composable
internal fun ChapterSearchPanel(session: EditorSession, query: String, onQuery: (String) -> Unit,
    onTarget: (SearchMatch) -> Unit, onHighlights: (String, List<SearchMatch>) -> Unit, onClose: () -> Unit,
    onApply: suspend (FormattingSource, String) -> FormatApplyResult) {
    val settings = LocalAppContainer.current.settingsStore
    val body by produceState(session.editorState.text.toString(), session) {
        snapshotFlow { session.editorState.text.toString() }.collect { value = it }
    }
    val computed by produceState<Triple<String, String, SearchMatches>?>(null, body, query) {
        delay(180)
        value = withContext(Dispatchers.Default) { Triple(body, query, TextSearch.find(body, query)) }
    }
    val matches = computed?.takeIf { it.first == body && it.second == query }?.third
    var index by remember(session, query) { mutableIntStateOf(0) }
    var positioned by remember(session, query) { mutableStateOf(false) }
    var replaceOpen by remember { mutableStateOf(false) }
    var replacement by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<ReplacementPreview?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(body, matches) { onHighlights(body, matches?.ranges.orEmpty()) }
    LaunchedEffect(query) {
        delay(600)
        if (query.isNotEmpty()) runCatching { settings.setRecentSearch(query) }
    }
    LaunchedEffect(matches) {
        matches?.ranges?.let { ranges ->
            if (ranges.isNotEmpty()) {
                index = index.coerceIn(0, ranges.lastIndex)
                // Updating highlights while the author types must never move their caret.
                if (!positioned) { onTarget(ranges[index]); positioned = true }
            }
        }
    }
    fun navigate(delta: Int) {
        val ranges = matches?.ranges.orEmpty()
        if (ranges.isNotEmpty()) {
            index = (index + delta + ranges.size) % ranges.size
            onTarget(ranges[index])
            positioned = true
        }
    }
    fun prepare(all: Boolean) {
        val found = matches ?: return
        val selected = found.ranges.getOrNull(index) ?: return
        val source = FormattingSource(session.chapterId, body)
        val q = query
        val to = replacement
        busy = true
        error = null
        scope.launch {
            try {
                preview = withContext(Dispatchers.Default) {
                    val result = TextSearch.replace(source.body, q, to, if (all) null else selected)
                    val sample = if (all) found.ranges.first() else selected
                    val start = (sample.start - 35).coerceAtLeast(0)
                    ReplacementPreview(source, result, if (all) found.total else 1,
                        source.body.substring(start, minOf(source.body.length, sample.end + 65)),
                        result.substring(start.coerceAtMost(result.length), minOf(result.length, sample.start + to.length + 65)))
                }
            } catch (e: CancellationException) { throw e
            } catch (_: IllegalArgumentException) { error = R.string.search_replace_too_large
            } finally { busy = false }
        }
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppTextField(query, { onQuery(it.take(200)) }, singleLine = true,
                    enabled = !busy, label = { Text(stringResource(R.string.search_in_chapter)) },
                    modifier = Modifier.weight(1f))
                IconButton(onClick = onClose, enabled = !busy) {
                    Icon(Icons.Default.Close, stringResource(R.string.action_close))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val ranges = matches?.ranges.orEmpty()
                Text(if (matches == null) stringResource(R.string.search_working)
                    else stringResource(R.string.search_count, if (ranges.isEmpty()) 0 else index + 1, matches.total),
                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { navigate(-1) }, enabled = ranges.isNotEmpty() && !busy) {
                    Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.search_previous))
                }
                IconButton(onClick = { navigate(1) }, enabled = ranges.isNotEmpty() && !busy) {
                    Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.search_next))
                }
                QuietTextButton(onClick = { replaceOpen = !replaceOpen }, enabled = !busy) {
                    Text(stringResource(R.string.search_replace))
                }
            }
            if ((matches?.total ?: 0) > TextSearch.NAVIGATION_LIMIT) Text(stringResource(R.string.search_navigation_limit),
                style = MaterialTheme.typography.bodySmall)
            if (replaceOpen) {
                AppTextField(replacement, { replacement = it.take(2000) }, singleLine = true, enabled = !busy,
                    label = { Text(stringResource(R.string.search_replacement)) }, modifier = Modifier.fillMaxWidth())
                Row {
                    val canReplace = (matches?.total ?: 0) > 0 && query != replacement && !busy
                    QuietTextButton(onClick = { prepare(false) }, enabled = canReplace) { Text(stringResource(R.string.search_replace_current)) }
                    QuietTextButton(onClick = { prepare(true) }, enabled = canReplace) { Text(stringResource(R.string.search_replace_all)) }
                }
            }
            error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
    preview?.let { p ->
        AlertDialog(onDismissRequest = { if (!busy) preview = null },
            title = { Text(stringResource(R.string.search_replace_preview, p.count)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.search_replace_protection))
                Text(stringResource(R.string.search_before, p.before))
                Text(stringResource(R.string.search_after, p.after))
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { ConfirmAction(enabled = !busy, onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        when (onApply(p.source, p.result)) {
                            FormatApplyResult.APPLIED -> { preview = null; index = 0 }
                            FormatApplyResult.STALE -> error = R.string.format_stale
                            FormatApplyResult.SAVE_FAILED -> error = R.string.search_replace_failed
                        }
                    } catch (e: CancellationException) { throw e
                    } catch (_: Exception) { error = R.string.search_replace_failed
                    } finally { busy = false }
                }
            }) { Text(stringResource(R.string.search_replace_confirm)) } },
            dismissButton = { QuietTextButton(enabled = !busy, onClick = { preview = null; error = null }) {
                Text(stringResource(R.string.action_cancel))
            } })
    }
}
