package com.qingling.writing.feature.outline
import com.qingling.writing.core.design.ConfirmAction
import com.qingling.writing.core.design.*
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowUp
import com.qingling.writing.core.design.AppTextField

import androidx.compose.foundation.background
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import com.qingling.writing.core.design.QuietTextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qingling.writing.R
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.design.CenterMessage
import com.qingling.writing.core.design.ConfirmDialog
import com.qingling.writing.core.design.Dimens
import com.qingling.writing.core.design.Spacing
import com.qingling.writing.core.design.TitleInputDialog
import com.qingling.writing.core.design.TouchTarget
import com.qingling.writing.core.model.OutlineChapter
import com.qingling.writing.core.model.OutlineVolume
import com.qingling.writing.domain.validation.TitleError
import com.qingling.writing.domain.validation.TitleValidator
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.qingling.writing.core.design.SeedBookCover
import com.qingling.writing.core.design.relativeEditTime
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import kotlin.math.roundToInt
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.gestures.scrollBy
import kotlinx.coroutines.delay

/** 目录面板内的对话框类型(新建/重命名/跨卷移动/删除确认)。 */
private sealed interface OutlineDialog {
    data object NewVolume : OutlineDialog
    data class RenameVolume(val volume: OutlineVolume) : OutlineDialog
    data class RenameChapter(val chapter: OutlineChapter) : OutlineDialog
    data class DuplicateChapter(val chapter: OutlineChapter) : OutlineDialog
    data class MoveChapter(val chapter: OutlineChapter) : OutlineDialog
    data class Position(val volume: OutlineVolume,val chapter: OutlineChapter? = null) : OutlineDialog
    data class DeleteVolume(val volume: OutlineVolume) : OutlineDialog
    data class DeleteChapter(val chapter: OutlineChapter) : OutlineDialog
}

/**
 * 目录内容(P02,数据驱动):卷章树 + 新建卷/章、重命名、上移/下移、跨卷移动、软删除。
 * 同时用于 目录页(outline 目的地)与编辑器左滑面板(P04);[onOpenChapter] 由调用方决定跳转方式。
 * 所有行高满足 56dp 最小值,菜单按钮显式 48dp 触控目标(M1 复核 F8 的统一写法)。
 */
