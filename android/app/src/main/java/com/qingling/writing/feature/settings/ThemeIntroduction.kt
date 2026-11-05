package com.qingling.writing.feature.settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.datastore.ThemeMode
import com.qingling.writing.core.model.GuestSpace
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun ThemeIntroduction() {
    val container=LocalAppContainer.current
    var visible by remember {mutableStateOf(false)}
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if(!container.settingsStore.themeInitialized.first()) {
            if(container.writingRepository.observeBooks(GuestSpace.ID).first().isEmpty()) visible=true

        }
    }
    if(visible) AlertDialog(onDismissRequest={},title={Text("选一张写作的底色")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("以后可以在设置里随时切换。",color=MaterialTheme.colorScheme.onSurfaceVariant)
        listOf(ThemeMode.DARK to "深灰",ThemeMode.LIGHT to "暖纸",ThemeMode.FOLLOW_SYSTEM to "跟随系统").forEach{(mode,label) ->
            FilledTonalButton(enabled=!busy,onClick={busy=true;scope.launch{try{container.settingsStore.setThemeMode(mode);visible=false}catch(_:Exception){error=true}finally{busy=false}}},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(label)}
        }
        if(error)Text("选择未保存，请重试",color=MaterialTheme.colorScheme.error)
    }},confirmButton={})
}
