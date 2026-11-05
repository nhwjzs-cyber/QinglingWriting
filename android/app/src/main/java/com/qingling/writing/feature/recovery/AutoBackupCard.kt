package com.qingling.writing.feature.recovery
import com.qingling.writing.core.design.AppSecondaryButton
import com.qingling.writing.core.design.AppChoice

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.model.*
import com.qingling.writing.core.design.formatTimestamp
import kotlinx.coroutines.*

@Composable
fun AutoBackupCard(enabled: Boolean = true) {
    val container = LocalAppContainer.current
    val state by container.autoBackupStore.state.collectAsStateWithLifecycle(initialValue = AutoBackupState())
    val running by container.autoBackup.busy.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var operation by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val resolver = LocalContext.current.applicationContext.contentResolver
    val folderName by produceState("未选择备份文件夹", state.folder) {
        value = if (state.folder.isEmpty()) "未选择备份文件夹" else withContext(Dispatchers.IO) {
            runCatching {
                val tree = android.net.Uri.parse(state.folder)
                val doc = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree,
                    android.provider.DocumentsContract.getTreeDocumentId(tree))
                resolver.query(doc, arrayOf(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                } ?: "已选择备份文件夹"
            }.getOrDefault("已选择的文件夹暂时无法读取")
        }
    }
    fun act(block: suspend () -> Unit) {
        // A picker result may arrive while a scheduled backup is running; queue it through the controller mutex.
        if (operation) return
        operation = true; error = null
        scope.launch {
            try { block()
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { error = "设置未能完成，请重新选择文件夹或稍后重试。"
            } finally { operation = false }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) act { container.autoBackup.selectFolder(uri); container.autoBackup.runBackup() }
    }
    val available = enabled && !operation && !running
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("自动独立备份", style = MaterialTheme.typography.titleLarge)
            Text("${if (state.enabled) "已启用" else "未启用"} · $folderName")
            AppSecondaryButton(enabled = available, onClick = {
                try { picker.launch(null) } catch (_: Exception) { error = "无法打开文件夹选择器。" }
            }) { Text(if (state.folder.isEmpty()) "选择文件夹并启用" else "更换备份文件夹") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("定期自动备份", Modifier.weight(1f))
                Switch(state.enabled, onCheckedChange = { value -> act { container.autoBackup.configure(value, state.hours) } },
                    enabled = available && state.folder.isNotEmpty())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(6, 24).forEach { hours ->
                    AppChoice(selected = state.hours == hours, enabled = available && state.folder.isNotEmpty(),
                        onClick = { act { container.autoBackup.configure(state.enabled, hours) } }, label = { Text("约每 $hours 小时") })
                }
            }
            Text(AutoBackupPolicy.summary(state, System.currentTimeMillis()), style = MaterialTheme.typography.bodyMedium)
            if (state.lastSuccess > 0) Text("成功时间：${formatTimestamp(java.time.Instant.ofEpochMilli(state.lastSuccess).toString())}",
                style = MaterialTheme.typography.bodySmall)
            (error ?: state.error.takeIf { it.isNotEmpty() })?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (state.scheduleError.isNotEmpty()) Text(state.scheduleError, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
            if (operation || running) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button(enabled = available && state.enabled && state.folder.isNotEmpty(),
                onClick = { act { container.autoBackup.runBackup() } }) { Text("立即备份到此文件夹") }
            Text("选择文件夹后会立即尝试一次。定时任务可能被省电策略推迟；打开应用时也会检查是否到期。强行停止应用后，后台任务可能停止，重新打开后会恢复安排。",
                style = MaterialTheme.typography.bodySmall)
            Text("只备份已保存到本机的稿件、回收站和历史。备份未加密，展开上限 32 MB。每次新增文件，不覆盖或自动清理旧备份，请留意空间；关闭功能不会删除备份。文件夹可位于本机或你已配置的文件提供方。",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