@Composable
fun OutlineContent(
    bookId: String,
    currentChapterId: String?,
    onOpenChapter: (String) -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    beforeCopy: suspend () -> Boolean = { true },
    onLibrary: (() -> Unit)? = null,
    onCompleted: (OutlineChapter) -> Unit = {},
    showHeader: Boolean = true,
    onOpenSource:(String,Int,Int)->Unit={id,_,_->onOpenChapter(id)},
    onExport:((String,String)->Unit)?=null,
) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val container = LocalAppContainer.current
    val viewModel: OutlineViewModel = viewModel(
        key = bookId,
        factory = viewModelFactory {
            initializer { OutlineViewModel(container.outlineRepository, bookId) }
        },
    )
    val volumes by viewModel.volumes.collectAsStateWithLifecycle()
    val bookTitle by viewModel.bookTitle.collectAsStateWithLifecycle()
    val notes by remember {container.planning.inbox()}.collectAsStateWithLifecycle(initialValue=emptyList())
    val pending=notes.filter { val note=com.qingling.writing.core.model.QuickNote.decode(it.body);it.bookId==bookId && note.foreshadow && !note.resolved }
    var inboxOpen by remember {mutableStateOf(false)}
    var inboxChapter by remember {mutableStateOf<String?>(null)}
    val scope = rememberCoroutineScope()

    var collapsed by rememberSaveable { mutableStateOf(listOf<String>()) }
    var filter by rememberSaveable(bookId) { mutableStateOf("") }
    val listState = rememberLazyListState()
    var positioned by rememberSaveable(bookId) { mutableStateOf(false) }
    var positionedChapter by rememberSaveable(bookId) { mutableStateOf<String?>(null) }
    var positionedFilter by rememberSaveable(bookId) { mutableStateOf("") }
    var creatingChapter by remember { mutableStateOf(false) }
    var createdChapterId by rememberSaveable {mutableStateOf<String?>(null)}
    var createdChapterTitle by rememberSaveable {mutableStateOf("")}
    var revealCreated by rememberSaveable {mutableStateOf(false)}
    val motion = LocalMotionEnabled.current
    var completionError by remember { mutableStateOf<String?>(null) }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var dropKey by remember { mutableStateOf<String?>(null) }
    val latestVolumes by rememberUpdatedState(volumes)
    fun updateDrop() {
        val key=dragKey ?: return
        val visible=listState.layoutInfo.visibleItemsInfo
        val eligible=visible.filter { item ->
            val id=item.key.toString()
            if(key.startsWith("v-")) id.startsWith("v-") else id.startsWith("c-") || id.startsWith("v-")
        }
        dropKey=eligible.minByOrNull { kotlin.math.abs(it.offset+it.size/2f-dragY) }?.key?.toString() ?: dropKey
    }
    LaunchedEffect(dragKey) {
        while(dragKey!=null) {
            val info=listState.layoutInfo;val edge=100f
            val speed=when {dragY < info.viewportStartOffset+edge -> -24f;dragY > info.viewportEndOffset-edge -> 24f;else -> 0f}
            if(speed!=0f) {listState.scrollBy(speed);updateDrop()}
            delay(16)
        }
    }
    val dragEnabled=filter.isEmpty()
    fun finishDrag() {
            val source=dragKey;val target=dropKey;dragKey=null;dropKey=null
            if(source!=null && target!=null && source!=target) scope.launch {
                try {
                    if(source.startsWith("v-")) {
                        val index=latestVolumes.indexOfFirst { "v-${it.id}"==target }
                        if(index>=0) viewModel.placeVolume(bookId,source.removePrefix("v-"),index)
                    } else {
                        val v=latestVolumes.firstOrNull { "v-${it.id}"==target || it.chapters.any { c->"c-${c.id}"==target } }
                        val index=if(target.startsWith("v-")) 0 else v?.chapters?.indexOfFirst { "c-${it.id}"==target } ?: -1
                        if(v!=null && index>=0) viewModel.placeChapter(source.removePrefix("c-"),v.id,index)
                    }
                } catch(e: kotlinx.coroutines.CancellationException) {throw e} catch(_: Exception) { completionError="排序未保存，请重试；原目录保留" }
            }
    }
    fun startDrag(key: String) {
        val item=listState.layoutInfo.visibleItemsInfo.firstOrNull {it.key.toString()==key} ?: return
        dragKey=key;dropKey=key;dragY=item.offset+item.size/2f
    }
    val visibleVolumes = remember(volumes, filter) {
        if (filter.isEmpty()) volumes else volumes.mapNotNull { v ->
            v.copy(chapters = v.chapters.filter { it.title.contains(filter) }).takeIf { it.chapters.isNotEmpty() }
        }
    }
    LaunchedEffect(currentChapterId, filter, volumes.flatMap { it.chapters }.map { it.id }.sorted()) {
        if(revealCreated) return@LaunchedEffect
        if (positioned && positionedChapter == currentChapterId && positionedFilter == filter) return@LaunchedEffect
        if (volumes.isEmpty()) return@LaunchedEffect
        val volume = visibleVolumes.firstOrNull { v -> v.chapters.any { it.id == currentChapterId } }
        if (volume != null) {
            collapsed = collapsed - volume.id
            var index = 0
            for (v in visibleVolumes) {
                if (v.id == volume.id) {
                    index += 1 + v.chapters.indexOfFirst { it.id == currentChapterId }
                    break
                }
                index += 1 + if (filter.isNotEmpty() || v.id !in collapsed) v.chapters.size else 0
            }
            androidx.compose.runtime.withFrameNanos { }
            listState.scrollToItem(index)
        } else listState.scrollToItem(0)
        positioned = true; positionedChapter = currentChapterId; positionedFilter = filter
    }
    var dialog by remember { mutableStateOf<OutlineDialog?>(null) }
    var dialogError by remember { mutableStateOf<TitleError?>(null) }
    var planningTarget by remember { mutableStateOf<Pair<String?,String?>?>(null) }
    fun open(target: OutlineDialog) {
        dialogError = null
        dialog = target
    }

    fun createChapter(volume: OutlineVolume) {
        if(creatingChapter) return
        creatingChapter=true
        scope.launch {
            try {
                if(!beforeCopy()) completionError="当前章未保存成功，未创建新章。"
                else when(val result=viewModel.createChapter(bookId,volume.id,"")) {
                    is OutlineViewModel.OperationResult.Created -> {
                        collapsed=collapsed-volume.id
                        createdChapterId=result.chapter.id;createdChapterTitle=result.chapter.title
                        android.widget.Toast.makeText(context,"已新建章节",android.widget.Toast.LENGTH_SHORT).show()
                    }
                    else -> completionError="未能创建章节，请重试。"
                }
            } catch(e:kotlinx.coroutines.CancellationException) { throw e }
            catch(_:Exception) { completionError="新章未创建，请检查存储空间后重试。" }
            finally { creatingChapter=false }
        }
    }
    LaunchedEffect(revealCreated,createdChapterId,volumes) {
        if(!revealCreated || filter.isNotEmpty()) return@LaunchedEffect
        val target=volumes.firstOrNull {v->v.chapters.any {it.id==createdChapterId}} ?: return@LaunchedEffect
        collapsed=collapsed-target.id
        var index=0
        for(v in volumes) {
            if(v.id==target.id){index+=1+v.chapters.indexOfFirst {it.id==createdChapterId};break}
            index+=1+if(v.id !in collapsed)v.chapters.size else 0
        }
        androidx.compose.runtime.withFrameNanos { }
        listState.scrollToItem(index)
        positioned=true;positionedChapter=currentChapterId;positionedFilter=filter;revealCreated=false
    }
    val expansion = mutableMapOf<String,Float>()
    visibleVolumes.forEach { volume ->
        val fraction = androidx.compose.runtime.key(volume.id) {
            val value by androidx.compose.animation.core.animateFloatAsState(if(filter.isNotEmpty() || volume.id !in collapsed)1f else 0f,
                if(motion) AppMotion.panelSpring() else androidx.compose.animation.core.snap(), label="volume-"+volume.id)
            value
        }
        expansion[volume.id]=fraction
    }
    MenuBoundary(modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        if(showHeader) Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = Spacing.s24, vertical = Spacing.s16)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp,61.dp).then(if(onLibrary!=null) Modifier.clickable(onClick=onLibrary) else Modifier)) {
                    SeedBookCover(bookTitle.orEmpty(), bookId, Modifier.size(40.dp,57.dp))
                    if(onLibrary!=null) Text("⇄",Modifier.align(Alignment.BottomEnd).background(MaterialTheme.colorScheme.surfaceContainerHighest,RoundedCornerShape(8.dp)).padding(horizontal=3.dp),color=MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(bookTitle.orEmpty(), style = MaterialTheme.typography.titleLarge.copy(fontFamily=androidx.compose.ui.text.font.FontFamily.Serif), maxLines = 2)
                    Text("${volumes.size} 卷 · ${volumes.sumOf { it.chapters.size }} 章", fontSize = 13.sp, lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                onClose?.let { FilledTonalIconButton(onClick = it) { Icon(Icons.Filled.Close, "关闭目录", tint = MaterialTheme.colorScheme.onSurfaceVariant) } }
            }
            onLibrary?.let { toggle -> AppSegments(listOf("目录","书库"),0,{if(it==1)toggle()},Modifier.padding(top=12.dp)) }
            val done = volumes.sumOf { v -> v.chapters.count { it.completed } }
            Text("全书 "+volumes.sumOf { v -> v.chapters.sumOf { it.wordCount } }+" 字 · 已完成 "+done+" 章",
                fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=10.dp))
            if(pending.isNotEmpty()) QuietTextButton(onClick={inboxChapter=null;inboxOpen=true}){Text("未回收伏笔 "+pending.size+" 条",color=MaterialTheme.colorScheme.onSurfaceVariant)}
            completionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextField(filter, { filter = it.take(120) }, singleLine = true,
                placeholder = { Text(stringResource(R.string.outline_filter), fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, null) }, shape = RoundedCornerShape(13.dp),
                colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent),
                modifier = Modifier.fillMaxWidth())
        }
        else Column(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=4.dp)) {
            AppTextField(filter,{filter=it.take(120)},singleLine=true,placeholder={Text("按章名筛选")},modifier=Modifier.fillMaxWidth())
            completionError?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) }
        }
        if(createdChapterId!=null) Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("已新建："+createdChapterTitle+if(filter.isNotEmpty() && !createdChapterTitle.contains(filter)) "（被当前筛选隐藏）" else "",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={filter="";revealCreated=true}){Text(if(filter.isNotEmpty()) "清除筛选查看" else "在目录查看")}
            TextButton(onClick={createdChapterId=null}){Text("知道了")}
        }
        if (volumes.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                Text(stringResource(R.string.outline_empty),color=MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick={open(OutlineDialog.NewVolume)}) { Text("新建第一卷") }
            }
        } else {
            Text(if(dragKey!=null) "拖动中 · 靠近边缘滚动 · 松手确认" else "点状态点切换完成 · 长按拖动柄排序",Modifier.padding(8.dp),style=MaterialTheme.typography.bodySmall,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                if (visibleVolumes.isEmpty()) item { Text(stringResource(R.string.search_no_results), Modifier.padding(24.dp)) }
                visibleVolumes.forEach { volume ->
                    item(key = "v-${volume.id}") {
                        if(dropKey=="v-${volume.id}") HorizontalDivider(thickness=3.dp,color=MaterialTheme.colorScheme.primary)
                        VolumeHeaderRow(
                            volume = volume,
                            chapterCount = volume.chapters.size,
                            expanded = filter.isNotEmpty() || volume.id !in collapsed,
                            onToggle = { collapsed = if (volume.id in collapsed) collapsed - volume.id else collapsed + volume.id },
                            onNewChapter = { createChapter(volume) },
                            onExport=if(onExport==null)null else { {scope.launch{if(beforeCopy())onExport("VOLUME",volume.id)}} },
                            onSummary = { scope.launch { if (beforeCopy()) planningTarget = null to volume.id } },
                            onRename = { open(OutlineDialog.RenameVolume(volume)) },
                            onMoveUp = { scope.launch { viewModel.moveVolume(bookId, volume.id, -1) } },
                            onMoveDown = { scope.launch { viewModel.moveVolume(bookId, volume.id, +1) } },
                            onDelete = { open(OutlineDialog.DeleteVolume(volumes.first { it.id == volume.id })) },
                            onPosition = { open(OutlineDialog.Position(volume)) },
                            dragGrip = {if(dragEnabled) ReorderGrip("拖动卷排序",onStart={startDrag("v-${volume.id}")},onDrag={dragY+=it;updateDrop()},onEnd={finishDrag()},onCancel={dragKey=null;dropKey=null})},
                        )
                    }
                    if ((expansion[volume.id] ?: 0f) > 0f) items(volume.chapters.size, key = { "c-${volume.chapters[it].id}" }) { index ->
                        val chapter = volume.chapters[index]
                        Column(Modifier.revealHeight(expansion[volume.id] ?: 0f)) {
                        if(dropKey=="c-${chapter.id}") HorizontalDivider(thickness=3.dp,color=MaterialTheme.colorScheme.primary)
                        ChapterRow(
                            chapter = chapter,
                            pendingCount=pending.count{com.qingling.writing.core.model.QuickNote.decode(it.body).chapterId==chapter.id},
                            onNotes={scope.launch{if(beforeCopy()){inboxChapter=chapter.id;inboxOpen=true}else completionError="请先处理正文保存失败"}},
                            onComplete = { value -> scope.launch {
                                try { container.writingProgress.complete(chapter.id,value); completionError = null; if(value) onCompleted(chapter) }
                                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                catch (_: Exception) { completionError = "完成状态未保存，请稍后重试。" }
                            }; Unit },
                            reorderEnabled = filter.isEmpty(),
                            dragGrip = {if(dragEnabled) ReorderGrip("拖动章节排序",onStart={startDrag("c-${chapter.id}")},onDrag={dragY+=it;updateDrop()},onEnd={finishDrag()},onCancel={dragKey=null;dropKey=null})},
                            isCurrent = chapter.id == currentChapterId,
                            onOpen = { onOpenChapter(chapter.id) },
                            onRename = { open(OutlineDialog.RenameChapter(chapter)) },
                            onExport=if(onExport==null)null else { {scope.launch{if(beforeCopy())onExport("CHAPTER",chapter.id)}} },
                            onOutline = { scope.launch { if (beforeCopy()) planningTarget = chapter.id to null } },
                            onDuplicate = { open(OutlineDialog.DuplicateChapter(chapter)) },
                            onFirst = { scope.launch { viewModel.placeChapter(chapter.id, chapter.volumeId, 0) } },
                            onLast = { scope.launch { viewModel.placeChapter(chapter.id, chapter.volumeId, Int.MAX_VALUE) } },
                            onMoveUp = { scope.launch { viewModel.moveChapterWithinVolume(chapter.id, -1) } },
                            onMoveDown = { scope.launch { viewModel.moveChapterWithinVolume(chapter.id, +1) } },
                            onMoveToVolume = { open(OutlineDialog.MoveChapter(chapter)) },
                            onPosition = { open(OutlineDialog.Position(volume,chapter)) },
                            onDelete = { open(OutlineDialog.DeleteChapter(chapter)) },
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(horizontal = Spacing.s24),
                        )
                        }
                    }
                }
            }
        }
        FilledTonalButton(onClick={open(OutlineDialog.NewVolume)}, shape=RoundedCornerShape(12.dp),
            colors=ButtonDefaults.filledTonalButtonColors(containerColor=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurfaceVariant),
            modifier=Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=16.dp).height(42.dp)) { Text("＋ 新建卷") }
    }

    }
    if(inboxOpen) com.qingling.writing.feature.planning.NoteInboxDialog(onDismiss={inboxOpen=false},bookId=bookId,initialTab=1,initialChapterId=inboxChapter,onOpenSource=onOpenSource)
    planningTarget?.let { target ->
        com.qingling.writing.feature.planning.PlanningDialog(bookId,currentChapterId,
            initialChapterId = target.first,initialVolumeId = target.second,
            onDismiss = { planningTarget = null },onOpenChapter = onOpenChapter)
    }
    when (val current = dialog) {
        is OutlineDialog.Position -> PositionDialog(current.volume,current.chapter,volumes,onDismiss={dialog=null}) { volume,index ->
            scope.launch {
                try {if(current.chapter==null) viewModel.placeVolume(bookId,current.volume.id,index) else viewModel.placeChapter(current.chapter.id,volume,index)
                    dialog=null
                } catch(e: kotlinx.coroutines.CancellationException) {throw e} catch(_: Exception) { completionError="排序未保存，请重试";dialog=null }
            }
        }
        OutlineDialog.NewVolume -> TitleInputDialog(
            title = stringResource(R.string.action_new_volume),
            label = stringResource(R.string.field_volume_title),
            initialValue = stringResource(R.string.default_new_volume_title),
            error = dialogError,
            maxCodePoints = TitleValidator.SECTION_MAX_CODE_POINTS,
            onConfirm = { raw ->
                scope.launch {
                    when (val result = viewModel.createVolume(bookId, raw)) {
                        is OutlineViewModel.OperationResult.Ok -> dialog = null
                        is OutlineViewModel.OperationResult.Invalid ->
                            dialogError = result.error
                        else -> dialog = null
                    }
                }
            },
            onDismiss = { dialog = null },
        )
        is OutlineDialog.RenameVolume -> TitleInputDialog(
            title = stringResource(R.string.action_rename),
            label = stringResource(R.string.field_volume_title),
            initialValue = current.volume.title,
            error = dialogError,
            maxCodePoints = TitleValidator.SECTION_MAX_CODE_POINTS,
            onConfirm = { raw ->
                scope.launch {
                    when (val result = viewModel.renameVolume(current.volume.id, raw)) {
                        is OutlineViewModel.OperationResult.Ok -> dialog = null
                        is OutlineViewModel.OperationResult.Invalid ->
                            dialogError = result.error
                        else -> dialog = null
                    }
                }
            },
            onDismiss = { dialog = null },
        )
        is OutlineDialog.RenameChapter -> TitleInputDialog(
            title = stringResource(R.string.action_rename),
            label = stringResource(R.string.field_chapter_title),
            initialValue = current.chapter.title,
            error = dialogError,
            maxCodePoints = TitleValidator.SECTION_MAX_CODE_POINTS,
            onConfirm = { raw ->
                scope.launch {
                    if(!beforeCopy()) { completionError="当前章未保存成功，请先处理保存失败。";return@launch }
                    when (val result = viewModel.renameChapter(current.chapter.id, raw)) {
                        is OutlineViewModel.OperationResult.Ok -> dialog = null
                        is OutlineViewModel.OperationResult.Invalid ->
                            dialogError = result.error
                        else -> dialog = null
                    }
                }
            },
            onDismiss = { dialog = null },
        )
        is OutlineDialog.DuplicateChapter -> TitleInputDialog(
            title = "复制章节为新草稿", label = "新章节标题",
            initialValue = "章节副本", error = dialogError,
            maxCodePoints = TitleValidator.SECTION_MAX_CODE_POINTS,
            onConfirm = { title -> scope.launch {
                try {
                    if (!beforeCopy()) {
                        completionError = "原章尚未保存成功，未创建副本。请先重试保存。"
                    } else when (val result = viewModel.duplicateChapter(current.chapter.id, title)) {
                        is OutlineViewModel.OperationResult.Created -> { dialog = null;android.widget.Toast.makeText(context,"已创建章节副本",android.widget.Toast.LENGTH_SHORT).show() }
                        is OutlineViewModel.OperationResult.Invalid -> dialogError = result.error
                        else -> Unit
                    }
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { completionError = "复制失败，原章保留不变，请稍后重试。"; dialog = null }
            } }, onDismiss = { dialog = null },
        )
        is OutlineDialog.MoveChapter -> MoveChapterDialog(
            chapter = current.chapter,
            volumes = volumes,
            onConfirm = { targetVolumeId ->
                dialog = null
                scope.launch { viewModel.moveChapterToVolume(current.chapter.id, targetVolumeId) }
            },
            onDismiss = { dialog = null },
        )
        is OutlineDialog.DeleteVolume -> ConfirmDialog(
            title = stringResource(R.string.action_move_to_recycle_bin),
            body = stringResource(
                R.string.delete_volume_body_format,
                current.volume.title,
                current.volume.chapters.size,
            ),
            confirmText = stringResource(R.string.action_move_to_recycle_bin),
            onConfirm = {
                dialog = null
                scope.launch { viewModel.deleteVolume(current.volume.id) }
            },
            onDismiss = { dialog = null },
        )
        is OutlineDialog.DeleteChapter -> ConfirmDialog(
            title = stringResource(R.string.action_move_to_recycle_bin),
            body = stringResource(R.string.delete_chapter_body_format, current.chapter.title),
            confirmText = stringResource(R.string.action_move_to_recycle_bin),
            onConfirm = {
                dialog = null
                scope.launch { viewModel.deleteChapter(current.chapter.id) }
            },
            onDismiss = { dialog = null },
        )
        null -> {}
    }
}

