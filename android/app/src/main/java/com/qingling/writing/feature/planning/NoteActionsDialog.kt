package com.qingling.writing.feature.planning
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.design.*
import com.qingling.writing.core.model.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

@Composable
fun NoteActionsDialog(entry:PlanningEntry,onDismiss:()->Unit,onOpenSource:(String,Int,Int)->Unit,
    onInsert:(suspend(String)->Boolean)?=null,resolutionChapterId:String="",resolutionSourceTitle:String="") {
    val container=LocalAppContainer.current;val scope=rememberCoroutineScope()
    val books by remember{container.writingRepository.observeBooks(GuestSpace.ID)}.collectAsStateWithLifecycle(initialValue=emptyList())
    var current by remember(entry.id){mutableStateOf(entry)}
    var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    var editing by androidx.compose.runtime.saveable.rememberSaveable(entry.id){mutableStateOf(false)};var moving by remember{mutableStateOf(false)};var deleting by remember{mutableStateOf(false)};var classify by remember{mutableStateOf(false)}
    var quoteExpanded by remember(entry.id){mutableStateOf(false)}
    val note=QuickNote.decode(current.body)
    fun action(block:suspend()->Unit){if(!busy){busy=true;scope.launch{try{block();error=null}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){error=e.message?:"操作失败，原文字保留"}finally{busy=false}}}}
    if(!editing) WritingSheet(if(note.foreshadow) "伏笔详情" else "便签详情",scopeLabel=books.find{it.id==current.bookId}?.title ?: "通用灵感箱",onDismiss=onDismiss,canDismiss=!busy){
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(note.sourceQuote.isNotBlank()) {Text("引用原文",style=MaterialTheme.typography.titleSmall);androidx.compose.foundation.text.selection.SelectionContainer {Text(note.sourceQuote,maxLines=if(quoteExpanded) Int.MAX_VALUE else 4,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)};TextButton(onClick={quoteExpanded=!quoteExpanded}){Text(if(quoteExpanded) "收起原文" else "展开原文")}}
            else if(note.chapterId.isNotBlank()) Text(if(note.sourceKind.isBlank()) "旧记录未保留原文引用，可在正文中重新选取建立关联" else "关联章节，未选择原文",style=MaterialTheme.typography.bodySmall)
            Text(if(note.foreshadow) "伏笔备注" else "便签备注",style=MaterialTheme.typography.titleSmall)
            Text(note.text.ifBlank {"尚未填写备注"})
            if(note.foreshadow) Text(noteTrail(current),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(note.resolved && note.resolvedChapterId.isNotBlank()) AppSecondaryButton(enabled=!busy,onClick={onDismiss();onOpenSource(note.resolvedChapterId,0,0)},modifier=Modifier.fillMaxWidth()){Text("定位回收章节")}
            if(note.sourceTitle.isNotBlank())Text("来源："+note.sourceTitle,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(note.chapterId.isNotBlank())AppSecondaryButton(enabled=!busy,onClick={action { val body=container.writingRepository.readChapter(note.chapterId).body
                if(note.sourceQuote.isBlank()) {onDismiss();onOpenSource(note.chapterId,note.offset.coerceIn(0,body.length),note.offset.coerceIn(0,body.length))}
                else { val start=QuoteLocator.find(body,note);check(start!=null){"原文已变化或存在多处匹配，无法可靠定位；请重新选取原文建立关联"};onDismiss();onOpenSource(note.chapterId,start,start+note.sourceQuote.length) } }},modifier=Modifier.fillMaxWidth()){Text(if(note.sourceQuote.isBlank()) if(note.sourceKind.isBlank()) "打开来源章节（旧位置）" else "打开关联章节" else "查看并选中原文")}
            AppSecondaryButton(enabled=!busy,onClick={editing=true},modifier=Modifier.fillMaxWidth()){Text("编辑内容")}
            AppSecondaryButton(enabled=!busy,onClick={moving=true},modifier=Modifier.fillMaxWidth()){Text("改归属")}
            AppSecondaryButton(enabled=!busy,onClick={action{current=container.planning.updateNote(current,note.clearResolution().copy(foreshadow=!note.foreshadow))}},modifier=Modifier.fillMaxWidth()){Text(if(note.foreshadow)"转回便签"else"转为伏笔")}
            if(note.foreshadow)AppSecondaryButton(enabled=!busy,onClick={action{current=container.planning.updateNote(current,if(note.resolved) note.clearResolution() else note.resolve(resolutionChapterId,resolutionSourceTitle))}},modifier=Modifier.fillMaxWidth()){Text(if(note.resolved)"重新标记待回收"else"标记已回收")}
            if(onInsert!=null)AppSecondaryButton(enabled=!busy&&note.text.isNotBlank(),onClick={action{check(onInsert(note.text)){"正文尚未保存成功，未插入"};onDismiss()}},modifier=Modifier.fillMaxWidth()){Text("插入备注到当前章")}
            AppSecondaryButton(enabled=!busy,onClick={classify=true},modifier=Modifier.fillMaxWidth()){Text("复制引用与备注到设定本")}
            TextButton(enabled=!busy,onClick={deleting=true}){Text("删除这条灵感",color=MaterialTheme.colorScheme.error)}
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }
    }
    if(editing)QuickNoteSheet(onDismiss={editing=false},existing=current,sourceTitle=note.sourceTitle,onSaved={action{current=checkNotNull(container.planning.load(current.id)){"记录已被移除"}}})
    if(moving)NoteOwnerDialog(books,current.bookId,onDismiss={moving=false},onConfirm={target->action{current=container.planning.moveNote(current,target);moving=false}})
    if(deleting)ConfirmDialog("删除这条灵感？","只移除此条灵感，原章节不变；已有备份中的副本保留。","确认删除",onConfirm={action{container.planning.updateNote(current,note.copy(deleted=true));deleting=false;onDismiss()}},onDismiss={deleting=false})
    if(classify)AlertDialog(onDismissRequest={if(!busy)classify=false},title={Text("复制到设定本")},text={Column {
        if(current.bookId.isBlank())Text("请先将灵感归入一本书，再存入它的设定本。")else listOf(PlanningKind.CHARACTER,PlanningKind.PLACE,PlanningKind.FACTION,PlanningKind.ITEM,PlanningKind.TERM).forEach{kind->AppSecondaryButton(enabled=!busy,onClick={action{container.planning.save(PlanningEntry(UUID.randomUUID().toString(),current.bookId,null,null,kind.name,current.title,listOf(if(note.sourceQuote.isNotBlank()) "引用原文：\n"+note.sourceQuote else "",if(note.text.isNotBlank()) "备注：\n"+note.text else "").filter{it.isNotBlank()}.joinToString("\n\n"),Instant.now().toString()));classify=false}}){Text(kind.label)}}
    }},confirmButton={TextButton(enabled=!busy,onClick={classify=false}){Text("关闭")}})
}
