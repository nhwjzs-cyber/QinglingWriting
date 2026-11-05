package com.qingling.writing.feature.editor
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable internal fun ProofReviewBar(issue:ProofIssue?,position:Int,total:Int,changed:Boolean,busy:Boolean,
    canFix:Boolean,onNext:()->Unit,onIgnore:()->Unit,onFix:()->Unit,onList:()->Unit,onClose:()->Unit){
    Surface(color=MaterialTheme.colorScheme.secondaryContainer){Column(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp)){
        Text((if(changed)"正文已变化 · "else"")+(if(issue==null)"本次没有待处理提示"else"第 $position / 共 $total 条 · ${issue.message}"),maxLines=2,style=MaterialTheme.typography.labelMedium)
        LazyRow{
            item{TextButton(enabled=!busy,onClick=onNext){Text("下一条")}}
            item{TextButton(enabled=!busy&&issue!=null,onClick=onIgnore){Text("忽略")}}
            if(canFix)item{TextButton(enabled=!busy&&!changed,onClick=onFix){Text("删去重复")}}
            item{TextButton(enabled=!busy,onClick=onList){Text("返回列表")}}
            item{TextButton(enabled=!busy,onClick=onClose){Text("结束校对")}}
        }
    }}
}
