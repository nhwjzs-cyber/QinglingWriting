package com.qingling.writing.feature.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.design.*
import com.qingling.writing.core.model.ChapterInfo
import kotlinx.coroutines.launch

@Composable
internal fun ChapterInfoSheet(chapterId:String,title:String,words:Long,onDismiss:()->Unit,onExport:()->Unit={}) {
    val repo=LocalAppContainer.current.writingProgress
    val scope=rememberCoroutineScope()
    var info by remember(chapterId){mutableStateOf<ChapterInfo?>(null)}
    var busy by remember(chapterId){mutableStateOf(false)}
    var error by remember(chapterId){mutableStateOf<String?>(null)}
    var loaded by remember(chapterId){mutableStateOf(false)}
    LaunchedEffect(chapterId) {
        try { repo.chapterInfo(chapterId).collect {info=it;loaded=true} }
        catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(_:Exception){loaded=true;error="章节信息暂时无法读取，请关闭后重试"}
    }
    WritingSheet("章节信息",scopeLabel=title,compact=true,onDismiss=onDismiss,canDismiss=!busy) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            val current=info
            if(current==null) EmptyState(if(loaded)"无法显示章节信息"else"正在读取章节信息",subtitle=error ?: if(loaded)"章节可能已被移入回收站"else null)
            else {
                StatRow(listOf(StatData("本章字数",com.qingling.writing.feature.bookshelf.formatWords(words),"字"),StatData("完成状态",if(current.completed)"已完成"else"草稿",isText=true)))
                StatRow(listOf(StatData("创建时间",formatTimestamp(current.createdAt),isText=true),StatData("修改时间",formatTimestamp(current.updatedAt),isText=true)))
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {Text("标记本章已完成");Text("由你手动决定，不按字数自动判定",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    Switch(current.completed,enabled=!busy,onCheckedChange={value->busy=true;error=null;scope.launch {
                        try{repo.complete(chapterId,value)}
                        catch(e:kotlinx.coroutines.CancellationException){throw e}
                        catch(_:Exception){error="完成状态未保存，请重试"}
                        finally{busy=false}
                    }})
                }
                AppSecondaryButton(enabled=!busy,onClick=onExport,modifier=Modifier.fillMaxWidth()){Text("导出本章")}
                error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            }
        }
    }
}
