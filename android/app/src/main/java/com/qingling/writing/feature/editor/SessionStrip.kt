package com.qingling.writing.feature.editor
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.model.*
import kotlinx.coroutines.delay

@Composable
internal fun SessionStrip(session:EditorSession?,onStats:()->Unit) {
    if(session==null)return
    var metrics by remember(session){mutableStateOf(session.sessionMetrics())}
    LaunchedEffect(session){while(true){metrics=session.sessionMetrics();delay(1000)}}
    val container=LocalAppContainer.current
    val goal by container.settingsStore.dailyGoal.collectAsStateWithLifecycle(initialValue=2000)
    val days by remember{container.writingProgress.days()}.collectAsStateWithLifecycle(initialValue=emptyList())
    val net=days.find{it.day==java.time.LocalDate.now().toString()}?.netWords ?: 0
    TextButton(onClick=onStats,modifier=Modifier.fillMaxWidth(),contentPadding=PaddingValues(horizontal=26.dp,vertical=0.dp)) {
        Text("本次输入 "+metrics.inserted+" 字 · 活跃 "+metrics.activeMillis/60000+" 分钟",Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if(goal==0) "目标已关闭" else if(net>=goal) "今日目标已达成" else "距目标 "+(goal-net)+" 字",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun CharacterQuickBar(bookId:String?,session:EditorSession?) {
    if(bookId==null||session==null)return
    val repo=LocalAppContainer.current.planning
    val people by remember(bookId){repo.search(bookId,"",PlanningKind.CHARACTER.name)}.collectAsStateWithLifecycle(initialValue=emptyList())
    if(people.isEmpty())return
    LazyRow(Modifier.fillMaxWidth(),contentPadding=PaddingValues(horizontal=18.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        items(people.take(12),key={it.id}){entry ->
            TextButton(enabled=session.editorState.composition==null,onClick={val state=session.editorState;if(state.composition==null)state.edit {
                val start=selection.min;replace(start,selection.max,entry.title);selection=TextRange(start+entry.title.length)
            }}) {Text(entry.title,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
    }
}