@Composable
private fun VolumeHeaderRow(
    volume: OutlineVolume,
    chapterCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onNewChapter: () -> Unit,
    onSummary: () -> Unit,
    onExport:(()->Unit)?=null,
    onRename: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onPosition: () -> Unit,
    dragGrip: @Composable ()->Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val rotation by androidx.compose.animation.core.animateFloatAsState(if(expanded)180f else 0f,
        if(LocalMotionEnabled.current) AppMotion.panelSpring() else androidx.compose.animation.core.snap(),label="volumeChevron")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.listRowMinHeight)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = Spacing.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggle) {
            Icon(Icons.Filled.KeyboardArrowUp, modifier=Modifier.graphicsLayer { rotationZ=rotation },
                contentDescription = stringResource(if (expanded) R.string.outline_collapse else R.string.outline_expand))
        }
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(if(volume.title.isBlank() || volume.title.all {it.isDigit()}) "未命名卷" else volume.title,style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle))
            if(volume.title.isBlank() || volume.title.all {it.isDigit()}) Text("原名「${volume.title}」",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.outline_chapter_count_format, chapterCount) + " · 已完成 " + volume.chapters.count { it.completed } + "/" + chapterCount,fontSize=11.sp,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if (volume.summary.isNotBlank()) Text(volume.summary,maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onSummary))
        }
        IconButton(onClick = onNewChapter) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_new_chapter))
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.action_more),
                )
            }
            AppMenu(menuOpen, {menuOpen=false}, context="${volume.title} · $chapterCount 章") {
                onExport?.let{export->AppMenuItem({Text("导出本卷")},{menuOpen=false;export()},WritingIcons.Export)}
                AppMenuItem({Text("分卷概要")},{menuOpen=false;onSummary()},Icons.Filled.List)
                AppMenuItem({Text("新建章节")},{menuOpen=false;onNewChapter()},Icons.Filled.Add)
                AppMenuItem({Text("重命名")},{menuOpen=false;onRename()},Icons.Filled.Edit)
                MenuRule()
                AppMenuItem({Text("移到指定位置")},{menuOpen=false;onPosition()},Icons.Filled.ArrowForward)
                MenuRule()
                AppMenuItem({Text("移入回收站")},{menuOpen=false;onDelete()},Icons.Filled.Delete,danger=true)
            }
        }
        dragGrip()
    }
}

