package com.qingling.writing.feature.outline

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.design.*
import com.qingling.writing.core.model.*
import kotlinx.coroutines.launch

/** One stable header, two animated pages; each page/book retains its own scroll and expansion state. */
@Composable
fun OutlineBrowser(bookId: String, currentChapterId: String?, onOpenChapter: (String)->Unit,
    onClose: ()->Unit, beforeCopy: suspend ()->Boolean, modifier: Modifier=Modifier,
    onCompleted: (OutlineChapter)->Unit = {}, startInLibrary: Boolean = false, requestKey: Int = 0, onOpenSource:(String,Int,Int)->Unit={id,_,_->onOpenChapter(id)},onExport:((String,String,String)->Unit)?=null) {
    val container = LocalAppContainer.current
    val books by remember { container.writingRepository.observeBooks(GuestSpace.ID) }.collectAsStateWithLifecycle(initialValue=emptyList())
    var selectedBook by rememberSaveable(bookId) { mutableStateOf(bookId) }
    var library by rememberSaveable { mutableStateOf(startInLibrary) }
    var query by rememberSaveable { mutableStateOf("") }
    var create by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val pages = rememberSaveableStateHolder()
    val motion = LocalMotionEnabled.current
    val distance = with(LocalDensity.current) { 12.dp.roundToPx() }
    val volumes by remember(selectedBook) { container.outlineRepository.observeOutline(selectedBook) }.collectAsStateWithLifecycle(initialValue=emptyList())
    val selected = books.firstOrNull { it.id == selectedBook }
    LaunchedEffect(requestKey) { library = startInLibrary }
    LaunchedEffect(books) { if(books.isNotEmpty() && books.none { it.id == selectedBook }) selectedBook = books.firstOrNull { it.id == bookId }?.id ?: books.first().id }
    Column(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal=18.dp, vertical=16.dp), verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                SeedBookCover(selected?.title.orEmpty(), selectedBook, Modifier.size(42.dp,60.dp))
                Column(Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(selected?.title ?: "我的书库", fontFamily=FontFamily.Serif, fontSize=19.sp, fontWeight=FontWeight.Bold, maxLines=2, overflow=TextOverflow.Ellipsis)
                    Text(volumes.size.toString()+" 卷 · "+volumes.sumOf { it.chapters.size }+" 章", fontSize=12.sp, color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("全书 "+volumes.sumOf { v -> v.chapters.sumOf { it.wordCount } }+" 字 · 已完成 "+volumes.sumOf { v -> v.chapters.count { it.completed } }+" 章", fontSize=11.sp, color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick=onClose, modifier=Modifier.size(44.dp)) { Icon(Icons.Filled.Close,"关闭目录与书库") }
            }
            AppSegments(listOf("目录","书库"), if(library) 1 else 0, { library=it==1 })
            error?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) }
        }
        AnimatedContent(targetState=library, modifier=Modifier.weight(1f).fillMaxWidth(),
            transitionSpec={
                val direction=if(targetState)1 else -1
                if(motion) (fadeIn(tween(260))+slideInHorizontally(AppMotion.panelSpring()){direction*distance}) togetherWith
                    (fadeOut(tween(220))+slideOutHorizontally(AppMotion.panelSpring()){-direction*distance})
                else fadeIn(snap()) togetherWith fadeOut(snap())
            }, label="outlineLibraryPage") { showLibrary ->
            if(!showLibrary) pages.SaveableStateProvider("outline-"+selectedBook) {
                OutlineContent(selectedBook,currentChapterId,onOpenChapter,Modifier.fillMaxSize(),onClose=null,
                    beforeCopy=beforeCopy,onCompleted=onCompleted,showHeader=false,onOpenSource=onOpenSource,onExport=onExport?.let{export->{scopeName,id->export(selectedBook,scopeName,id)}})
            } else pages.SaveableStateProvider("library") {
                val listState=rememberLazyListState()
                Column(Modifier.fillMaxSize().padding(horizontal=18.dp)) {
                    AppTextField(query,{query=it.take(200)},singleLine=true,placeholder={Text("搜索书名")},modifier=Modifier.fillMaxWidth())
                    LazyColumn(Modifier.weight(1f),state=listState,contentPadding=PaddingValues(vertical=12.dp)) {
                        val visible=books.filter { it.title.contains(query,true) }.sortedBy { if(it.id==bookId)0 else 1 }
                        if(visible.isEmpty()) item { CenterMessage("没有匹配的作品") }
                        items(visible,key={it.id}) { book ->
                            Row(Modifier.fillMaxWidth().clickable { selectedBook=book.id;library=false }.padding(vertical=14.dp),
                                horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                                SeedBookCover(book.title,book.id,Modifier.size(36.dp,52.dp))
                                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                    Text(book.title,fontFamily=FontFamily.Serif,style=MaterialTheme.typography.titleMedium,maxLines=2)
                                    Text(book.chapterCount.toString()+" 章 · "+book.wordCount+" 字 · "+relativeEditTime(book.updatedAt),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if(book.id==bookId) Text("当前",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                            }
                            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.35f))
                        }
                    }
                    FilledTonalButton(onClick={ scope.launch { if(beforeCopy()) create=true else error="当前章未保存成功，请先返回检查。" } },
                        modifier=Modifier.fillMaxWidth().padding(bottom=16.dp).height(42.dp),
                        colors=ButtonDefaults.filledTonalButtonColors(containerColor=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurfaceVariant)) { Text("＋ 新建作品") }
                }
            }
        }
    }
    if(create) com.qingling.writing.feature.bookshelf.AddBookFlow(onDismiss={create=false},onCreated={ id ->
        create=false;selectedBook=id;library=false
        scope.launch { container.writingRepository.resolveBookActiveChapter(id)?.let(onOpenChapter) }
    })
}
