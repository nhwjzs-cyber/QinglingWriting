package com.qingling.writing.feature.outline
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.PinnableContainer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable internal fun ReorderGrip(label: String,onStart:()->Unit,onDrag:(Float)->Unit,onEnd:()->Unit,onCancel:()->Unit) {
    val pinContainer=LocalPinnableContainer.current
    var pin by remember {mutableStateOf<PinnableContainer.PinnedHandle?>(null)}
    val start by rememberUpdatedState(onStart);val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onEnd);val cancel by rememberUpdatedState(onCancel)
    DisposableEffect(Unit) {onDispose {pin?.release();pin=null}}
    val ink=MaterialTheme.colorScheme.onSurfaceVariant
    Box(Modifier.size(48.dp).semantics {contentDescription="$label，长按后拖动"}.pointerInput(pinContainer) {
        detectDragGesturesAfterLongPress(onDragStart={pin=pinContainer?.pin();start()},
            onDrag={change,delta->change.consume();drag(delta.y)},
            onDragEnd={end();pin?.release();pin=null},onDragCancel={cancel();pin?.release();pin=null})
    },contentAlignment=Alignment.Center) {
        Canvas(Modifier.size(18.dp)) {for(x in listOf(.3f,.7f)) for(y in listOf(.2f,.5f,.8f)) drawCircle(ink,1.3.dp.toPx(),androidx.compose.ui.geometry.Offset(size.width*x,size.height*y))}
    }
}
