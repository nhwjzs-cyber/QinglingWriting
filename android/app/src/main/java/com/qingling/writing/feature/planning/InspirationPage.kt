package com.qingling.writing.feature.planning

import androidx.compose.foundation.clickable
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.design.*
import com.qingling.writing.core.model.*
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

internal fun noteAge(entry:PlanningEntry,now:Instant=Instant.now()):Long = try {
    Duration.between(Instant.parse(QuickNote.decode(entry.body).capturedAt.ifBlank{entry.updatedAt}),now).toDays().coerceAtLeast(0)
}catch(_:Exception){0}

@Composable
internal fun NoteOwnerDialog(books:List<BookSummary>,initial:String,onDismiss:()->Unit,onConfirm:(String)->Unit,excluded:String?=null) {
    var selected by rememberSaveable{mutableStateOf(initial)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("归入哪里？")},text={
        Column(Modifier.heightIn(max=340.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            AppChoice(selected.isEmpty(),{selected=""},{Text("通用灵感箱")},Modifier.fillMaxWidth())
            books.filter{it.id!=excluded}.forEach{book->AppChoice(selected==book.id,{selected=book.id},{Text(book.title+(if(book.archived)" · 已归档"else""))},Modifier.fillMaxWidth())}
        }
    },confirmButton={Button(onClick={onConfirm(selected)}){Text("确认归属")}},dismissButton={TextButton(onClick=onDismiss){Text("取消")}})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspirationPage(onOpenSource:(String,Int,Int)->Unit,modifier:Modifier=Modifier,onClose:(()->Unit)?=null,onOpenSettings:(()->Unit)?=null) {
    val container=LocalAppContainer.current
    val entries by remember { container.planning.inbox() }.collectAsStateWithLifecycle(initialValue=emptyList())
    val books by remember { container.writingRepository.observeBooks(GuestSpace.ID) }.collectAsStateWithLifecycle(initialValue=emptyList())
    var view by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(listOf<String>()) }
    var allShown by rememberSaveable { mutableStateOf(listOf<String>()) }
    var initialized by rememberSaveable { mutableStateOf(false) }
    var quick by rememberSaveable { mutableStateOf(false) }
    var quickForeshadow by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    val detail=entries.firstOrNull {it.id==detailId}
    LaunchedEffect(books) {
        if(!initialized && books.isNotEmpty()) {
            val active=try { container.writingRepository.resolveLastActiveChapter()?.let { container.writingRepository.readChapter(it).bookId } }
                catch(e:kotlinx.coroutines.CancellationException) { throw e } catch(_:Exception) { null }
            expanded=listOf(active ?: books.firstOrNull { !it.archived }?.id.orEmpty());initialized=true
        }
    }
    val visible=entries.filter { entry ->
        val note=QuickNote.decode(entry.body)
        query.isBlank() || entry.title.contains(query,true) || note.text.contains(query,true) || note.sourceQuote.contains(query,true) || note.sourceTitle.contains(query,true) || books.firstOrNull { it.id==entry.bookId }?.title?.contains(query,true)==true
    }
    val allDebts=entries.filter {QuickNote.decode(it.body).let {n->n.foreshadow&&!n.resolved}}
    val debts=visible.filter { QuickNote.decode(it.body).let { n->n.foreshadow && !n.resolved } }
        .sortedBy { runCatching { Instant.parse(QuickNote.decode(it.body).capturedAt.ifBlank { it.updatedAt }) }.getOrDefault(Instant.EPOCH) }
    val overdue=allDebts.count { noteAge(it)>=30 }
    val sorted=books.sortedWith(compareBy<BookSummary> { it.archived }.thenByDescending { it.updatedAt })
    val drawerIds=(sorted.filterNot { it.archived }.map { it.id }+""+sorted.filter { it.archived }.map { it.id })
        .filter { id->query.isBlank() || visible.any { it.bookId==id } }
    Scaffold(modifier.fillMaxSize(),topBar={
        TopAppBar(title={Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Icon(androidx.compose.ui.res.painterResource(com.qingling.writing.R.drawable.ic_brand_mark),null,Modifier.size(25.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("轻灵写作",fontSize=19.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
        }},actions={
            ChromeAction(androidx.compose.material.icons.Icons.Filled.Search,"搜索灵感",{searchOpen=!searchOpen})
            if(onOpenSettings!=null) ChromeAction(androidx.compose.material.icons.Icons.Filled.Settings,"设置",onOpenSettings)
            if(onClose!=null) TextButton(onClick=onClose) { Text("返回") }
            Spacer(Modifier.width(12.dp))
        },colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background))
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(),contentPadding=PaddingValues(horizontal=26.dp,vertical=12.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(bottom=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("灵感",Modifier.weight(1f),fontFamily=androidx.compose.ui.text.font.FontFamily.Serif,fontSize=23.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold)
                    TextButton(onClick={quickForeshadow=false;quick=true},contentPadding=PaddingValues(horizontal=6.dp)) { Text("＋ 记灵感",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                    TextButton(onClick={quickForeshadow=true;quick=true},contentPadding=PaddingValues(horizontal=6.dp)) { Text("＋ 记伏笔",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Surface(Modifier.fillMaxWidth().clickable { view=1 },shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().heightIn(min=42.dp).padding(horizontal=14.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text(if(overdue>0)"●"else"○",fontSize=9.sp,color=if(overdue>0)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if(overdue>0)overdue.toString()+" 条伏笔超过 30 天未回收"else if(allDebts.isNotEmpty())allDebts.size.toString()+" 条伏笔待回收"else"伏笔已全部闭环，剧情推进很扎实",Modifier.weight(1f).padding(horizontal=8.dp),fontSize=12.sp)
                        Text("去看看 ›",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if(searchOpen || query.isNotBlank()) SearchField(query,{query=it.take(200)},"搜索灵感、书名或来源",modifier=Modifier.fillMaxWidth().padding(top=12.dp))
                if(query.isNotBlank()) Text("搜索匹配 "+visible.size+" 条 · 全局待回收 "+allDebts.size+" 条",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                FilterChips(listOf(0,1),view,{if(it==0) "收纳 · 按书" else (if(query.isBlank()) "欠账 · 全书 " else "欠账 · 匹配 ")+debts.size},{view=it},Modifier.padding(top=14.dp,bottom=16.dp))
            }
            if(view==0) {
                if(drawerIds.isEmpty()) item { EmptyState("没有匹配的灵感",subtitle="试试其他书名、来源或关键词") }
                items(drawerIds,key={"drawer-"+it}) { id ->
                    val book=books.firstOrNull { it.id==id }
                    val notes=visible.filter { it.bookId==id }.sortedWith(compareByDescending<PlanningEntry> { QuickNote.decode(it.body).let { n->n.foreshadow && !n.resolved } }.thenByDescending { it.updatedAt })
                    val open=id in expanded
                    val index=drawerIds.indexOf(id)
                    val shape=androidx.compose.foundation.shape.RoundedCornerShape(topStart=if(index==0)20.dp else 0.dp,topEnd=if(index==0)20.dp else 0.dp,
                        bottomStart=if(index==drawerIds.lastIndex)20.dp else 0.dp,bottomEnd=if(index==drawerIds.lastIndex)20.dp else 0.dp)
                    Surface(shape=shape,color=MaterialTheme.colorScheme.surface) {
                        Column(Modifier.fillMaxWidth().animateContentSize(if(LocalMotionEnabled.current)AppMotion.panelSpring()else androidx.compose.animation.core.snap()).padding(horizontal=18.dp)) {
                            Row(Modifier.fillMaxWidth().heightIn(min=80.dp).clickable { expanded=if(open)expanded-id else expanded+id },verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                    Text(book?.title ?: "通用灵感箱",style=MaterialTheme.typography.titleMedium,maxLines=2)
                                    val pending=notes.count { QuickNote.decode(it.body).let { n->n.foreshadow && !n.resolved } }
                                    Text("便签 "+notes.count { !QuickNote.decode(it.body).foreshadow }+" · 伏笔 "+notes.count { QuickNote.decode(it.body).foreshadow }+(if(pending>0)" · 未回收 "+pending else if(id.isEmpty())" · 不归属任何书"else"")+(if(book?.archived==true)" · 已归档"else""),
                                        style=MaterialTheme.typography.labelSmall,color=if(pending>0)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(if(open)"⌃"else"›",color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if(open) {
                                if(notes.isEmpty()) EmptyState("还没有灵感",subtitle="随时记下一个念头，稍后再来整理")
                                (if(id in allShown)notes else notes.take(4)).forEach { entry->
                                    NoteRow(entry,{detailId=entry.id})
                                    val quote=QuickNote.decode(entry.body).sourceQuote
                                    val hit=if(query.isNotBlank()) quote.indexOf(query,ignoreCase=true) else -1
                                    if(hit>=0) Text("原文："+quote.substring((hit-24).coerceAtLeast(0),minOf(quote.length,hit+query.length+64)),maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if(notes.size>4) TextButton(onClick={allShown=if(id in allShown)allShown-id else allShown+id}) { Text(if(id in allShown)"收起"else"展开全部 "+notes.size+" 条 ›",color=MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            if(index<drawerIds.lastIndex) HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.35f))
                        }
                    }
                }
                item { Text("写作中打开灵感，只看当前书的那一屉。",Modifier.padding(top=22.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(debts,key={it.id}) { entry ->
                    val note=QuickNote.decode(entry.body)
                    Surface(Modifier.fillMaxWidth().padding(bottom=10.dp),shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surface) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Row { Text(entry.title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);Text(noteAge(entry).toString()+" 天",fontSize=11.sp,color=MaterialTheme.colorScheme.error) }
                            Text(if(note.sourceQuote.isNotBlank()) "原文："+note.sourceQuote else note.text.takeIf{it!=entry.title}.orEmpty(),maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(noteTrail(entry),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Row { TextButton(onClick={detailId=entry.id}){Text("查看")};if(note.chapterId.isNotBlank())TextButton(onClick={detailId=entry.id}){Text("定位 ›")} }
                        }
                    }
                }
                val recovered=visible.filter { QuickNote.decode(it.body).let { n->n.foreshadow && n.resolved } }.sortedByDescending { it.updatedAt }.take(5)
                if(debts.isEmpty() && recovered.isEmpty()) item { EmptyState(if(query.isBlank()) "还没有伏笔记录" else "没有匹配的伏笔",subtitle=if(query.isBlank()) "从正文选取一段，记下第一条伏笔。" else "试试其他关键词。") }
                if(recovered.isNotEmpty())item { Text("最近回收",style=MaterialTheme.typography.titleMedium) }
                items(recovered,key={"recovered-"+it.id}) { NoteRow(it,{detailId=it.id}) }
            }
        }
    }
    if(quick) QuickNoteSheet(onDismiss={quick=false},foreshadow=quickForeshadow)
    detail?.let { NoteActionsDialog(it,onDismiss={detailId=null},onOpenSource=onOpenSource) }
}

internal fun noteTrail(entry: PlanningEntry): String {
    val note = QuickNote.decode(entry.body)
    val planted = "埋于 " + note.sourceTitle.ifBlank { "未关联章节" }
    if (!note.resolved) return planted + " · 已埋 " + noteAge(entry) + " 天"
    val destination = note.resolvedSourceTitle.ifBlank {
        runCatching { Instant.parse(note.resolvedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString() }.getOrDefault("")
    }
    return planted + if (destination.isNotBlank()) " → 收于 " + destination else " · 已回收（旧记录未记回收位置）"
}

@Composable
internal fun NoteRow(entry:PlanningEntry,onClick:()->Unit) {
    val note=QuickNote.decode(entry.body)
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically){
        Text(if(note.foreshadow)if(note.resolved)"✓"else"○"else"≡",color=if(note.foreshadow&&!note.resolved)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(entry.title,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium)
            Text(if(note.foreshadow) noteTrail(entry) else note.sourceTitle.ifBlank { "未关联章节" },maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(if(note.foreshadow)if(note.resolved)"已回收"else"未回收"else relativeEditTime(entry.updatedAt),style=MaterialTheme.typography.labelSmall,color=if(note.foreshadow&&!note.resolved)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun BookInspirationSheet(bookId:String,onDismiss:()->Unit,onOpenGlobal:()->Unit,onOpenSource:(String,Int,Int)->Unit,
    currentChapterId:String?=null,initialChapterId:String?=null,initialTab:Int=0,onInsert:(suspend(String)->Boolean)?=null,
    onCapture:((Boolean)->Unit)?=null,currentSourceTitle:String="") {
    val container=LocalAppContainer.current
    val notes by remember{container.planning.inbox()}.collectAsStateWithLifecycle(initialValue=emptyList())
    val title by remember(bookId){container.outlineRepository.observeBookTitle(bookId)}.collectAsStateWithLifecycle(initialValue="")
    val scoped=notes.filter{it.bookId==bookId} // Scope is a data filter, not just a title.
    var tab by rememberSaveable{mutableIntStateOf(initialTab)}
    var chapterOnly by rememberSaveable{mutableStateOf(initialChapterId)}
    var detailId by rememberSaveable{mutableStateOf<String?>(null)};val detail=notes.firstOrNull {it.id==detailId};var quick by rememberSaveable{mutableStateOf(false)}
    var quickForeshadow by rememberSaveable { mutableStateOf(false) }
    fun createNote(foreshadow:Boolean) { if(onCapture!=null) { onDismiss();onCapture(foreshadow) } else { quickForeshadow=foreshadow;quick=true } }
    val captureChapter=currentChapterId ?: initialChapterId
    val captureSource by produceState(currentSourceTitle,captureChapter,title) {
        value=currentSourceTitle.ifBlank { captureChapter?.let { id->try {title.orEmpty()+" · "+container.writingRepository.readChapter(id).title}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){null} } ?: title.orEmpty() }
    }
    val visible=scoped.filter{val n=QuickNote.decode(it.body);(chapterOnly==null||n.chapterId==chapterOnly)&&when(tab){1->n.foreshadow&&!n.resolved;2->n.foreshadow&&n.resolved;else->true}}
    WritingSheet("本书灵感",scopeLabel=title.orEmpty(),onDismiss=onDismiss,footer={TextButton(onClick=onOpenGlobal,modifier=Modifier.fillMaxWidth()){Text("全部书的灵感在主页灵感页 ›",color=MaterialTheme.colorScheme.onSurfaceVariant)}}){
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item{Text("只看这一本书 · 共 "+scoped.size+" 条",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Row{TextButton(onClick={createNote(false)}){Text("＋ 记灵感")};TextButton(onClick={createNote(true)}){Text("＋ 记伏笔")}};FilterChips(listOf(0,1,2),tab,{listOf("全部","待回收","已回收")[it]},{tab=it});if(chapterOnly!=null)TextButton(onClick={chapterOnly=null}){Text("仅本章 · 显示本书全部")}}
            if(visible.isEmpty())item{EmptyState(if(tab==1) "本书没有待回收伏笔" else if(tab==2) "还没有回收记录" else "这里还没有灵感",subtitle="用上方的记灵感或记伏笔按钮，留住此刻的想法")}
            items(visible,key={it.id}){entry->Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){Column(Modifier.padding(horizontal=14.dp)){NoteRow(entry,{detailId=entry.id});Text(QuickNote.decode(entry.body).let{n->if(n.sourceQuote.isNotBlank()) "原文："+n.sourceQuote else n.text.takeIf{it!=entry.title}.orEmpty()},maxLines=2,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(bottom=12.dp))}}}
        }
    }
    if(quick)QuickNoteSheet(onDismiss={quick=false},bookId=bookId,chapterId=(currentChapterId ?: initialChapterId).orEmpty(),sourceTitle=captureSource,foreshadow=quickForeshadow)
    detail?.let{entry->NoteActionsDialog(entry,onDismiss={detailId=null},onOpenSource=onOpenSource,onInsert=onInsert,resolutionChapterId=currentChapterId.orEmpty(),resolutionSourceTitle=currentSourceTitle)}
}
