package com.qingling.writing.feature.history
import com.qingling.writing.core.design.ConfirmAction

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.model.*
import kotlinx.coroutines.*

private data class DiffPreview(val rows: List<DiffLine>,val truncated: Boolean)

@Composable
internal fun RevisionDiffDialog(chapterId: String, revision: Revision, onDismiss: () -> Unit) {
    val repository = LocalAppContainer.current.writingRepository
    var preview by remember(revision.id) { mutableStateOf<DiffPreview?>(null) }
    var error by remember(revision.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(revision.id) {
        try {
            preview = withContext(Dispatchers.Default) {
                val latest = repository.readChapter(chapterId).body
                val limited = revision.body.length > 100_000 || latest.length > 100_000
                val all = RevisionDiff.compare(revision.body.take(100_000), latest.take(100_000))
                DiffPreview(all.take(800),limited || all.size > 800)
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "无法读取当前章节，请关闭后重试。" }
    }
    com.qingling.writing.core.design.WritingSheet("历史与当前对比",onDismiss=onDismiss) { Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("− 红色：历史中有、当前已删除\n＋ 绿色：当前新增。仅比较正文，不会修改内容。", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it,color = MaterialTheme.colorScheme.error) }
            if (preview == null && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            preview?.let { p ->
                if (p.truncated) Text("大稿仅展示前 10 万字符、最多 800 行；完整内容仍保留。",color = MaterialTheme.colorScheme.primary)
                if (p.rows.none { it.kind != DiffKind.SAME }) Text(if (p.truncated) "展示范围内一致" else "正文完全一致")
                LazyColumn(Modifier.heightIn(max = 420.dp),verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(p.rows) { _, row ->
                        val prefix = when(row.kind) { DiffKind.SAME -> "  "; DiffKind.REMOVED -> "− "; DiffKind.ADDED -> "＋ " }
                        Text(prefix+row.text, color = when(row.kind) {
                            DiffKind.SAME -> MaterialTheme.colorScheme.onSurfaceVariant
                            DiffKind.REMOVED -> if (MaterialTheme.colorScheme.surface.luminanceSafe() < .5f) Color(0xFFFFB4AB) else Color(0xFF9C2920)
                            DiffKind.ADDED -> if (MaterialTheme.colorScheme.surface.luminanceSafe() < .5f) Color(0xFFA7D6B7) else Color(0xFF23613B)
                        },style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        } }
}
private fun Color.luminanceSafe(): Float = .2126f*red+.7152f*green+.0722f*blue
