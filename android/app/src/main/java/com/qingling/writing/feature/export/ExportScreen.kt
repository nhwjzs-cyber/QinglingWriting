package com.qingling.writing.feature.export
import com.qingling.writing.core.design.QuietTextButton
import com.qingling.writing.core.design.ConfirmAction
import com.qingling.writing.core.design.AppSecondaryButton
import com.qingling.writing.core.design.AppChoice

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qingling.writing.R
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.design.BackButton
import com.qingling.writing.core.design.CenterMessage
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(bookId: String, onBack: () -> Unit, modifier: Modifier = Modifier,preset:com.qingling.writing.core.model.ExportPreset=com.qingling.writing.core.model.ExportPreset()) {
    val container = LocalAppContainer.current
    val model: ExportViewModel = viewModel(key = bookId, factory = viewModelFactory {
        initializer { ExportViewModel(container.outlineRepository, container.exportRepository,
            container.exportFiles, bookId, createSavedStateHandle(),container.settingsStore,preset) }
    })
    val ui by model.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copyBusy by remember { mutableStateOf(false) }
    var copyNotice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var previewVisible by rememberSaveable { mutableStateOf(false) }
    val shareTitle = "分享 ${ui.format.name}"
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) model.cancelSave()
        else model.completeSave { context.contentResolver.openOutputStream(uri, "wt") }
    }
    val docxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(com.qingling.writing.core.files.DocxWriter.MIME)) { uri ->
        if (uri == null) model.cancelSave()
        else model.completeSave { context.contentResolver.openOutputStream(uri, "wt") }
    }
    BackHandler(enabled = ui.busy || copyBusy) { /* Keep the pending write associated with this page. */ }
    Scaffold(modifier = modifier.fillMaxSize(), topBar = {
        TopAppBar(title = { Text(stringResource(R.string.export_title)) },
            navigationIcon = { BackButton(onClick = { if (!ui.busy && !copyBusy) onBack() }) })
    }, bottomBar = {
        if (ui.bookTitle != null) Surface(tonalElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppSecondaryButton(enabled = !ui.busy && !copyBusy && ui.selected.isNotEmpty(), modifier = Modifier.weight(1f),
                    onClick = { scope.launch {
                        val prepared = model.prepare() ?: return@launch
                        try {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.exports", prepared.staged.file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = prepared.format.mime
                                putExtra(Intent.EXTRA_STREAM, uri)
                                clipData = ClipData.newRawUri(prepared.staged.fileName, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, shareTitle))
                            model.sharedChooserOpened()
                        } catch (_: Exception) { model.launchFailed() }
                    } }) { Text("分享 ${ui.format.name}") }
                Button(enabled = !ui.busy && !copyBusy && ui.selected.isNotEmpty(), modifier = Modifier.weight(1f),
                    onClick = { scope.launch {
                        val prepared = model.beginSave() ?: return@launch
                        try { if(prepared.format == com.qingling.writing.core.model.ExportFormat.DOCX) docxLauncher.launch(prepared.staged.fileName) else launcher.launch(prepared.staged.fileName) }
                        catch (_: Exception) { model.launchFailed() }
                    } }) { Text("保存 ${ui.format.name}") }
            }
        }
    }) { padding ->
        when {
            !ui.loaded -> CenterMessage(stringResource(R.string.editor_loading), Modifier.padding(padding))
            ui.bookTitle == null -> CenterMessage(stringResource(R.string.export_no_book), Modifier.padding(padding))
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(ui.bookTitle.orEmpty(), style = MaterialTheme.typography.titleLarge)
                            Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=androidx.compose.foundation.shape.RoundedCornerShape(20.dp)){Text("范围："+ui.scopeLabel,Modifier.padding(horizontal=12.dp,vertical=4.dp),style=MaterialTheme.typography.labelMedium)}
                            Text(stringResource(R.string.export_description), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.export_selected_count, ui.selected.size),
                                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        com.qingling.writing.core.model.ExportFormat.entries.forEach { format ->
                            AppChoice(selected = ui.format == format,enabled = !ui.busy && !copyBusy,
                                onClick = { model.setFormat(format) },label = { Text(format.name) })
                        }
                    }
                    Text("单次最多 1000 章、正文 8 MB。DOCX 保留标题层级、空行和缩进，正文内容预览共用下方入口。",style = MaterialTheme.typography.bodySmall)
                }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        QuietTextButton(enabled = !ui.busy, onClick = model::selectAll) { Text(stringResource(R.string.export_select_all)) }
                        QuietTextButton(enabled = !ui.busy, onClick = model::selectNone) { Text(stringResource(R.string.export_select_none)) }
                        Spacer(Modifier.weight(1f))
                        QuietTextButton(enabled = !ui.busy && ui.selected.isNotEmpty(), onClick = { scope.launch {
                            if (model.prepare() != null) previewVisible = true
                        } }) { Text(stringResource(R.string.export_preview)) }
                    }
                }
                item {
                    AppSecondaryButton(enabled = !ui.busy && !copyBusy && ui.selected.isNotEmpty(), onClick = {
                        copyBusy = true; copyNotice = null
                        scope.launch {
                            try {
                                val prepared = model.prepare(com.qingling.writing.core.model.ExportFormat.TXT)
                                if (prepared != null) {
                                    val text = withContext(Dispatchers.IO) {
                                        require(prepared.staged.file.length() <= 512 * 1024) { "Clipboard size limit" }
                                        prepared.staged.file.readText(Charsets.UTF_8)
                                    }
                                    clipboard.setText(AnnotatedString(text)); copyNotice = "已复制所选章节，按当前标题选项和目录顺序合并"
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                            catch (_: Exception) { copyNotice = "内容过大或复制未成功，请使用 TXT 导出（剪贴板上限 512 KB）。" }
                            finally { copyBusy = false }
                        }
                    }) { Text("复制所选章节（全选可复制整书）") }
                    copyNotice?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
                item {
                    ExportOption(stringResource(R.string.export_include_book), ui.includeBook, !ui.busy) {
                        model.setOptions(it, ui.includeVolumes, ui.includeChapters)
                    }
                    ExportOption(stringResource(R.string.export_include_volume), ui.includeVolumes, !ui.busy) {
                        model.setOptions(ui.includeBook, it, ui.includeChapters)
                    }
                    ExportOption(stringResource(R.string.export_include_chapter), ui.includeChapters, !ui.busy) {
                        model.setOptions(ui.includeBook, ui.includeVolumes, it)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                }
                ui.errorDetail?.let { detail -> item { Text(detail,color = MaterialTheme.colorScheme.error) } }
                ui.notice?.let { notice -> item {
                    Text(stringResource(when (notice) {
                        ExportNotice.SAVED -> R.string.export_saved
                        ExportNotice.CANCELLED -> R.string.export_cancelled
                        ExportNotice.SHARE_OPENED -> R.string.export_share_opened
                        ExportNotice.EMPTY -> R.string.export_empty_selection
                        ExportNotice.CHANGED -> R.string.export_changed
                        ExportNotice.MISSING -> R.string.export_pending_missing
                        ExportNotice.FAILED -> R.string.export_failed
                    }), color = if (notice in listOf(ExportNotice.FAILED, ExportNotice.MISSING, ExportNotice.CHANGED))
                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp))
                } }
                if (ui.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (ui.volumes.none { it.chapters.isNotEmpty() }) item {
                    Text(stringResource(R.string.export_empty_book), Modifier.padding(vertical = 24.dp))
                }
                ui.volumes.forEach { volume ->
                    item(key = "volume:${volume.id}") {
                        val count = volume.chapters.count { it.id in ui.selected }
                        val checked = when { count == 0 -> ToggleableState.Off
                            count == volume.chapters.size -> ToggleableState.On
                            else -> ToggleableState.Indeterminate }
                        Row(Modifier.fillMaxWidth().triStateToggleable(state = checked,
                            enabled = !ui.busy && volume.chapters.isNotEmpty(), role = Role.Checkbox,
                            onClick = { model.toggleVolume(volume.id) }).heightIn(min = 56.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            TriStateCheckbox(state = checked, onClick = null)
                            Text(volume.title, style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                            Text(stringResource(R.string.outline_chapter_count_format, volume.chapters.size),
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    items(volume.chapters, key = { "chapter:${it.id}" }) { chapter ->
                        ExportOption(chapter.title, chapter.id in ui.selected, !ui.busy,
                            modifier = Modifier.padding(start = 16.dp)) { model.toggleChapter(chapter.id) }
                    }
                }
            }
        }
    }
    val preview = ui.preview
    if (previewVisible && preview != null) AlertDialog(onDismissRequest = { previewVisible = false; model.dismissPreview() },
        title = { Text(stringResource(R.string.export_preview)) },
        text = { Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.export_order_note), style = MaterialTheme.typography.labelLarge)
            Text(preview.chapterTitles.joinToString("\n"), style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text(preview.text.ifEmpty { stringResource(R.string.export_blank_preview) })
            if (preview.truncated) Text(stringResource(R.string.export_preview_truncated),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }, confirmButton = { ConfirmAction(onClick = { previewVisible = false; model.dismissPreview() }) {
            Text(stringResource(R.string.action_close))
        } })
}

@Composable
private fun ExportOption(title: String, checked: Boolean, enabled: Boolean,
    modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp)
        .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(title, Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyLarge)
    }
}
