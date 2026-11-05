package com.qingling.writing.feature.settings
import com.qingling.writing.core.design.AppChoice

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qingling.writing.BuildConfig
import com.qingling.writing.core.design.BackButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun InformationDialog(initialPrivacy: Boolean,onDismiss:()->Unit) {
    var selected by remember {mutableStateOf(if(initialPrivacy) "privacy" else "about")}
    val context=LocalContext.current.applicationContext
    val text by produceState("正在读取…",selected) {
        value=if(selected=="about") "轻灵写作\n版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）\n\n维护者：C\n联系邮箱：nhwjzs@gmail.com\n\n本地优先的 Android 写作工具。当前为内部预览版本，新功能即将上线……\n\n反馈请提供版本和复现步骤。重要稿件请保留独立备份。"
            else withContext(Dispatchers.IO) {runCatching {context.assets.open("legal/$selected.txt").bufferedReader().use {it.readText()}}.getOrDefault("说明读取失败，请重新打开")}
    }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Scaffold(Modifier.fillMaxSize(),topBar={TopAppBar(title={Text("关于与数据说明")},navigationIcon={BackButton(onClick=onDismiss)})}) {padding->
            Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf("about" to "关于","privacy" to "隐私与数据","terms" to "使用约定","changelog" to "更新记录").forEach { (key,label)->AppChoice(selected==key,{selected=key},label={Text(label)}) }
                }
                key(selected) {Text(text,Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(vertical=12.dp))}
            }
        }
    }
}
