package com.qingling.writing.feature.editor
import com.qingling.writing.core.design.QuietTextButton
import com.qingling.writing.core.design.ConfirmAction
import com.qingling.writing.core.design.AppSecondaryButton
import com.qingling.writing.core.design.AppTextField

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.design.BackButton
import com.qingling.writing.core.model.*
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BatchEditDialog(bookId: String, format: Boolean, onDismiss: () -> Unit, onApplied: () -> Unit) {
    val container = LocalAppContainer.current
    val model: BatchEditViewModel = viewModel(key = "batch-$bookId-$format", factory = viewModelFactory {
        initializer { BatchEditViewModel(container.batchEditRepository) }
    })
    val ui by model.ui.collectAsStateWithLifecycle()
    val volumeFlow = remember(bookId) { container.batchEditRepository.observeVolumes(bookId) }
    val volumes by volumeFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val saved by produceState<FormatOptions?>(null) { value = container.settingsStore.formatOptions.first() }
    val initial = saved ?: return
    var volumeId by rememberSaveable { mutableStateOf<String?>(null) }
    var find by rememberSaveable { mutableStateOf("") }
    var replacement by rememberSaveable { mutableStateOf("") }
    var indent by rememberSaveable { mutableStateOf(initial.indentTwoCharacters) }
    var trim by rememberSaveable { mutableStateOf(initial.trimLineEnds) }
    var spacing by rememberSaveable { mutableStateOf(initial.spacing.name) }
    var choosingScope by remember { mutableStateOf(false) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    val scope = BatchScope(bookId, volumeId)
    val operation = if (format) BatchOperation.Format(FormatOptions(indent, trim, ParagraphSpacing.valueOf(spacing)))
        else BatchOperation.Replace(find, replacement)
    val plan = ui.plan?.takeIf { it.source.scope == scope && it.operation == operation }
    val originals = remember(plan) { plan?.source?.documents?.associateBy { it.id }.orEmpty() }
    val scopeName = if (volumeId == null) "整本书" else volumes.firstOrNull { it.id == volumeId }?.title ?: "所选卷已不可用"
    fun dismiss() {
        val latest = model.ui.value
        if (!latest.busy) {
            model.reset()
            if (latest.applied > 0) onApplied() else onDismiss()
        }
    }
    LaunchedEffect(ui.applied) { if (ui.applied > 0) { model.reset(); onApplied() } }
    Dialog(onDismissRequest = ::dismiss, properties = DialogProperties(usePlatformDefaultWidth = false,
        dismissOnBackPress = !ui.busy, dismissOnClickOutside = false)) {
        BackHandler { dismiss() }
        Scaffold(Modifier.fillMaxSize().imePadding(), topBar = {
            TopAppBar(title = { Text(if (format) "整卷／整书排版" else "全书替换") },
                navigationIcon = { BackButton(onClick = ::dismiss) })
        }) { padding ->
            LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text("仅修改正文，不改标题，不包含回收站。先预览再确认；原文将保存在各章历史。批量操作请从各章历史恢复，不使用编辑页撤销按钮。",
                        style = MaterialTheme.typography.bodyMedium)
                    Text("单次最多 1000 章，正文处理前后各不超过 8 MB；排版最多 5 万个换行，请按卷分批处理大书稿。",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
                item { AppSecondaryButton(enabled = !ui.busy, onClick = { choosingScope = true }) { Text("处理范围：$scopeName") } }
                if (!format) {
                    item { AppTextField(find, { find = it.take(200); model.reset() }, label = { Text("查找内容（区分英文大小写）") },
                        enabled = !ui.busy, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                    item { AppTextField(replacement, { replacement = it.take(2000); model.reset() }, label = { Text("替换为（留空即删除）") },
                        enabled = !ui.busy, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                } else {
                    item { Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(indent, enabled = !ui.busy, onCheckedChange = { indent = it; model.reset() }); Text("段首缩进两个全角空格")
                    } }
                    item { Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(trim, enabled = !ui.busy, onCheckedChange = { trim = it; model.reset() }); Text("清理行尾空格")
                    } }
                    item { Column {
                        Text("段间空行")
                        ParagraphSpacing.entries.forEach { value ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(spacing == value.name, enabled = !ui.busy,
                                    onClick = { spacing = value.name; model.reset() })
                                Text(when (value) { ParagraphSpacing.KEEP -> "保持原样"; ParagraphSpacing.COMPACT -> "不留空行"; ParagraphSpacing.ONE_BLANK_LINE -> "保留一个空行" })
                            }
                        }
                    } }
                }
                item {
                    Button(enabled = !ui.busy && (format || find.isNotEmpty()), onClick = { model.preview(scope, operation) }) {
                        Text("生成预览")
                    }
                    if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                    ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                }
                plan?.let { preview ->
                    item {
                        Text("《${preview.source.bookTitle}》 · $scopeName", style = MaterialTheme.typography.titleMedium)
                        Text(if (format) "将修改 ${preview.changes.size} 章" else "将修改 ${preview.changes.size} 章，共 ${preview.changes.sumOf { it.hits }} 处")
                        if (preview.changes.isEmpty()) Text("没有需要修改的内容")
                        Button(enabled = !ui.busy && preview.changes.isNotEmpty(), onClick = { confirming = true }) {
                            Text("核对并应用")
                        }
                    }
                    items(preview.changes, key = { it.chapterId }) { change ->
                        val before = originals.getValue(change.chapterId)
                        val start = if (operation is BatchOperation.Replace)
                            (before.body.indexOf(operation.find) - 60).coerceAtLeast(0) else 0
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${before.volumeTitle} · ${before.title}", style = MaterialTheme.typography.titleMedium)
                                if (!format) Text("${change.hits} 处", style = MaterialTheme.typography.labelMedium)
                                Text("修改前（片段）\n${before.body.drop(start).take(300)}", style = MaterialTheme.typography.bodySmall)
                                HorizontalDivider()
                                Text("修改后（片段）\n${change.after.drop(start).take(300)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
    if (choosingScope) AlertDialog(onDismissRequest = { choosingScope = false }, title = { Text("选择处理范围") },
        text = { LazyColumn(Modifier.heightIn(max = 320.dp)) {
            item { QuietTextButton(onClick = { volumeId = null; model.reset(); choosingScope = false }) { Text("整本书") } }
            items(volumes, key = { it.id }) { volume ->
                QuietTextButton(onClick = { volumeId = volume.id; model.reset(); choosingScope = false }) { Text(volume.title) }
            }
        } }, confirmButton = { ConfirmAction(onClick = { choosingScope = false }) { Text("取消") } })
    if (confirming && plan != null) AlertDialog(onDismissRequest = { if (!ui.busy) confirming = false },
        title = { Text("确认批量修改") },
        text = { Text("即将修改《${plan.source.bookTitle}》的$scopeName，共 ${plan.changes.size} 章。每章先保留原文历史，再整批应用；历史会占用额外空间。预览过时或任何一步失败，都不会留下部分修改。") },
        confirmButton = { ConfirmAction(enabled = !ui.busy, onClick = { confirming = false; model.apply(plan) }) { Text("确认应用") } },
        dismissButton = { QuietTextButton(enabled = !ui.busy, onClick = { confirming = false }) { Text("取消") } })
}
