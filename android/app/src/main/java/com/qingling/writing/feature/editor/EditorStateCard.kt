package com.qingling.writing.feature.editor
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
@Composable
internal fun EditorStateCard(title:String,description:String,onBack:()->Unit,action:String?=null,onAction:()->Unit={}) {
    Column(Modifier.fillMaxSize().padding(28.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
        Text(title,style=MaterialTheme.typography.titleLarge)
        Text(description,Modifier.padding(vertical=20.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(action!=null)Button(onClick=onAction,modifier=Modifier.fillMaxWidth().height(52.dp)){Text(action)}
        TextButton(onClick=onBack){Text("回到书架")}
    }
}
