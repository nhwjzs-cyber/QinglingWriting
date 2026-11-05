package com.qingling.writing.feature.bookshelf
import com.qingling.writing.core.design.ConfirmAction
import com.qingling.writing.core.design.AppTextField

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.qingling.writing.core.design.QuietTextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qingling.writing.R
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.design.BackButton
import com.qingling.writing.core.design.EmptyState
import com.qingling.writing.core.design.Radii
import com.qingling.writing.core.design.Spacing
import com.qingling.writing.core.design.TouchTarget
import com.qingling.writing.core.design.formatTimestamp
import com.qingling.writing.core.model.RecycleEntry
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import com.qingling.writing.core.model.PurgePlan

/**
 * 回收站(P06 回收部分):按删除批次列出墓碑,按批次整体恢复。
 * 恢复父项不复活此前已单独删除的子项(docs/01 §4,数据层以 deletionBatchId 保证,
 * 并由 androidTest 覆盖);无自动永久清理。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: RecycleBinViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                RecycleBinViewModel(container.writingRepository, container.recycleBinRepository)
            }
        },
    )
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var plan by remember { mutableStateOf<PurgePlan?>(null) }
    var finalStep by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = busy) { }
    fun prepare(selected: List<RecycleEntry>) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try { plan = viewModel.preparePurge(selected); finalStep = false; confirmation = ""
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { error = "回收站内容已变化或暂时无法读取，请刷新后重试。"
            } finally { busy = false }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.recycle_title)) },
                navigationIcon = { BackButton(onClick = { if (!busy) onBack() }) },
                actions = { QuietTextButton(enabled = !busy && entries.isNotEmpty(), onClick = { prepare(entries.toList()) }) { Text("清空") } },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(Spacing.s16)) }
            if (entries.isEmpty()) {
                EmptyState(title = stringResource(R.string.recycle_empty),subtitle="删除的作品、分卷和章节会先放在这里",modifier=Modifier.fillMaxSize().wrapContentHeight())
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = Spacing.s24),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s8),
                ) {
                    items(entries, key = { it.batchId }) { entry ->
                        RecycleEntryCard(entry = entry, enabled = !busy, onRestore = {
                            if (!busy) {
                                busy = true; error = null
                                scope.launch {
                                    try { viewModel.restore(entry)
                                    } catch (e: CancellationException) { throw e
                                    } catch (_: Exception) { error = "暂时无法恢复，请重试。"
                                    } finally { busy = false }
                                }
                            }
                        }, onDelete = { prepare(listOf(entry)) })
                    }
                }
            }
            Text(
                text = stringResource(R.string.recycle_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.s24, vertical = Spacing.s16),
            )
        }
    }
    plan?.let { p ->
        AlertDialog(onDismissRequest = { if (!busy) plan = null },
            title = { Text(if (finalStep) "最后确认：永久删除" else "核对永久删除范围") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                Text(if (p.entries.size == 1) "选中：${p.entries.first().title}" else "选中 ${p.entries.size} 个回收站条目")
                Text("将永久删除 ${p.bookIds.size} 本书、${p.volumeIds.size} 卷、${p.chapterIds.size} 章及 ${p.revisionCount} 份历史。删除父项也会删除其中更早单独移入回收站的内容。")
                Text("无法从应用回收站或历史恢复。已有的独立备份和导出文件不会被删除。")
                Text("同时永久删除关联的 ${p.planningIds.size} 条细纲、概要、设定或便签。")
                if (finalStep) AppTextField(confirmation, { confirmation = it }, enabled = !busy,
                    label = { Text("输入“永久删除”确认") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { ConfirmAction(enabled = !busy && (!finalStep || confirmation == "永久删除"), onClick = {
                if (!finalStep) finalStep = true else {
                    busy = true; error = null
                    scope.launch {
                        try { viewModel.purge(p); plan = null
                        } catch (e: CancellationException) { throw e
                        } catch (_: Exception) {
                            error = "删除未完成，本次写入已回滚。范围可能已变化，请取消后重新核对。"
                        } finally { busy = false }
                    }
                }
            }) { Text(if (finalStep) "永久删除" else "继续核对") } },
            dismissButton = { QuietTextButton(enabled = !busy, onClick = { plan = null }) { Text("取消") } })
    }
}

@Composable
private fun RecycleEntryCard(entry: RecycleEntry, enabled: Boolean, onRestore: () -> Unit, onDelete: () -> Unit) {
    Card(
        shape = RoundedCornerShape(Radii.bookCard),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.s24),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.s20, top = Spacing.s12, bottom = Spacing.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(Radii.button),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = when (entry.kind) {
                                RecycleEntry.Kind.BOOK -> stringResource(R.string.recycle_kind_book)
                                RecycleEntry.Kind.VOLUME -> stringResource(R.string.recycle_kind_volume)
                                RecycleEntry.Kind.CHAPTER -> stringResource(R.string.recycle_kind_chapter)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.s8, vertical = Spacing.s4),
                        )
                    }
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = Spacing.s8),
                    )
                }
                val contextText = entry.bookTitle?.let {
                    stringResource(R.string.recycle_entry_context_format, it)
                }
                val rangeText = if (entry.volumeCount != null || entry.chapterCount != null) {
                    stringResource(
                        R.string.recycle_entry_book_detail_format,
                        entry.volumeCount ?: 0,
                        entry.chapterCount ?: 0,
                    )
                } else {
                    null
                }
                val deletedText = stringResource(R.string.recycle_deleted_at_format, formatTimestamp(entry.deletedAt))
                val detail = listOfNotNull(contextText, rangeText, deletedText).joinToString(" · ")
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.s4),
                )
            }
            QuietTextButton(
                enabled = enabled,
                onClick = onRestore,
                modifier = Modifier.sizeIn(minWidth = TouchTarget.min, minHeight = TouchTarget.min),
            ) {
                Text(text = stringResource(R.string.action_restore))
            }
        }
        QuietTextButton(enabled = enabled, onClick = onDelete) {
            Text("永久删除", color = MaterialTheme.colorScheme.error)
        }
    }
}
