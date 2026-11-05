package com.qingling.writing.feature.outline
import com.qingling.writing.core.design.QuietTextButton
import com.qingling.writing.core.design.ConfirmAction
import com.qingling.writing.core.design.AppTextField

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.qingling.writing.core.model.*

@Composable internal fun PositionDialog(volume: OutlineVolume,chapter: OutlineChapter?,volumes: List<OutlineVolume>,onDismiss:()->Unit,onConfirm:(String,Int)->Unit) {
    var selected by remember {mutableStateOf(volume.id)}
    var position by remember {mutableStateOf("1")}
    val target=volumes.firstOrNull {it.id==selected}
    val size=if(chapter==null) volumes.size else (target?.chapters?.count {it.id!=chapter.id} ?: 0)+1
    val index=position.toIntOrNull()
    AlertDialog(onDismissRequest=onDismiss,title={Text("移到指定位置")},text={
        Column(Modifier.heightIn(max=450.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(if(chapter==null) volume.title else chapter.title)
            if(chapter!=null) volumes.forEach { v->Row { RadioButton(selected==v.id,{selected=v.id;position="1"});Text(v.title,Modifier.padding(top=12.dp)) } }
            AppTextField(position,{position=it.filter(Char::isDigit).take(6)},singleLine=true,label={Text("最终位置（1–$size）")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
            Text("从 1 开始计数；移动后按新位置重新排列。")
        }
    },confirmButton={ConfirmAction(enabled=index!=null && index in 1..size,onClick={onConfirm(selected,checkNotNull(index)-1)}){Text("确认移动")}},
        dismissButton={QuietTextButton(onClick=onDismiss){Text("取消")}})
}
