package com.qingling.writing.feature.history
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.design.*
import com.qingling.writing.core.model.Revision
import kotlinx.coroutines.launch
@Composable
fun HistorySheet(chapterId:String,onDismiss:()->Unit,onRestored:()->Unit) {
    val repo=LocalAppContainer.current.historyRepository;val scope=rememberCoroutineScope()
    val revisions by remember(chapterId){repo.observeRevisions(chapterId)}.collectAsStateWithLifecycle(initialValue=emptyList())
    var preview by remember{mutableStateOf<Revision?>(null)};var restore by remember{mutableStateOf<Revision?>(null)}
    var compare by remember{mutableStateOf<Revision?>(null)}
    var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    fun snapshot(){if(!busy){busy=true;scope.launch{try{repo.createManualSnapshot(chapterId)}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){error="未能保存快照，请重试"}finally{busy=false}}}}
    WritingSheet("历史版本",onDismiss=onDismiss,canDismiss=!busy,footer={AppSecondaryButton(enabled=!busy,onClick=::snapshot,modifier=Modifier.fillMaxWidth()){Text("保存当前版本")}}){
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item{Text("恢复前会自动保留当前正文",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant);error?.let{Text(it,color=MaterialTheme.colorScheme.error)}}
            if(revisions.isEmpty())item{EmptyState("还没有历史版本",subtitle="自动保存的快照会显示在这里，也可以保存当前版本",icon=WritingIcons.History)}
            items(revisions,key={it.id}){r->
                Column {
                    ListRow(title=formatTimestamp(r.createdAt),subtitle=revisionSourceLabel(r.source)+" · "+r.body.ifBlank {"（空章）"},icon=WritingIcons.History,onClick={preview=r},enabled=!busy)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                        QuietTextButton(enabled=!busy,onClick={compare=r}){Text("对比")}
                        QuietTextButton(enabled=!busy,onClick={restore=r}){Text("恢复")}
                    }
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.4f))
                }
            }
        }
    }
    preview?.let{r->WritingSheet("历史版本预览",scopeLabel="当前章",onDismiss={preview=null}){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)){Text(r.body.ifBlank{"（空章）"})}}}
    compare?.let{r->RevisionDiffDialog(chapterId,r){compare=null}}
    restore?.let{r->ConfirmDialog("恢复这个版本？","当前正文会先保存为恢复前历史，然后恢复所选版本。","确认恢复",onConfirm={if(!busy){busy=true;scope.launch{try{repo.restoreRevision(chapterId,r.id);restore=null;onRestored();onDismiss()}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){error="恢复未成功，当前正文保留，请重试";restore=null}finally{busy=false}}}},onDismiss={if(!busy)restore=null})}
}

internal fun revisionSourceLabel(source:Revision.Source)=when(source) {
    Revision.Source.AUTO -> "自动快照"
    Revision.Source.MANUAL -> "手动保存"
    Revision.Source.BEFORE_RESTORE -> "恢复前"
    Revision.Source.BEFORE_CHANGE -> "修改前"
    Revision.Source.IMPORTED -> "导入版本"
}
