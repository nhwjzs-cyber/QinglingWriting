package com.qingling.writing.feature.bookshelf
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.design.*
import com.qingling.writing.core.model.*
import kotlinx.coroutines.launch
@Composable
fun DeleteBookDialog(book:BookSummary,onDismiss:()->Unit,onDeleted:()->Unit) {
    val container=LocalAppContainer.current;val scope=rememberCoroutineScope()
    val all by produceState<List<PlanningEntry>?>(null){container.planning.inbox().collect{value=it}}
    val books by remember{container.writingRepository.observeBooks(GuestSpace.ID)}.collectAsStateWithLifecycle(initialValue=emptyList())
    val notes=all.orEmpty().filter{it.bookId==book.id}
    var target by rememberSaveable{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text("移入回收站")},text={Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("《"+book.title+"》及其 "+book.chapterCount+" 章将移入回收站，不会自动永久删除。")
        if(notes.isNotEmpty()){
            Text("本书有 "+notes.size+" 条灵感，请先选择迁移去向",color=MaterialTheme.colorScheme.error)
            AppChoice(target.isEmpty(),{target=""},{Text("通用灵感箱")},Modifier.fillMaxWidth(),enabled=!busy)
            books.filter{it.id!=book.id}.forEach{other->AppChoice(target==other.id,{target=other.id},{Text(other.title)},Modifier.fillMaxWidth(),enabled=!busy)}
        }
        Text("灵感会先完整迁移，再移动作品；任一步失败都会取消。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={Button(enabled=!busy&&all!=null,onClick={busy=true;scope.launch{try{container.planning.moveNotesAndDeleteBook(book.id,target);onDeleted()}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){error="未完成删除，原书与灵感保留，请刷新后重试"}finally{busy=false}}},colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error.copy(alpha=.12f),contentColor=MaterialTheme.colorScheme.error)){Text(if(busy)"正在处理…"else if(notes.isEmpty())"移入回收站"else"迁移灵感并移入回收站")}},dismissButton={TextButton(enabled=!busy,onClick=onDismiss){Text("取消")}})
}
