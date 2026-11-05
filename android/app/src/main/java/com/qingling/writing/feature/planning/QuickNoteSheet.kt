package com.qingling.writing.feature.planning

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.model.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickNoteSheet(onDismiss: () -> Unit, bookId: String? = null, chapterId: String = "",
    sourceTitle: String = "", offset: Int = 0, initialText: String = "", foreshadow: Boolean = false,
    existing: PlanningEntry? = null, onSaved: () -> Unit = {}, onDraftChanged: (String)->Unit = {},sourceQuote:String="",recoveryKey:String?=null,restoredText:String?=null,restoredOwner:String?=null) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val books by remember{container.writingRepository.observeBooks(GuestSpace.ID)}.collectAsStateWithLifecycle(initialValue=emptyList())
    var owner by rememberSaveable(existing?.id){mutableStateOf(restoredOwner ?: existing?.bookId ?: bookId ?: "")}
    var expandedQuote by rememberSaveable {mutableStateOf(false)}
    var ownerPicker by remember{mutableStateOf(false)}
    var text by rememberSaveable(existing?.id, initialText) { mutableStateOf(restoredText ?: existing?.let { QuickNote.decode(it.body).text } ?: initialText) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var discard by remember { mutableStateOf(false) }
    val quote=existing?.let {QuickNote.decode(it.body).sourceQuote} ?: sourceQuote
    val originalOwner=existing?.bookId ?: bookId ?: ""
    val original = existing?.let { QuickNote.decode(it.body).text } ?: initialText
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) { text = (text + if (text.isBlank()) "" else "\n") + spoken;onDraftChanged(text) }
    }
    val protectedId=rememberSaveable{recoveryKey ?: ("quick:"+java.util.UUID.randomUUID().toString())}
    var completed by remember{mutableStateOf(false)}
    fun protect(){if(completed)return
        val dirty=if(existing==null)text.isNotBlank()||quote.isNotBlank()else text!=original||owner!=originalOwner
        if(!dirty){container.recoveryDrafts.remove(protectedId);return}
        container.recoveryDrafts.put(protectedId,org.json.JSONObject().put("kind","quick").put("title",if(foreshadow)"未保存伏笔"else"未保存便签").put("text",text).put("quote",quote).put("bookId",owner).put("chapterId",chapterId).put("sourceTitle",sourceTitle).put("offset",offset).put("foreshadow",existing?.let{QuickNote.decode(it.body).foreshadow} ?: foreshadow).put("entryId",existing?.id.orEmpty()).put("version",existing?.version ?: 0))
    }
    LaunchedEffect(text,owner){protect()}
    fun finishDraft(){completed=true;container.recoveryDrafts.remove(protectedId);onDismiss()}
    fun dismiss() { if (!busy) { if ((existing==null && (text.isNotBlank() || quote.isNotBlank())) || (existing!=null && (text!=original || owner!=originalOwner))) discard = true else finishDraft() } }
    fun save() {
        if (busy || (text.isBlank() && quote.isBlank())) return
        busy = true; error = null
        scope.launch {
            try {
                if (existing == null) container.planning.capture(text,owner,chapterId,sourceTitle,offset,foreshadow,quote)
                else container.planning.saveNoteWithOwner(existing,text,owner)
                keyboard?.hide();completed=true;container.recoveryDrafts.remove(protectedId);onSaved();onDismiss()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "保存失败，文字仍在，请重试" }
            finally { busy = false }
        }
    }
    com.qingling.writing.core.design.WritingSheet(if(foreshadow||existing?.let{QuickNote.decode(it.body).foreshadow}==true)"记一笔伏笔"else"灵感速记",
        scopeLabel=if(bookId.isNullOrBlank())"灵感"else"本书",onDismiss=::dismiss,canDismiss=!busy,compact=true,footer={
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            TextButton(enabled=!busy,onClick={try{voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE,"zh-CN"))}catch(_:Exception){error="设备没有可用的系统语音输入，请使用键盘"}}){Text("语音输入",color=MaterialTheme.colorScheme.onSurfaceVariant)}
            TextButton(enabled=!busy,onClick=::dismiss){Text("收起",color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Button(enabled=!busy&&(text.isNotBlank()||quote.isNotBlank())&&text.length+quote.length<=50_000,shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp),onClick=::save,modifier=Modifier.weight(1f).height(48.dp)){Text(if(busy)"正在保存…"else"保存")}
        }
    }) {
        val sheetKeyboard=LocalSoftwareKeyboardController.current
        val windowFocused=androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused
        LaunchedEffect(windowFocused){if(windowFocused){kotlinx.coroutines.delay(150);focus.requestFocus();sheetKeyboard?.show()}}
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(bookId==null||existing!=null)TextButton(enabled=!busy,onClick={ownerPicker=true}){Text("归入："+(books.find{it.id==owner}?.title?:"通用灵感箱"),color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Text(if(sourceTitle.isBlank())"先记下来，稍后归类"else"来源："+sourceTitle,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=3)
            if(quote.isNotBlank()) { Text("引用原文（保留原样）",style=MaterialTheme.typography.labelMedium);Text(quote,maxLines=if(expandedQuote) Int.MAX_VALUE else 6,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis);TextButton(onClick={expandedQuote=!expandedQuote}){Text(if(expandedQuote) "收起原文" else "展开原文")} }
            else if(existing!=null && QuickNote.decode(existing.body).chapterId.isNotBlank()) Text(if(QuickNote.decode(existing.body).sourceKind.isBlank()) "旧记录未保留原文引用" else "关联章节，未选择原文",style=MaterialTheme.typography.labelSmall)
            com.qingling.writing.core.design.AppTextField(text,{if(it.length+quote.length<=50_000){text=it;protect();onDraftChanged(it)}else error="单条最多5万字符，请拆分保存"},label={Text(if(foreshadow||existing?.let{QuickNote.decode(it.body).foreshadow}==true) "伏笔备注" else "便签备注")},placeholder={Text(if(foreshadow||existing?.let{QuickNote.decode(it.body).foreshadow}==true) "后续准备如何呼应或回收？" else "写下自己的想法；引用原文会单独保留")},readOnly=busy,
                minLines=4,maxLines=8,modifier=Modifier.fillMaxWidth().focusRequester(focus))
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }
    }

    if(ownerPicker) NoteOwnerDialog(books,owner.orEmpty(),onDismiss={ownerPicker=false},onConfirm={owner=it;protect();ownerPicker=false})
    if(discard) AlertDialog(onDismissRequest={discard=false},title={Text("保存本次修改？")},text={Text("备注或归属修改尚未保存。可以保存后关闭，也可以继续编辑或放弃本次修改。") },
        confirmButton={Button(enabled=text.isNotBlank()||quote.isNotBlank(),onClick={discard=false;save()}){Text("保存并关闭")}},
        dismissButton={Row{TextButton(onClick={discard=false}){Text("继续编辑")};TextButton(onClick={discard=false;finishDraft()}){Text("放弃修改")}}})
}
