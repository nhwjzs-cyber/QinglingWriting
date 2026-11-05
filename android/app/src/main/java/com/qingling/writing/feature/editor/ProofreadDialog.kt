package com.qingling.writing.feature.editor
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.design.*
import kotlinx.coroutines.*

@Composable
internal fun ProofreadDialog(source:String,onDismiss:()->Unit,onLocate:(ProofIssue)->Boolean,initialIgnored:Set<String> = emptySet(),onIgnored:(Set<String>)->Unit={},onLocatedSession:(ProofResult,Set<String>,ProofIssue)->Unit={_,_,_->}) {
    val container=LocalAppContainer.current;val scope=rememberCoroutineScope()
    val saved by container.settingsStore.proofWords.collectAsStateWithLifecycle(initialValue=null)
    var words by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(saved){if(words==null&&saved!=null)words=saved}
    var result by remember{mutableStateOf<ProofResult?>(null)}
    var ignored by remember{mutableStateOf(initialIgnored)}
    var editingWords by remember{mutableStateOf(false)}
    var busy by remember{mutableStateOf(false)};var message by remember{mutableStateOf<String?>(null)}
    fun scan(){if(busy||words==null)return;busy=true;scope.launch{
        try{val entries=words.orEmpty().lines().map{it.trim()}.filter{it.isNotEmpty()};require(entries.size<=100&&entries.all{it.length<=80}){"词表最多100行，每项最多80字符"};container.settingsStore.setProofWords(words.orEmpty());result=withContext(Dispatchers.Default){Proofreader.scan(source,entries)};message=null}
        catch(e:CancellationException){throw e}catch(e:Exception){message=e.message?:"检查失败，请重试"}finally{busy=false}
    }}
    LaunchedEffect(words!=null){if(words!=null&&result==null)scan()}
    WritingSheet("本地校对",onDismiss=onDismiss,canDismiss=!busy,footer={
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
            AppSecondaryButton(onClick={ignored=result?.issues?.map{it.stableKey}?.toSet().orEmpty();onIgnored(ignored)},modifier=Modifier.weight(1f)){Text("全部忽略")}
            Button(onClick=onDismiss,enabled=!busy,modifier=Modifier.weight(1f).heightIn(min=48.dp)){Text("完成")}
        }
    }) {
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item {Text("结果仅供参考，不会自动修改正文",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth()) {TextButton(onClick={editingWords=true},modifier=Modifier.weight(1f)){Text("词表 · "+words.orEmpty().lines().count{it.isNotBlank()}+" 项",color=MaterialTheme.colorScheme.onSurfaceVariant)};TextButton(enabled=!busy,onClick=::scan){Text("重新检查")}}
                if(busy)LinearProgressIndicator(Modifier.fillMaxWidth());message?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            }
            val visible=result?.issues.orEmpty().filterNot{it.stableKey in ignored}
            if(result!=null&&visible.isEmpty())item{CenterMessage("本次检查没有待处理提示")}
            visible.groupBy {when{it.message.contains("重复")->"重复字词";it.message.contains("标点")->"标点";it.message.contains("词表")->"词表";else->"段落"}}.forEach{(group,issues)->
                item{Text(group+" · "+issues.size,style=MaterialTheme.typography.titleMedium)}
                items(issues){issue->Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){
                    Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                        Text(issue.message,style=MaterialTheme.typography.labelMedium);Text(issue.excerpt,maxLines=3,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Row{TextButton(onClick={if(onLocate(issue)){result?.let{onLocatedSession(it,ignored,issue)};onDismiss()}else message="正文已变化，请重新检查"}){Text("定位")};TextButton(onClick={ignored=ignored+issue.stableKey;onIgnored(ignored)}){Text("忽略",color=MaterialTheme.colorScheme.onSurfaceVariant)}}
                    }
                }}
            }
        }
    }
    if(editingWords) WritingSheet("校对词表",scopeLabel="最多100项",onDismiss={editingWords=false},footer={Button(onClick={editingWords=false;scan()},modifier=Modifier.fillMaxWidth()){Text("保存并检查")}}){AppTextField(words.orEmpty(),{if(it.length<=5000)words=it},label={Text("每行一个词")},modifier=Modifier.fillMaxSize().padding(20.dp))}
}
