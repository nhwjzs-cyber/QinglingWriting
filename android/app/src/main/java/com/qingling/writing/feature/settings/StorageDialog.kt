package com.qingling.writing.feature.settings
import com.qingling.writing.core.design.EmptyState
import com.qingling.writing.core.design.ListRow
import com.qingling.writing.core.design.WritingIcons
import com.qingling.writing.core.design.QuietTextButton
import com.qingling.writing.core.design.ConfirmAction
import com.qingling.writing.core.design.AppSecondaryButton

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.design.BackButton
import com.qingling.writing.data.AutoCleanupPreview
import com.qingling.writing.data.StorageReport
import com.qingling.writing.core.files.ExpiredExportCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun StorageDialog(onDismiss: ()->Unit,onBackup: ()->Unit) {
    val container=LocalAppContainer.current
    val context=LocalContext.current
    val keep by container.settingsStore.autoHistoryLimit.collectAsStateWithLifecycle(initialValue=20)
    val scope=rememberCoroutineScope()
    var report by remember {mutableStateOf<StorageReport?>(null)}
    var preview by remember {mutableStateOf<AutoCleanupPreview?>(null)}
    var exportPreview by remember {mutableStateOf<ExpiredExportCache.Preview?>(null)}
    var busy by remember {mutableStateOf(false)}
    var message by remember {mutableStateOf<String?>(null)}
    fun refresh() {if(busy)return;busy=true;scope.launch {
        try {report=container.storageManager.report()}
        catch(e: CancellationException){throw e}
        catch(_: Exception){message="读取存储信息失败，请稍后重试。"}
        finally {busy=false}
    }}
    LaunchedEffect(Unit) {refresh()}
    fun size(value: Long)=Formatter.formatFileSize(context,value)
    Dialog(onDismissRequest={if(!busy)onDismiss()},properties=DialogProperties(usePlatformDefaultWidth=false,dismissOnClickOutside=false,dismissOnBackPress=!busy)) {
        BackHandler(enabled=busy) { }
        Scaffold(Modifier.fillMaxSize(),topBar={TopAppBar(title={Text("历史与存储")},navigationIcon={BackButton(onClick={if(!busy)onDismiss()})})}) {padding->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Text("本机数据",style=MaterialTheme.typography.titleLarge)
                report?.let {r->
                    Text("数据库文件（含 WAL）  ${size(r.databaseBytes)}")
                    Text("正文内容估算  ${size(r.manuscriptBytes)}")
                    Text("历史内容估算  ${size(r.historyBytes)} · ${r.historyCount} 份")
                    Text("本机停笔备份  ${size(r.backupBytes)} · ${r.backupCount} 份")
                    Text("应用缓存  ${size(r.cacheBytes)}")
                    Text("其中：导出临时文件  ${size(r.exportCacheBytes)}")
                    Text("其中：备份临时文件  ${size(r.backupCacheBytes)}")
                    Text("其中：其他缓存  ${size(r.otherCacheBytes)}")
                    Text("设备可用空间  ${size(r.availableBytes)}")
                } ?: if(busy) Text("正在读取…") else EmptyState("暂无法读取存储信息",subtitle="请稍后刷新重试")
                Text("正文和历史为 UTF-8 内容估算，已包含在数据库文件中；不要相加。数据库文件含空闲页，清理历史后文件大小未必立即减小。外部目录备份、导出文件与系统备份不计在这里。",style=MaterialTheme.typography.bodySmall)
                Text("以上三项临时文件包含在应用缓存总量中，不要重复相加。备份临时文件可能正在使用，其他缓存及停笔备份不会在此清理。",style=MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text("导出临时文件",style=MaterialTheme.typography.titleLarge)
                Text("仅可预览和清理超过 7 天的 TXT、DOCX 分享临时副本；可能导致旧分享链接失效。正文、历史、备份、已保存的导出文件和其他缓存保留。",style=MaterialTheme.typography.bodyMedium)
                AppSecondaryButton(enabled=!busy,onClick={busy=true;message=null;scope.launch {
                    try {exportPreview=container.storageManager.previewExportCache()}
                    catch(e: CancellationException){throw e}
                    catch(e: Exception){message=e.message ?: "无法预览导出缓存。"}
                    finally {busy=false}
                }}) {Text("预览旧导出临时文件")}
                HorizontalDivider()
                Text("自动历史",style=MaterialTheme.typography.titleLarge)
                Text("当前每章保留最近 $keep 份；设置中可调整。可预览并清理超出该数量的旧自动版本，包括回收站章节。手动版本、恢复前版本、正文和所有备份保留。",style=MaterialTheme.typography.bodyMedium)
                AppSecondaryButton(enabled=!busy,onClick={busy=true;message=null;scope.launch {
                    try {preview=container.storageManager.preview(keep)}
                    catch(e: CancellationException){throw e}
                    catch(e: Exception){message=e.message ?: "无法预览，请稍后重试。"}
                    finally {busy=false}
                }}) {Text("预览旧自动历史")}
                ListRow(title="查看备份与恢复",subtitle="导出独立备份或恢复为新副本",icon=WritingIcons.Backup,enabled=!busy,onClick={onDismiss();onBackup()})
                QuietTextButton(enabled=!busy,onClick={message=null;refresh()}) {Text("刷新数据")}
                message?.let {Text(it,color=MaterialTheme.colorScheme.primary)}
                if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        preview?.let {p->AlertDialog(onDismissRequest={if(!busy)preview=null},title={Text("清理旧自动历史？")},text={
            Text(if(p.ids.isEmpty()) "每章均没有超出 ${p.keep} 份的自动版本，无需清理。" else "将永久清理 ${p.ids.size} 份旧自动版本（内容约 ${size(p.estimatedBytes)}），每章保留最近 ${p.keep} 份。包含回收站章节；手动版本、恢复前版本、正文及备份不受影响。此操作无法撤销。")
        },confirmButton={ConfirmAction(enabled=!busy && p.ids.isNotEmpty(),onClick={busy=true;message=null;scope.launch {
            try {val count=container.storageManager.apply(p);preview=null;report=container.storageManager.report();message="已清理 $count 份旧自动历史。"}
            catch(e: CancellationException){throw e}
            catch(e: Exception){preview=null;message=e.message ?: "清理未完成，请重试。"}
            finally {busy=false}
        }}){Text("确认清理")}},dismissButton={QuietTextButton(enabled=!busy,onClick={preview=null}){Text(if(p.ids.isEmpty()) "关闭" else "取消")}})}
        exportPreview?.let { p -> AlertDialog(onDismissRequest={if(!busy)exportPreview=null},title={Text("清理旧导出临时文件？")},text={
            Text(if(p.items.isEmpty()) "没有符合条件的旧导出临时文件。" else "将永久删除 ${p.items.size} 个超过 7 天的导出临时副本（约 ${size(p.bytes)}）；旧分享链接可能失效。正文、历史、备份和已保存的导出文件不会删除。")
        },confirmButton={ConfirmAction(enabled=!busy && p.items.isNotEmpty(),onClick={busy=true;message=null;scope.launch {
            try {val count=container.storageManager.clearExportCache(p);exportPreview=null;report=container.storageManager.report();message="已清理 $count 个旧导出临时文件。"}
            catch(e: CancellationException){throw e}
            catch(e: Exception){exportPreview=null;message=e.message ?: "清理未完成，请刷新后重试。"}
            finally {busy=false}
        }}){Text("确认清理")}},dismissButton={QuietTextButton(enabled=!busy,onClick={exportPreview=null}){Text(if(p.items.isEmpty()) "关闭" else "取消")}})}
    }
}
