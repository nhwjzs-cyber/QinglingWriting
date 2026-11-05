package com.qingling.writing.feature.editor
import com.qingling.writing.core.design.QuietTextButton
import com.qingling.writing.core.design.ConfirmAction
import com.qingling.writing.core.design.AppTextField

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StructureDialog(bookId: String,split: StructurePlan?,onDismiss: () -> Unit,
    onApply: suspend (StructurePlan,String) -> String) {
    val container = LocalAppContainer.current
    val model: StructureViewModel = viewModel(key = "structure-$bookId-${split != null}",factory = viewModelFactory {
        initializer { StructureViewModel(container.structure,container.outlineRepository,bookId,onApply) }
    })
    remember(model) { model.begin(split);true }
    val ui by model.ui.collectAsStateWithLifecycle()
    val volumes by model.volumes.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var titles by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf(if (split != null) "拆分出的章节" else "") }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(ui.plan) { if (split == null && title.isBlank()) title = ui.plan?.source?.documents?.firstOrNull()?.title.orEmpty() }
    LaunchedEffect(ui.applied) { if (ui.applied) { model.clear();onDismiss() } }
    fun close() { if (!ui.busy) { model.clear();onDismiss() } }
    Dialog(onDismissRequest = ::close,properties = DialogProperties(usePlatformDefaultWidth = false,dismissOnClickOutside = false,dismissOnBackPress = !ui.busy)) {
        BackHandler(enabled = ui.busy) { }
        Scaffold(Modifier.fillMaxSize().imePadding(),topBar = {
            TopAppBar(title = { Text(if (split != null) "按光标拆分章节" else "合并所选章节") },navigationIcon = { BackButton(onClick = ::close) })
        }) { padding ->
            LazyColumn(Modifier.padding(padding).fillMaxSize(),contentPadding = PaddingValues(16.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(if (split != null) "原章保留光标前的内容，后半段成为紧随其后的新草稿。完整原文先存历史；原细纲留在原章。"
                        else "按目录顺序合并到最前面的所选章，其他所选章移入回收站。各原章先存历史；未选章节不改动。原章细纲仍各自保留，回收站内章节的细纲随之隐藏。")
                    Text("单次最多 200 章、正文不超过 8 MB。结构整理不计入今日净增；修改后的保留章改为草稿。",style = MaterialTheme.typography.bodySmall)
                }
                ui.error?.let { error -> item { Text(error,color = MaterialTheme.colorScheme.error) } }
                if (ui.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                val plan = ui.plan
                if (plan == null && split == null) {
                    item { Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(titles,{ titles = it },enabled = !ui.busy)
                        Text("将各章标题加入合并正文")
                    } }
                    item { Button(enabled = !ui.busy && selected.size >= 2,onClick = { model.preview(selected,titles) }) { Text("预览合并 ${selected.size} 章") } }
                    volumes.forEach { volume ->
                        item(key = volume.id) { Text(volume.title,style = MaterialTheme.typography.titleMedium) }
                        items(volume.chapters,key = { it.id }) { chapter ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(chapter.id in selected,{ checked -> selected = if (checked) selected+chapter.id else selected-chapter.id },
                                    enabled = !ui.busy && (chapter.id in selected || selected.size < ChapterStructure.MAX_CHAPTERS))
                                Column { Text(chapter.title);Text("${chapter.wordCount} 字",style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                } else if (plan != null) {
                    item { AppTextField(title,{ title = it },enabled = !ui.busy,singleLine = true,
                        label = { Text(if (split != null) "新章节标题" else "合并后的章节标题（不能为空）") },modifier = Modifier.fillMaxWidth()) }
                    item {
                        if (plan.splitOffset != null) {
                            val text = plan.source.documents.single().body;val offset = plan.splitOffset
                            Text("已按打开预览时的光标位置分为两部分。以下展示各自开头，完整内容将原样保留。",style = MaterialTheme.typography.bodySmall)
                            Text("原章开头：\n"+text.substring(0,minOf(offset,160)))
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            Text("新章开头：\n"+text.substring(offset,minOf(text.length,offset+160)))
                        } else {
                            Text("将保留第一项的章节位置；章间加入两个换行。${if(plan.includeTitles) "正文包含各章标题。" else "正文不加入各章标题。"}")
                            Text("合并顺序与片段：",style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    if (plan.splitOffset == null) items(plan.source.documents,key = { it.id }) { row ->
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                            Text("${row.volumeTitle} · ${row.title}",style = MaterialTheme.typography.titleMedium)
                            Text(row.body.take(160).ifEmpty { "（空章）" })
                        } }
                    }
                    item { Button(enabled = !ui.busy && title.isNotBlank(),onClick = { confirm = true }) { Text("核对并继续") } }
                    if (split == null) item { QuietTextButton(enabled = !ui.busy,onClick = { model.clear() }) { Text("重新选择") } }
                }
            }
        }
        if (confirm) AlertDialog(onDismissRequest = { if (!ui.busy) confirm = false },title = { Text("确认${if(split == null) "合并" else "拆分"}？") },
            text = { Text("将按上方预览修改目录和正文，并保留修改前的完整历史。操作开始后请等待完成；如内容变化，会要求重新预览。") },
            confirmButton = { ConfirmAction(enabled = !ui.busy,onClick = { confirm = false;model.apply(title) }) { Text("确认执行") } },
            dismissButton = { QuietTextButton(enabled = !ui.busy,onClick = { confirm = false }) { Text("取消") } })
    }
}
