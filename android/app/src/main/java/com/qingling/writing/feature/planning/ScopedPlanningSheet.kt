package com.qingling.writing.feature.planning
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.design.*
import com.qingling.writing.core.model.PlanningKind
import kotlinx.coroutines.launch
@Composable
fun ScopedPlanningSheet(bookId:String,chapterId:String?,volumeId:String?,onDismiss:()->Unit,onSaved:()->Unit={}) {
    val container=LocalAppContainer.current
    val model:PlanningViewModel=viewModel(key="scoped-"+(chapterId?:volumeId),factory=viewModelFactory{initializer{PlanningViewModel(container.planning,container.outlineRepository,bookId,container.recoveryDrafts)}})
    val draft by model.draft.collectAsStateWithLifecycle();val busy by model.busy.collectAsStateWithLifecycle();val error by model.error.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope()
    val clipboard=androidx.compose.ui.platform.LocalClipboardManager.current
    var discard by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf(false)}
    var inputError by remember {mutableStateOf<String?>(null)}
    var copied by remember {mutableStateOf(false)}
    LaunchedEffect(chapterId,volumeId){model.openScope(if(chapterId!=null)PlanningKind.CHAPTER_OUTLINE else PlanningKind.VOLUME_SUMMARY,chapterId,volumeId)}
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP){model.flushOnBackground()}
    fun close(){if(!busy)scope.launch{val saved=draft?.let{it.dirty||it.entry.version>0}==true;if(model.finishEdit()){if(saved)onSaved();onDismiss()}}}
    WritingSheet(if(chapterId!=null)"章节细纲"else"分卷概要",scopeLabel=if(chapterId!=null)"当前章"else"当前卷",onDismiss=::close,canDismiss=!busy,compact=true,
        footer={Button(enabled=!busy,onClick=::close,modifier=Modifier.fillMaxWidth().height(48.dp)){Text("保存并返回")}}){
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(if(draft?.dirty==true)"停笔约1秒自动保存"else"与正文独立，备份会一起保留",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            inputError?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            if(error!=null && draft?.dirty==true) {
                Text("保存未完成，可复制文字后重试，或放弃本次修改。",style=MaterialTheme.typography.bodySmall)
                Row {TextButton(enabled=!busy,onClick={clipboard.setText(androidx.compose.ui.text.AnnotatedString(draft?.entry?.body.orEmpty()));copied=true}){Text(if(copied) "已复制当前文字" else "复制当前文字")}
                    TextButton(enabled=!busy,onClick={discard=true}){Text("放弃修改并返回")}}
            }
            if(draft==null)LinearProgressIndicator(Modifier.fillMaxWidth())
            else if(chapterId!=null)ChapterOutlineEditor(draft!!.entry.id,draft!!.entry.body,busy){model.update(body=it);inputError=null;copied=false}
            else AppTextField(draft!!.entry.body,{if(it.length<=com.qingling.writing.core.model.PlanningRules.MAX_BODY){model.update(body=it);inputError=null;copied=false}else inputError="单条最多10万字符，本次超长输入未加入，请拆分粘贴"},readOnly=busy,minLines=4,maxLines=10,modifier=Modifier.fillMaxWidth())
        }
    }
    if(discard) ConfirmDialog("放弃未保存的修改？","只放弃本次未保存文字，已经保存的细纲或概要保留。建议先复制当前文字。","放弃并返回",onConfirm={model.discard();discard=false;onDismiss()},onDismiss={discard=false})
}
