package com.qingling.writing.feature.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingling.writing.core.design.LocalMotionEnabled
import com.qingling.writing.core.model.*
import com.qingling.writing.core.datastore.SettingsStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

internal data class EditorReferenceData(
    val chapterId: String = "",
    val outline: PlanningEntry? = null,
    val people: List<PlanningEntry> = emptyList(),
    val notes: List<PlanningEntry> = emptyList(),
    val previousEnding: String = "",
    val previousLoading: Boolean = false,
    val error: String? = null,
) {
    val parsed: ChapterReference get() = ChapterReference.parse(outline?.body.orEmpty())
    val hasOutline: Boolean get() = !outline?.body.isNullOrBlank()
}

/** Gesture is attached only to chrome/handles, never to manuscript scrolling. */
internal fun Modifier.drawerDrag(onRelease: (Float) -> Unit): Modifier = composed {
    val current by rememberUpdatedState(onRelease)
    val density = LocalDensity.current
    pointerInput(density) {
        var distance = 0f
        detectVerticalDragGestures(onDragStart = { distance = 0f },
            onVerticalDrag = { change, amount -> change.consume(); distance += amount },
            onDragCancel = { distance = 0f }, onDragEnd = { current(distance / density.density); distance = 0f })
    }
}

@Composable
internal fun ReferenceHandle(bookId: String?, store: SettingsStore, hasOutline: Boolean, collapsed: Boolean,
    onClick: () -> Unit, modifier: Modifier = Modifier) {
    val motion = LocalMotionEnabled.current
    val offset = remember { Animatable(0f) }
    LaunchedEffect(bookId, hasOutline) {
        if (bookId != null && hasOutline && !store.referenceHintSeen(bookId).first()) {
            if (motion && collapsed) {
                offset.animateTo(4f, tween(180)); offset.animateTo(0f, tween(260))
            }
            try { store.markReferenceHintSeen(bookId) } catch (_: java.io.IOException) { }
        }
    }
    Surface(modifier.graphicsLayer { translationY = offset.value * density }.width(78.dp).height(18.dp)
        .clickable(onClickLabel = "下拉显示细纲", onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("细纲 ⌄", fontSize = 11.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun ReferencePeek(data: EditorReferenceData, onEdit: () -> Unit, onExpand: () -> Unit, onClose: () -> Unit,
    modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth().height(110.dp).drawerDrag { if (it < -24) onClose() else if (it > 42) onExpand() },
        color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!data.hasOutline) {
                TextButton(onClick = onEdit, contentPadding = PaddingValues(0.dp)) { Text("本章暂无细纲 · 写一笔") }
            } else {
                Text("本章细纲", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text("目标：" + data.parsed.goal.ifBlank { "尚未填写本章目标" }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                Text(if(data.parsed.scenes.isNotEmpty()) "下一场："+data.parsed.nextScene else "正文 "+data.outline?.body.orEmpty().lineSequence().count()+" 行 · 展开查看全文", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClose, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), modifier = Modifier.height(26.dp)) { Text("收起", fontSize = 11.sp) }
                TextButton(onClick = onExpand, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), modifier = Modifier.height(26.dp)) { Text("展开 ›", fontSize = 11.sp) }
            }
        }
    }
}

@Composable
internal fun ReferencePanel(data: EditorReferenceData, tab: Int, onTab: (Int) -> Unit, onEdit: () -> Unit,
    onScene: (ReferenceScene) -> Unit, onLocate: (QuickNote) -> Unit, onInsertName: (String) -> Unit,
    busy: Boolean, error: String?, modifier: Modifier = Modifier, onEndUp: (() -> Unit)? = null) {
    val close by rememberUpdatedState(onEndUp)
    val threshold = with(LocalDensity.current) { 56.dp.toPx() }
    val boundary = remember { object : NestedScrollConnection {
        var excess = 0f
        override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: NestedScrollSource): androidx.compose.ui.geometry.Offset {
            if (source == NestedScrollSource.UserInput && available.y < 0 && consumed.y == 0f) {
                excess -= available.y
                if (excess > threshold) { excess = 0f; close?.invoke() }
            } else if (consumed.y != 0f || available.y > 0) excess = 0f
            return androidx.compose.ui.geometry.Offset.Zero
        }
    } }
    val contentScroll = remember(data.chapterId, tab) { androidx.compose.foundation.ScrollState(0) }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("细纲", "上章结尾", "伏笔 · " + data.notes.size, "人名").forEachIndexed { index, label ->
                Surface(Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(20.dp)).clickable { onTab(index) },
                    color = if (index == tab) MaterialTheme.colorScheme.primary.copy(alpha = .15f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(20.dp)) {
                    Text(label, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), fontSize = 11.sp,
                        color = if (index == tab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().nestedScroll(boundary).verticalScroll(contentScroll)
            .padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            (error ?: data.error)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            when (tab) {
                0 -> {
                    if (!data.hasOutline) TextButton(onClick = onEdit) { Text("本章暂无细纲 · 写一笔") }
                    else {
                        Text("本章目标", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(data.parsed.goal.ifBlank { "尚未填写本章目标" }, style = MaterialTheme.typography.bodyMedium)
                        if (data.parsed.scenes.isNotEmpty()) Text("分场 · 点按切换进度", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        data.parsed.scenes.forEach { scene ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(if (scene.status == SceneStatus.CURRENT) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else Color.Transparent)
                                .clickable(enabled = !busy) { onScene(scene) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(when (scene.status) { SceneStatus.DONE -> "✓"; SceneStatus.CURRENT -> "◉"; else -> "○" },
                                    color = if (scene.status == SceneStatus.TODO) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                                Text(scene.text, Modifier.weight(1f).padding(horizontal = 10.dp), style = MaterialTheme.typography.bodyMedium)
                                Text(scene.status.label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        TextButton(onClick = onEdit) { Text("编辑本章细纲") }
                    }
                    data.notes.filter { !QuickNote.decode(it.body).resolved }.forEach { entry ->
                        val note = QuickNote.decode(entry.body)
                        Row(Modifier.fillMaxWidth().clickable { onLocate(note) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("○ 本章伏笔：" + entry.title, style = MaterialTheme.typography.bodyMedium)
                                Text("未回收", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                            Text("定位 ›", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                1 -> Text(if (data.previousLoading) "正在读取上章结尾…" else data.previousEnding.ifBlank { "这是第一章，暂无上章结尾。" }, style = MaterialTheme.typography.bodyMedium)
                2 -> {
                    if (data.notes.isEmpty()) Text("本章暂无伏笔。可选中文字后设为伏笔，或从速记创建。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    data.notes.forEach { entry ->
                        val note = QuickNote.decode(entry.body)
                        Column(Modifier.fillMaxWidth().clickable { onLocate(note) }.padding(vertical = 6.dp)) {
                            Text(entry.title, style = MaterialTheme.typography.titleSmall)
                            if (note.sourceQuote.isNotBlank()) Text("原文：" + note.sourceQuote, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            else if (note.text != entry.title) Text(note.text, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            Text((if (note.resolved) "已回收" else "未回收") + (if (note.sourceQuote.isNotBlank()) " · 查看原文 ›" else if (note.sourceKind.isBlank()) " · 旧位置 ›" else " · 关联章节 ›"), fontSize = 11.sp,
                                color = if (note.resolved) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                        }
                    }
                }
                3 -> {
                    if (data.people.isEmpty()) Text("本书还没有人物，请在构思与设定中添加。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    data.people.forEach { entry ->
                        val card = CharacterCard.decode(entry.body)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.title, style = MaterialTheme.typography.titleSmall)
                                Text(listOf(card.aliases, card.identity).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(enabled = !busy, onClick = { onInsertName(entry.title) }) { Text("插入") }
                        }
                    }
                }
            }
        }
    }
}

internal enum class WritingAction(val label: String) { REFERENCE("参考"), PEOPLE("人名"), FORESHADOW("伏笔"), NOTE("速记"), FIND("查找"), FORMAT("排版"), PROOF("校对"), CHAPTER("章节信息") }
@Composable
internal fun AccessoryDrawer(page: Int, onPage: (Int)->Unit, onClose: ()->Unit,
    onAction: (WritingAction)->Unit, reference: @Composable ()->Unit, modifier: Modifier=Modifier,
    dragModifier: Modifier=Modifier, pendingCount: Int=0, fontSize: Int=17, chapterWords: Long=0,
    onGlobalFind: ()->Unit={onAction(WritingAction.FIND)},onFormatRules:()->Unit={onAction(WritingAction.FORMAT)}) {
    Surface(modifier.fillMaxWidth().height(384.dp).semantics { contentDescription="写作工具抽屉" },
        color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(topStart=20.dp,topEnd=20.dp)) {
        Column {
            Column(Modifier.fillMaxWidth().then(dragModifier).padding(top=8.dp)) {
                Box(Modifier.width(32.dp).height(4.dp).align(Alignment.CenterHorizontally)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.65f),RoundedCornerShape(2.dp)))
                Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal=18.dp),verticalAlignment=Alignment.CenterVertically) {
                    if(page!=0) TextButton(onClick={onPage(0)},contentPadding=PaddingValues(end=8.dp)){Text("‹ 工具",fontSize=12.sp)}
                    Text("写作工具",Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                    TextButton(onClick=onClose,contentPadding=PaddingValues(horizontal=4.dp)){Text("收起",fontSize=12.sp)}
                }
            }
            if(page!=0) Box(Modifier.weight(1f)) { reference() }
            else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=18.dp,vertical=6.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("参考与记录",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    WritingAction.entries.take(4).forEach { action ->
                        Surface(Modifier.weight(1f).height(70.dp).clip(RoundedCornerShape(13.dp)).clickable { onAction(action) },
                            color=MaterialTheme.colorScheme.surfaceContainer,shape=RoundedCornerShape(13.dp)) {
                            Box {
                                Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                                    Icon(when(action) { WritingAction.REFERENCE->Icons.Filled.List;WritingAction.PEOPLE->Icons.Filled.Person;WritingAction.FORESHADOW->Icons.Filled.Star;else->Icons.Filled.Edit },null,Modifier.size(23.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(action.label,Modifier.padding(top=7.dp),fontSize=12.sp)
                                }
                                if(action==WritingAction.FORESHADOW && pendingCount>0) Text(pendingCount.toString(),Modifier.align(Alignment.TopEnd).padding(5.dp),fontSize=10.sp,color=MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                Text("处理",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                WritingAction.entries.drop(4).forEach { action ->
                    Surface(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onAction(action) },
                        color=MaterialTheme.colorScheme.surfaceContainer,shape=RoundedCornerShape(12.dp)) {
                        Row(Modifier.fillMaxWidth().height(42.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            Icon(when(action){WritingAction.FIND->Icons.Filled.Search;WritingAction.FORMAT->Icons.Filled.Menu;WritingAction.PROOF->Icons.Filled.Check;else->Icons.Filled.Info},null,Modifier.size(19.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(when(action){WritingAction.FIND->"查找替换";WritingAction.PROOF->"本地校对";else->action.label},Modifier.weight(1f),fontSize=13.sp)
                            Text(when(action){WritingAction.FIND->"全书查找 ›";WritingAction.FORMAT->"规则 ›";WritingAction.PROOF->"本机检查 ›";else->"本章 "+chapterWords+" 字 ›"},
                                (when(action){WritingAction.FIND->Modifier.clickable(onClick=onGlobalFind);WritingAction.FORMAT->Modifier.clickable(onClick=onFormatRules);else->Modifier}).padding(vertical=8.dp),fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
