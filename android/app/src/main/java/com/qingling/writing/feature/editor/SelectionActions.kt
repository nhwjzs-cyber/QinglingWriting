package com.qingling.writing.feature.editor

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import com.qingling.writing.domain.validation.WordCounter

internal val SelectionModeIcon = ImageVector.Builder("Selection",24.dp,24.dp,24f,24f).apply {
    path(fill=null,stroke=SolidColor(Color.Black),strokeLineWidth=1.8f) {
        moveTo(8f,3f);lineTo(3f,3f);lineTo(3f,8f)
        moveTo(16f,3f);lineTo(21f,3f);lineTo(21f,8f)
        moveTo(3f,16f);lineTo(3f,21f);lineTo(8f,21f)
        moveTo(21f,16f);lineTo(21f,21f);lineTo(16f,21f)
        moveTo(8f,12f);lineTo(16f,12f)
    }
}.build()

internal fun paragraphRange(text: CharSequence, offset: Int): TextRange {
    val point=offset.coerceIn(0,text.length)
    val start=(point-1 downTo 0).firstOrNull {text[it]=='\n'}?.plus(1) ?: 0
    val end=(point until text.length).firstOrNull {text[it]=='\n'} ?: text.length
    return TextRange(start,end)
}

internal fun Modifier.selectParagraphOnTap(enabled: Boolean,state: TextFieldState,scroll: ScrollState,
    layout: ()->TextLayoutResult?): Modifier = if(!enabled) this else pointerInput(state,enabled) {
    awaitEachGesture {
        val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
        var moved=false
        var event=awaitPointerEvent(PointerEventPass.Final)
        while(event.changes.any {it.pressed}) {
            if(event.changes.any {(it.position-down.position).getDistance()>viewConfiguration.touchSlop}) moved=true
            event=awaitPointerEvent(PointerEventPass.Final)
        }
        if(!moved && event.changes.size==1) {
            val result=layout()
            if(result!=null && state.composition==null) {
                val pos=down.position+Offset(0f,scroll.value.toFloat())
                val range=paragraphRange(state.text,result.getOffsetForPosition(pos))
                state.edit {selection=range}
            }
        }
    }
}

@Composable
internal fun SelectionActions(text:String,onExit:()->Unit,onNote:(Boolean)->Unit,onCopy:()->Unit,onSearch:()->Unit, coordinates: (@Composable ()->Unit)? = null) {
    Surface(color=MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                TextButton(onClick=onExit){Text("退出",color=MaterialTheme.colorScheme.onSurfaceVariant)}
                if (coordinates != null) coordinates() else Text("拖动选文字，点段落选整段",style=MaterialTheme.typography.labelMedium,modifier=Modifier.padding(top=12.dp))
            }
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(enabled=text.isNotBlank(),shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),onClick={onNote(true)}){Text("设为伏笔")}
                FilledTonalButton(shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),enabled=text.isNotBlank(),onClick={onNote(false)}){Text("存便签")}
                FilledTonalButton(shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),enabled=text.isNotBlank(),onClick=onCopy){Text("复制")}
                FilledTonalButton(shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),enabled=text.isNotBlank(),onClick=onSearch){Text("查全书")}
            }
        }
    }
}
