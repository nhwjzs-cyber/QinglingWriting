package com.qingling.writing.feature.planning
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.withTransaction
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.design.*
import com.qingling.writing.core.model.*
import kotlinx.coroutines.*
import org.json.JSONObject
@Composable fun RecoveryDraftsSheet(onDismiss:()->Unit) {
 val c=LocalAppContainer.current;val scope=rememberCoroutineScope();val clipboard=LocalClipboardManager.current;val context=LocalContext.current
 val entries by c.recoveryDrafts.entries.collectAsStateWithLifecycle();val problem by c.recoveryDrafts.problem.collectAsStateWithLifecycle()
 var selected by rememberSaveable{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
 var expanded by rememberSaveable(selected){mutableStateOf(false)}
 var deleting by remember{mutableStateOf(false)};var quick by rememberSaveable{mutableStateOf(false)};var existing by remember{mutableStateOf<PlanningEntry?>(null)}
 var exportText by remember{mutableStateOf("")}
 var notice by remember{mutableStateOf<String?>(null)}
 val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")){uri->if(uri!=null)scope.launch{busy=true;try{withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri,"wt")!!.use{it.write(exportText.toByteArray(Charsets.UTF_8))}};error="已导出保护草稿"}catch(e:CancellationException){throw e}catch(_:Exception){error="导出失败，保护草稿仍保留"}finally{busy=false}}}
 fun action(block:suspend()->Unit){if(!busy){busy=true;scope.launch{try{block();error=null}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message?:"操作未完成，保护草稿仍保留"}finally{busy=false}}}}
 WritingSheet("未保存草稿",scopeLabel="本机保护副本",onDismiss=onDismiss,canDismiss=!busy){
  LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
   item{Text("异常退出留下的保护副本。已保存的正文会按版本校验恢复；有冲突时保留两份，避免自动覆盖。",style=MaterialTheme.typography.bodySmall);problem?.let{Text(it,color=MaterialTheme.colorScheme.error)}}
   notice?.let{message->item{Text(message,color=MaterialTheme.colorScheme.primary)}}
   if(entries.isEmpty())item{EmptyState("没有待处理草稿",subtitle="正常保存或明确放弃后，保护副本会移除")}
   items(entries,key={it.getString("key")}){j->ListRow(title=j.optString("title").ifBlank{"未命名草稿"},subtitle=(when(j.optString("kind")){"quick"->"便签或伏笔";"planning"->"设定或细纲";"editorConflict"->"正文版本不同 · 原文保留";else->"正文保护副本"})+" · "+j.optString("text").take(80),onClick={selected=j.getString("key");error=null})}
  }
 }
 val j=entries.firstOrNull{it.optString("key")==selected}
 if(j!=null && !quick)WritingSheet("保护草稿",scopeLabel="先核对再恢复",onDismiss={if(!busy)selected=null},canDismiss=!busy){
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
   Text(j.optString("title"),style=MaterialTheme.typography.titleMedium)
   if(j.optString("quote").isNotBlank()){Text("引用原文");SelectionContainer{Text(j.optString("quote"),maxLines=if(expanded)Int.MAX_VALUE else 4,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)}}
   SelectionContainer{Text(j.optString("text"),maxLines=if(expanded)Int.MAX_VALUE else 8,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)}
   TextButton(onClick={expanded=!expanded}){Text(if(expanded)"收起全文"else"展开全文")}
   error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
   AppSecondaryButton(enabled=!busy,onClick={try{clipboard.setText(AnnotatedString(j.optString("text")));error="已复制全部文字"}catch(_:Exception){error="复制未成功，请导出TXT"}},modifier=Modifier.fillMaxWidth()){Text("复制全部文字")}
   AppSecondaryButton(enabled=!busy,onClick={exportText=listOf(j.optString("quote"),j.optString("text")).filter{it.isNotEmpty()}.joinToString("\n\n");export.launch("轻灵写作-保护草稿.txt")},modifier=Modifier.fillMaxWidth()){Text("导出TXT（保留原文与备注）")}
   when(j.optString("kind")) {
    "quick"->AppSecondaryButton(enabled=!busy,onClick={action{val id=j.optString("entryId");existing=if(id.isBlank())null else c.planning.load(id).also{check(it!=null && it.version==j.optLong("version")){"原便签已变化，请复制或导出后合并，保护副本保留"}};quick=true}},modifier=Modifier.fillMaxWidth()){Text("继续编辑备注")}
    "planning"->AppSecondaryButton(enabled=!busy,onClick={action{
      val stored=c.planning.load(j.getString("entryId"));check(stored?.version==j.optLong("version") || stored==null && j.optLong("version")==0L){"原条目已变化，请复制或导出后合并，未覆盖原内容"}
      val e=stored ?: PlanningEntry(j.getString("entryId"),j.getString("bookId"),j.optString("chapterId").takeIf{it.isNotBlank()},j.optString("volumeId").takeIf{it.isNotBlank()},j.getString("entryKind"),j.getString("title"),j.getString("text"),java.time.Instant.now().toString())
      val saved=c.planning.save(e.copy(title=j.getString("title"),body=j.getString("text")));c.recoveryDrafts.planningCommitted(saved);selected=null
    }},modifier=Modifier.fillMaxWidth()){Text("恢复到原条目（校验版本）")}
    else->{
     AppSecondaryButton(enabled=!busy,onClick={action{
      c.database.withTransaction {
       val source=c.writingRepository.readChapter(j.getString("chapterId"))
       val fresh=c.outlineRepository.createChapter(source.bookId,source.volumeId,j.optString("title").ifBlank{"恢复草稿"})
       check(c.writingRepository.saveDraft(ChapterDraft(fresh.id,fresh.title,j.getString("text"),fresh.editSeq+1,titleEdited=true)) is SaveResult.Committed){"尚未保存成功，保护副本保留"}
      };c.recoveryDrafts.remove(j.getString("key"));selected=null
    }},modifier=Modifier.fillMaxWidth()){Text("另存为新章节（保留原章）")}
     AppSecondaryButton(enabled=!busy,onClick={action{c.recoveryRescue.restoreAsNewBook(j);notice="已恢复到新书《找回的稿件》，请到书库查看。";selected=null}},modifier=Modifier.fillMaxWidth()){Text("恢复为新书（不覆盖原稿）")}
     Text("原书或章节已缺失时，仍可把这份正文恢复到一本新书，也可以先导出TXT。",style=MaterialTheme.typography.bodySmall)
    }
   }
   TextButton(enabled=!busy,onClick={deleting=true}){Text("丢弃此保护副本",color=MaterialTheme.colorScheme.error)}
  }
 }
 LaunchedEffect(quick,selected){if(quick && j!=null && j.optString("entryId").isNotBlank() && existing==null){try{val loaded=c.planning.load(j.getString("entryId"));check(loaded!=null && loaded.version==j.optLong("version")){"原便签已变化，请复制或导出后合并"};existing=loaded}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){error=e.message;quick=false}}}
 if(j!=null && quick && (j.optString("entryId").isBlank() || existing!=null))QuickNoteSheet(onDismiss={quick=false},bookId=null,chapterId=j.optString("chapterId"),sourceTitle=j.optString("sourceTitle"),offset=j.optInt("offset"),sourceQuote=j.optString("quote"),foreshadow=j.optBoolean("foreshadow"),existing=existing,recoveryKey=j.getString("key"),restoredText=j.optString("text"),restoredOwner=j.optString("bookId"))
 if(deleting && j!=null)ConfirmDialog("丢弃保护副本？","只删除这份未保存草稿，已有正文和已存便签不变。此操作无法撤销，建议先复制或导出。","确认丢弃",onConfirm={c.recoveryDrafts.remove(j.getString("key"));deleting=false;selected=null},onDismiss={deleting=false})
}