@Composable
private fun ChapterRow(
    chapter: OutlineChapter,
    pendingCount:Int=0,
    onNotes:()->Unit={},
    isCurrent: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onOutline: () -> Unit,
    onExport:(()->Unit)?=null,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveToVolume: () -> Unit,
    onDelete: () -> Unit,
    reorderEnabled: Boolean = false,
    onComplete: (Boolean) -> Unit = {},
    onDuplicate: () -> Unit,
    onFirst: () -> Unit,
    onLast: () -> Unit,
    onPosition: () -> Unit,
    dragGrip: @Composable ()->Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    val base = MaterialTheme.colorScheme.surfaceContainer
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .background(Brush.horizontalGradient(if (isCurrent) listOf(accent.copy(alpha = .12f), base) else listOf(base, base)))
            .drawBehind { if (isCurrent) drawRect(accent, size = Size(3.5.dp.toPx(), size.height)) }
            .clickable(onClick = onOpen)
            .padding(horizontal = Spacing.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).toggleable(chapter.completed, role = Role.Checkbox, onValueChange = onComplete)
            .semantics { contentDescription = if (chapter.completed) "已完成，点按改为草稿" else "草稿，点按标记完成" }, contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.size(8.dp)) {
                drawCircle(if(chapter.completed || isCurrent) accent else androidx.compose.ui.graphics.Color(0xFF8B9096), style = if (chapter.completed) androidx.compose.ui.graphics.drawscope.Fill else androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            }
        }
        Column(Modifier.weight(1f).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = chapter.title,
            style = MaterialTheme.typography.titleMedium,
            color = if(chapter.unnamed) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.65f) else if(isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Text("${chapter.wordCount} 字 · ${relativeEditTime(chapter.updatedAt)}", fontSize = 12.sp, lineHeight = 17.sp,
            color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurfaceVariant)
        if(pendingCount>0) Text("未回收伏笔 "+pendingCount,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.clickable(onClick=onNotes))
        if (chapter.outline.isNotBlank()) Text(chapter.outline,maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOutline))
        }
        if (isCurrent) {
            Text(
                text = stringResource(R.string.outline_current_chapter),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.action_more),
                )
            }
            AppMenu(menuOpen, {menuOpen=false}, context="${chapter.title} · ${chapter.wordCount} 字") {
                onExport?.let{export->AppMenuItem({Text("导出本章")},{menuOpen=false;export()},WritingIcons.Export)}
                AppMenuItem({Text("章节细纲")},{menuOpen=false;onOutline()},Icons.Filled.List)
                AppMenuItem({Text("复制为新章节")},{menuOpen=false;onDuplicate()},MenuCopyIcon)
                AppMenuItem({Text("重命名")},{menuOpen=false;onRename()},Icons.Filled.Edit)
                MenuRule()
                AppMenuItem({Text("移到指定位置")},{menuOpen=false;onPosition()},Icons.Filled.ArrowForward)
                AppMenuItem({Text("移到本卷开头")},{menuOpen=false;onFirst()},Icons.Filled.KeyboardArrowUp)
                AppMenuItem({Text("移到本卷末尾")},{menuOpen=false;onLast()},Icons.Filled.KeyboardArrowDown)
                AppMenuItem({Text("移至其他卷")},{menuOpen=false;onMoveToVolume()},Icons.Filled.ArrowForward)
                MenuRule()
                AppMenuItem({Text("移入回收站")},{menuOpen=false;onDelete()},Icons.Filled.Delete,danger=true)
            }
        }
        if(reorderEnabled) dragGrip() else Spacer(Modifier.width(44.dp))
    }
}

@Composable
private fun MoveChapterDialog(
    chapter: OutlineChapter,
    volumes: List<OutlineVolume>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val candidates = volumes.filter { it.id != chapter.volumeId }
    var selected by remember { mutableStateOf(candidates.firstOrNull()?.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.move_to_volume_title)) },
        text = {
            Column {
                if (candidates.isEmpty()) {
                    Text(text = stringResource(R.string.move_to_volume_no_target))
                }
                candidates.forEach { volume ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RadioButton(
                            selected = selected == volume.id,
                            onClick = { selected = volume.id },
                        )
                        Text(
                            text = volume.title,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            ConfirmAction(
                onClick = {
                    selected?.let(onConfirm)
                },
                enabled = selected != null,
            ) {
                Text(text = stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            QuietTextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

/** All rows of a volume animate together; no per-item stagger or fade-in cascade. */
private fun Modifier.revealHeight(fraction: Float): Modifier = clipToBounds().layout { measurable, constraints ->
    val placeable=measurable.measure(constraints)
    layout(placeable.width,(placeable.height*fraction.coerceIn(0f,1f)).roundToInt()) { placeable.place(0,0) }
}
