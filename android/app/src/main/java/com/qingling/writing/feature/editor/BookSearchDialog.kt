package com.qingling.writing.feature.editor
import com.qingling.writing.core.design.SearchField
import com.qingling.writing.core.design.EmptyState

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qingling.writing.R
import com.qingling.writing.core.design.BackButton
import com.qingling.writing.core.design.ListRow
import com.qingling.writing.core.model.BookSearchHit
import com.qingling.writing.domain.repository.SearchRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class BookResults(val query: String, val hits: List<BookSearchHit>?, val failed: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookSearchDialog(bookId: String, repository: SearchRepository, initialQuery: String,
    onDismiss: () -> Unit, onOpen: suspend (String, String) -> Boolean) {
    var query by remember { mutableStateOf(initialQuery) }
    var opening by remember { mutableStateOf(false) }
    var openFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val result by produceState<BookResults?>(null, bookId, query) {
        if (query.isEmpty()) { value = BookResults(query, emptyList()); return@produceState }
        delay(250)
        value = try { BookResults(query, repository.searchBook(bookId, query))
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { BookResults(query, null, true) }
    }
    val current = result?.takeIf { it.query == query }
    Dialog(onDismissRequest = { if (!opening) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false,
        dismissOnBackPress = !opening, dismissOnClickOutside = false)) {
        Scaffold(Modifier.fillMaxSize().imePadding(), topBar = {
            TopAppBar(title = { Text(stringResource(R.string.search_book)) },
                navigationIcon = { BackButton(onClick = { if (!opening) onDismiss() }) })
        }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
                SearchField(query, { query = it.take(200); openFailed = false },
                    placeholder=stringResource(R.string.search_book_hint), enabled = !opening, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.search_literal_hint), Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
                if (current == null || opening) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (current?.failed == true || openFailed) Text(stringResource(R.string.search_failed), color = MaterialTheme.colorScheme.error)
                val hits = current?.hits.orEmpty()
                if (query.isNotEmpty() && current?.hits != null) Text(
                    if (hits.size > 200) stringResource(R.string.search_book_limit)
                    else stringResource(R.string.search_book_count, hits.size), style = MaterialTheme.typography.labelMedium)
                LazyColumn(Modifier.weight(1f)) {
                    if(!opening && current?.failed==false && hits.isEmpty()) item { EmptyState(if(query.isBlank()) "搜索整本作品" else "没有匹配的内容",subtitle=if(query.isBlank()) "输入关键词，查找各章正文" else "换个关键词试试") }
                    items(hits.take(200), key = { it.chapterId }) { hit ->
                        Column(Modifier.fillMaxWidth().clickable(enabled = !opening) {
                                opening = true
                                scope.launch {
                                    try { if (!onOpen(hit.chapterId, query)) openFailed = true
                                    } catch (e: CancellationException) { throw e
                                    } catch (_: Exception) { openFailed = true
                                    } finally { opening = false }
                                }
                            }.padding(vertical = 16.dp)) {
                            Text(searchHighlight("${hit.volumeTitle} · ${hit.chapterTitle}", query), style = MaterialTheme.typography.titleMedium)
                            Text(searchHighlight(hit.snippet.replace('\n', ' '), query), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
