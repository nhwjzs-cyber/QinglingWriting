package com.qingling.writing.feature.planning

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.qingling.writing.core.design.AppTextField
import com.qingling.writing.core.model.*

@Composable internal fun ChapterOutlineEditor(id:String,body:String,busy:Boolean,onChange:(String)->Unit){
    var raw by rememberSaveable(id){mutableStateOf(false)}
    var error by remember(id){mutableStateOf<String?>(null)}
    fun change(text:String){if(text.length<=PlanningRules.MAX_BODY){onChange(text);error=null}else error="单条最多10万字符，请拆分后再填写"}
    TextButton(enabled=!busy,onClick={raw=!raw}){Text(if(raw)"结构化编辑"else"原文编辑 ›")}
    error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    if(raw)AppTextField(body,::change,readOnly=busy,label={Text("细纲原文")},minLines=4,maxLines=12,modifier=Modifier.fillMaxWidth())
    else {
        val form=remember(body){ChapterOutlineForm.fromBody(body)}
        AppTextField(form.goal,{change(form.copy(goal=it).toBody())},readOnly=busy,label={Text("本章目标")},minLines=2,maxLines=6,modifier=Modifier.fillMaxWidth())
        Text("分场 · 点按状态推进",style=MaterialTheme.typography.labelSmall)
        form.scenes.forEachIndexed{index,scene->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                TextButton(enabled=!busy,onClick={change(form.cycle(index).toBody())}){Text(scene.status.label)}
                AppTextField(scene.text,{text->change(form.copy(scenes=form.scenes.mapIndexed{i,s->if(i==index)s.copy(text=text)else s}).toBody())},readOnly=busy,singleLine=true,modifier=Modifier.weight(1f),placeholder={Text("分场内容")})
                TextButton(enabled=!busy,onClick={change(form.copy(scenes=form.scenes.filterIndexed{i,_->i!=index}).toBody())}){Text("删除")}
            }
        }
        TextButton(enabled=!busy,onClick={change(form.copy(scenes=form.scenes+OutlineSceneForm("",SceneStatus.TODO)).toBody())}){Text("＋ 添加分场")}
    }
}
