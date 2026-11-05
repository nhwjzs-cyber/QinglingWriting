package com.qingling.writing.feature.recovery
import com.qingling.writing.core.design.QuietTextButton
import com.qingling.writing.core.design.AppSecondaryButton
import com.qingling.writing.core.design.AppTextField
import com.qingling.writing.core.design.AppChoice

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.design.BackButton
import com.qingling.writing.core.model.*
import kotlinx.coroutines.*

private data class TxtSource(val name: String, val bytes: ByteArray)
private data class ImportPreview(val sources: List<TxtSource>, val encoding: String, val split: Boolean, val pattern: String?,
    val chapters: List<ImportedChapter>, val detected: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxtImportDialog(onDismiss: () -> Unit, onOpenBook: ((String)->Unit)? = null) {
    val container = LocalAppContainer.current
    val resolver = LocalContext.current.applicationContext.contentResolver
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf(emptyList<TxtSource>()) }
    var encoding by remember { mutableStateOf("AUTO") }
    var split by remember { mutableStateOf(true) }
    var custom by remember { mutableStateOf(false) }
    var pattern by remember { mutableStateOf("第[0-9一二三四五六七八九十百千]+章.*") }
    var title by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var succeeded by remember { mutableStateOf(false) }
    var importedBook by remember {mutableStateOf<String?>(null)}
    var preview by remember { mutableStateOf<ImportPreview?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            busy = true; error = null; preview = null; files = emptyList()
            try {
                val loaded = withContext(Dispatchers.IO) {
                    require(uris.size <= 50) { "一次最多选择 50 个 TXT 文件" }
                    var remaining = TxtImport.MAX_BYTES
                    uris.mapIndexed { index, uri ->
                        ensureActive()
                        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                            if (it.moveToFirst()) it.getString(0) else null
                        } ?: "稿件${index + 1}.txt"
                        require(name.endsWith(".txt", ignoreCase = true)) { "请选择 TXT 文件" }
                        val bytes = resolver.openInputStream(uri)?.use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                require(output.size() + count <= remaining) { "所选文件总大小不能超过 5 MB" }
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        } ?: error("无法读取文件，请重新选择")
                        remaining -= bytes.size
                        TxtSource(name, bytes)
                    }
                }
                files = loaded
                title = TxtImport.title(loaded.first().name.dropLast(4))
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { error = e.message ?: "无法读取文件"
            } finally { busy = false }
        }
    }
    val activePattern = if(custom && split && files.size == 1) pattern else null
    LaunchedEffect(files, encoding, split, activePattern) {
        preview = null
        if (files.isNotEmpty()) {
            error = null
            try {
                val result = withContext(Dispatchers.Default) {
                    val decoded = files.map { TxtImport.decode(it.bytes, encoding) }
                    val chapters = files.flatMapIndexed { i, file ->
                        TxtImport.split(decoded[i].text, file.name.dropLast(4), split && files.size == 1,activePattern)
                    }
                    require(chapters.size <= TxtImport.MAX_CHAPTERS) { "最多支持 1000 章" }
                    ImportPreview(files, encoding, split, activePattern, chapters, decoded.map { it.encoding }.distinct().joinToString(" / "))
                }
                preview = result
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { error = "解码或分章失败，请调整编码：${e.message.orEmpty()}" }
        }
    }
    val current = preview?.takeIf { it.sources == files && it.encoding == encoding && it.split == split && it.pattern == activePattern }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false,
        dismissOnBackPress = !busy, dismissOnClickOutside = false)) {
        Scaffold(Modifier.fillMaxSize().imePadding(), topBar = {
            TopAppBar(title = { Text("TXT 导入") }, navigationIcon = { BackButton(onClick = { if (!busy) onDismiss() }) })
        }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (succeeded) {
                    Text("✓ 导入完成",style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.primary)
                    Text("《"+title.trim()+"》已保存到本机",style=MaterialTheme.typography.titleMedium)
                    Card {Row(Modifier.fillMaxWidth().padding(20.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
                        Text((current?.chapters?.size ?: 0).toString()+" 章");Text((current?.chapters?.sumOf{com.qingling.writing.domain.validation.WordCounter.count(it.body)} ?: 0).toString()+" 字");Text(current?.detected.orEmpty())
                    }}
                    Text("✓ 原文件未改\n✓ 已按预览生成章节\n✓ 后续作品备份会包含本书",color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(onOpenBook!=null) Button(onClick={importedBook?.let{onDismiss();onOpenBook(it)}},modifier=Modifier.fillMaxWidth().height(54.dp)){Text("打开新书")}
                    AppSecondaryButton(onClick=onDismiss,modifier=Modifier.fillMaxWidth()){Text("回到首页")}
                } else {
                    Text(if(files.isEmpty()) "选择文件 → 预览确认" else "选择文件 ✓ → 预览确认",style=MaterialTheme.typography.labelLarge)
                    Text("只创建新作品，不覆盖旧稿。支持 UTF-8 / GBK，总计不超过 5 MB、50 个文件、1000 章。",
                        style = MaterialTheme.typography.bodySmall)
                    AppSecondaryButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !busy) { Text("选择 TXT 文件（可多选）") }
                    if (files.isNotEmpty()) {
                        files.forEachIndexed {index,file -> Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                                Text(file.name+" · "+file.bytes.size/1024+" KB",Modifier.weight(1f),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                QuietTextButton(enabled=!busy,onClick={files=files.filterIndexed{i,_->i!=index}}){Text("移除")}
                            }
                        }}
                        AppTextField(title, { title = it.take(160) }, label = { Text("新作品名称") }, singleLine = true,
                            enabled = !busy, modifier = Modifier.fillMaxWidth())
                        Row {
                            listOf("AUTO" to "自动", "UTF-8" to "UTF-8", "GB18030" to "GBK").forEach { (value, label) ->
                                AppChoice(selected = encoding == value, onClick = { if (!busy) encoding = value },
                                    label = { Text(label) }, modifier = Modifier.padding(end = 8.dp))
                            }
                        }
                        if (files.size == 1) Row {
                            Checkbox(split, { if (!busy) split = it })
                            Text("识别章节标题自动分章", Modifier.padding(top = 12.dp))
                        }
                        if(files.size == 1 && split) {
                            Row { Checkbox(custom,{if(!busy) custom=it});Text("自定义分章正则",Modifier.padding(top=12.dp)) }
                            if(custom) AppTextField(pattern,{if(!busy) pattern=it.take(256)},label={Text("逐行完整匹配，例如 Chapter [0-9]+.*")},
                                supportingText={Text("标题行最多 512 字符；不匹配的文字完整保留。请检查预览。")},modifier=Modifier.fillMaxWidth())
                        }
                        Text("自动识别并非绝对准确，请确认预览没有乱码。切换编码会重新生成预览。", style = MaterialTheme.typography.bodySmall)
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (busy || (files.isNotEmpty() && current == null && error == null)) LinearProgressIndicator(Modifier.fillMaxWidth())
                    current?.let { p ->
                        Text("识别编码："+p.detected+" · 共 "+p.chapters.size+" 章 · 约 "+p.chapters.sumOf{com.qingling.writing.domain.validation.WordCounter.count(it.body)}+" 字")
                        Button(enabled = !busy && title.isNotBlank(), onClick = {
                            busy = true; error = null
                            val name = TxtImport.title(title)
                            scope.launch {
                                try {
                                    importedBook=withContext(Dispatchers.IO) { container.txtImportRepository.importBook(name, p.chapters) }
                                    title = name; succeeded = true
                                } catch (e: CancellationException) { throw e
                                } catch (_: Exception) { error = "导入未完成，已取消本次写入。请检查存储空间后重试。"
                                } finally { busy = false }
                            }
                        }) { Text("确认导入为新作品") }
                        LazyColumn(Modifier.weight(1f)) {
                            itemsIndexed(p.chapters) { i, chapter ->
                                Text("${i + 1}. ${chapter.title}", style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(top = 12.dp))
                                Text(com.qingling.writing.domain.validation.WordCounter.count(chapter.body).toString()+" 字 · "+chapter.body.take(100).ifEmpty { "（空章）" }, style = MaterialTheme.typography.bodySmall, color=if(chapter.body.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
