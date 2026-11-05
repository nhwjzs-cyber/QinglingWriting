package com.qingling.writing.feature.planning
import com.qingling.writing.core.design.*
import com.qingling.writing.core.design.QuietTextButton
import androidx.compose.material.icons.filled.List
import com.qingling.writing.core.design.ConfirmAction
import com.qingling.writing.core.design.AppSecondaryButton
import com.qingling.writing.core.design.AppMenu
import com.qingling.writing.core.design.AppMenuItem
import com.qingling.writing.core.design.AppTextField
import com.qingling.writing.core.design.AppChoice

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.design.BackButton
import com.qingling.writing.core.model.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanningDialog(bookId: String,currentChapterId: String?,onDismiss: () -> Unit,
    initialChapterId: String? = null,initialVolumeId: String? = null,
    onInsert: (suspend (String) -> Boolean)? = null,onOpenChapter: (String) -> Unit = {},onOpenSource:(String,Int,Int)->Unit={id,_,_->onOpenChapter(id)}) {
    if(initialChapterId!=null || initialVolumeId!=null){ScopedPlanningSheet(bookId,initialChapterId,initialVolumeId,onDismiss);return}
    val container = LocalAppContainer.current
    val model: PlanningViewModel = viewModel(key = "planning-$bookId",factory = viewModelFactory {
        initializer { PlanningViewModel(container.planning,container.outlineRepository,bookId,container.recoveryDrafts) }
    })
    val entries by model.entries.collectAsStateWithLifecycle()
    val volumes by model.volumes.collectAsStateWithLifecycle()
    val draft by model.draft.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val deleted by model.deleted.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("") }
    var createMenu by remember { mutableStateOf(false) }
    var deleteConfirm by remember {mutableStateOf(false)}
    var discardConfirm by remember { mutableStateOf(false) }
    var convertConfirm by remember { mutableStateOf(false) }
    var insertConfirm by remember { mutableStateOf(false) }
    var targetVolume by rememberSaveable { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var inboxOpen by remember { mutableStateOf(false) }
    LaunchedEffect(query,filter) { model.filter(query,filter) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) { model.flushOnBackground() }
    LaunchedEffect(initialChapterId,initialVolumeId) {
        if (initialChapterId != null) model.openScope(PlanningKind.CHAPTER_OUTLINE,chapterId = initialChapterId)
        else if (initialVolumeId != null) model.openScope(PlanningKind.VOLUME_SUMMARY,volumeId = initialVolumeId)
    }
    fun back() { if (!busy) scope.launch {
        if (draft == null) onDismiss()
        else if (model.finishEdit()) { if (initialChapterId != null || initialVolumeId != null) onDismiss() }
    } }
    if(inboxOpen) NoteInboxDialog(onDismiss={inboxOpen=false},onInsert=onInsert,onOpenSource=onOpenSource)
    if(!inboxOpen && draft==null) Dialog(onDismissRequest = ::back,properties = DialogProperties(usePlatformDefaultWidth = false,dismissOnClickOutside = false)) {
        BackHandler(onBack = ::back)
        Scaffold(Modifier.fillMaxSize().imePadding(),topBar = {
            TopAppBar(title = { Text(draft?.entry?.kind?.let { PlanningKind.valueOf(it).label } ?: "构思与设定") },
                navigationIcon = { BackButton(onClick = ::back) },actions = {
                    if (draft != null) Button(enabled = !busy,onClick = ::back) { Text("保存并返回") }
                    else Box {
                        IconButton(onClick = { createMenu = true }) { Icon(Icons.Filled.Add,"新建条目") }
                        AppMenu(createMenu,{ createMenu = false }) {
                            PlanningKind.entries.forEach { kind -> AppMenuItem(text = { Text(kind.label) },icon=Icons.Filled.List,onClick = {
                                createMenu = false
                                when (kind) {
                                    PlanningKind.CHAPTER_OUTLINE -> currentChapterId?.let { scope.launch { model.openScope(kind,chapterId = it) } }
                                        ?: run { notice = "请从目录中选择章节，再打开细纲" }
                                    PlanningKind.VOLUME_SUMMARY -> {
                                        val volume = volumes.firstOrNull { it.chapters.any { c -> c.id == currentChapterId } } ?: volumes.firstOrNull()
                                        if (volume == null) notice = "请先创建分卷" else scope.launch { model.openScope(kind,volumeId = volume.id) }
                                    }
                                    else -> scope.launch { model.openScope(kind) }
                                }
                            }) }
                        }
                    }
                })
        }) { padding ->
            LazyColumn(Modifier.padding(padding).fillMaxSize(),contentPadding = PaddingValues(16.dp),verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text("每本作品独立保存 · 细纲与概要不计入正文字数",style = MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    SearchField(query,{query=it.take(200)},"搜索标题或内容",Modifier.fillMaxWidth().padding(vertical=12.dp))
                    FilterChips(listOf("")+PlanningKind.entries.map {it.name},filter,{if(it.isEmpty()) "全部" else PlanningKind.valueOf(it).label},{filter=it})
                    error?.let { Text(it,color = MaterialTheme.colorScheme.error) }
                    notice?.let { Text(it) }
                    deleted?.let { removed -> Row {Text("已删除："+removed.title,Modifier.weight(1f));TextButton(enabled=!busy,onClick={scope.launch{model.undoDelete()}}){Text("撤销上次删除")}}}
                }
                item { ToolRow(Icons.Filled.List,"打开全局便签与伏笔箱","跨作品的便签与未回收伏笔",onClick={inboxOpen=true}) }
                val visible = entries.filterNot { it.kind==PlanningKind.INSPIRATION.name && it.body.startsWith("〔轻灵速记·1〕") }
                if (visible.isEmpty()) item { EmptyState("还没有匹配的条目",subtitle="点右上角 + 添加，或换个筛选试试",icon=WritingIcons.Scroll,actionLabel="＋ 添加第一条",onAction={createMenu=true}) }
                items(visible,key = { it.id }) { entry ->
                    val owner = entry.chapterId?.let { cid -> volumes.flatMap { it.chapters }.find { it.id == cid }?.title }
                        ?: entry.volumeId?.let { vid -> volumes.find { it.id == vid }?.title }
                    ListRow(title=entry.title,subtitle=PlanningKind.valueOf(entry.kind).label+(owner?.let {" · "+it} ?: "")+"\n"+entry.body.ifEmpty {"（暂无内容）"},
                        icon=WritingIcons.Scroll,onClick={scope.launch{model.edit(entry)}})
                }
            }
        }
    }
    draft?.let { editing ->
        WritingSheet(PlanningKind.valueOf(editing.entry.kind).label,onDismiss=::back,canDismiss=!busy,compact=true,scopeLabel="本书",
            footer={Button(enabled=!busy,onClick=::back,modifier=Modifier.fillMaxWidth()){Text("保存并返回")}}) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (busy) "正在保存…" else if (editing.dirty) "尚未保存 · 停笔约 1 秒自动保存" else if (editing.entry.version == 0L) "新条目 · 输入后自动保存" else "已保存到本机",style = MaterialTheme.typography.labelMedium)
                error?.let { Text(it,color = MaterialTheme.colorScheme.error) }
                notice?.let { Text(it) }
                AppTextField(editing.entry.title,{ model.update(title = it) },readOnly=busy,label = { Text(if(editing.entry.kind == PlanningKind.CHARACTER.name) "姓名（最多 120 字）" else "标题（最多 120 字）") },singleLine = true,modifier = Modifier.fillMaxWidth())
                if (editing.entry.kind == PlanningKind.CHARACTER.name) {
                    val card = CharacterCard.decode(editing.entry.body)
                    AppTextField(card.aliases,{ model.update(body = card.copy(aliases = it.replace('\n',' ').replace('\r',' ')).encode()) },readOnly=busy,label = { Text("别名") },singleLine = true,modifier = Modifier.fillMaxWidth())
                    AppTextField(card.identity,{ model.update(body = card.copy(identity = it.replace('\n',' ').replace('\r',' ')).encode()) },readOnly=busy,label = { Text("身份") },singleLine = true,modifier = Modifier.fillMaxWidth())
                    AppTextField(card.introduction,{ model.update(body = card.copy(introduction = it).encode()) },readOnly=busy,label = { Text("简介") },minLines=4,maxLines=8,modifier = Modifier.fillMaxWidth())
                } else if(editing.entry.kind==PlanningKind.CHAPTER_OUTLINE.name)ChapterOutlineEditor(editing.entry.id,editing.entry.body,busy){model.update(body=it)}
                else AppTextField(editing.entry.body,{ model.update(body = it) },readOnly=busy,label = { Text("${PlanningKind.valueOf(editing.entry.kind).label}内容") },
                    minLines=4,maxLines=10,modifier = Modifier.fillMaxWidth())
                if (editing.entry.kind == PlanningKind.INSPIRATION.name) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppSecondaryButton(enabled = !busy && volumes.isNotEmpty() && editing.entry.body.isNotBlank(),onClick = {
                        targetVolume = volumes.firstOrNull { it.chapters.any { c -> c.id == currentChapterId } }?.id ?: volumes.firstOrNull()?.id
                        convertConfirm = true
                    }) { Text("复制成新章节") }
                    if (onInsert != null) AppSecondaryButton(enabled = !busy && editing.entry.body.isNotEmpty(),onClick = { insertConfirm = true }) { Text("插入当前章") }
                }
                if(editing.entry.version>0 && editing.entry.chapterId==null && editing.entry.volumeId==null) TextButton(enabled=!busy,onClick={deleteConfirm=true}) {Text("删除此条目",color=MaterialTheme.colorScheme.error)}
                if (editing.dirty && error != null) QuietTextButton(onClick = { discardConfirm = true }) { Text("放弃尚未保存的修改") }
            }
        }
    }
        if(deleteConfirm) ConfirmDialog("删除此条目？","只删除当前设定或便签，不影响章节正文。未保存的修改一并放弃；返回本次列表后可撤销上次删除，退出本次页面后请从已有备份恢复。","确认删除",onConfirm={scope.launch{model.deleteCurrent();deleteConfirm=false}},onDismiss={if(!busy)deleteConfirm=false})
        if (discardConfirm) AlertDialog(onDismissRequest = { discardConfirm = false },title = { Text("放弃未保存文字？") },
            text = { Text("已保存内容保留。本次尚未成功保存的文字会丢失，请先复制到安全位置。") },
            confirmButton = { ConfirmAction(onClick = { model.discard();discardConfirm = false }) { Text("确认放弃") } },
            dismissButton = { QuietTextButton(onClick = { discardConfirm = false }) { Text("继续编辑") } })
        if (convertConfirm) AlertDialog(onDismissRequest = { if(!busy) convertConfirm = false },title = { Text("复制便签为新章") },
            text = { Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                Text("原便签保留；新章追加到所选卷末尾，不计作新写作字数。")
                volumes.forEach { volume -> Row { RadioButton(targetVolume == volume.id,{ targetVolume = volume.id });QuietTextButton(onClick = { targetVolume = volume.id }) { Text(volume.title) } } }
            } },confirmButton = { ConfirmAction(enabled = !busy && targetVolume != null,onClick = { scope.launch {
                val id = targetVolume?.let { model.convert(it) }
                if (id != null) { convertConfirm = false;notice="已创建新章节，可在目录中点按打开" }
                else convertConfirm = false
            } }) { Text("确认创建") } },dismissButton = { QuietTextButton(enabled = !busy,onClick = { convertConfirm = false }) { Text("取消") } })
        if (insertConfirm) AlertDialog(onDismissRequest = { insertConfirm = false },title = { Text("插入便签内容") },
            text = { Text("将内容插入当前章光标位置，不替换选中文字。原便签保留，可在正文中撤销。") },
            confirmButton = { ConfirmAction(enabled = !busy,onClick = { scope.launch {
                if (model.save() && model.draft.value?.dirty == false) {
                    val text = model.draft.value?.entry?.body.orEmpty()
                    if (onInsert?.invoke(text) == true) { insertConfirm = false;model.finishEdit();onDismiss() }
                    else { notice = "正文尚未保存成功或状态已变化，未插入，请重试。";insertConfirm = false }
                } else insertConfirm = false
            } }) { Text("确认插入") } },dismissButton = { QuietTextButton(onClick = { insertConfirm = false }) { Text("取消") } })
}
