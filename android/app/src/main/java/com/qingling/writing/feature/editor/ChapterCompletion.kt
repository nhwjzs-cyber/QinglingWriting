package com.qingling.writing.feature.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

internal data class CompletionData(val title:String,val words:Long,val minutes:Int,val today:Long)

@Composable
internal fun CompletionNotice(data:CompletionData,full:Boolean,onExpand:()->Unit,onDismiss:()->Unit,onDisable:()->Unit,
    modifier:Modifier=Modifier) {
    LaunchedEffect(data,full) {if(!full){delay(6500);onDismiss()}}
    if(full) Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(26.dp),verticalArrangement=Arrangement.Center,
                horizontalAlignment=Alignment.CenterHorizontally) {
                Text("✓ 本章已完成",style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.primary)
                Text(data.title,Modifier.padding(vertical=24.dp),style=MaterialTheme.typography.titleLarge)
                Card {
                    Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                        Text("本章 "+data.words+" 字")
                        Text("本次会话 "+data.minutes+" 分钟")
                        Text("今日净增 "+data.today+" 字")
                    }
                }
                Button(onClick=onDismiss,modifier=Modifier.fillMaxWidth().padding(top=28.dp).height(54.dp)){Text("继续写作")}
                TextButton(onClick={onDisable();onDismiss()}){Text("不再自动显示小结页",color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }
        }
    } else Snackbar(modifier=modifier,containerColor=MaterialTheme.colorScheme.surfaceContainerHighest,contentColor=MaterialTheme.colorScheme.onSurface,action={TextButton(onClick=onExpand){Text("展开")}},dismissAction={TextButton(onClick=onDismiss){Text("关闭")}}) {
        Text("本章完成 · "+data.words+" 字\n本次 "+data.minutes+" 分钟 · 今日净增 "+data.today+" 字")
    }
}
