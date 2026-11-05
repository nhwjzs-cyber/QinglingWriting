package com.qingling.writing.core.design

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Regular sheets keep 70/92% snaps; compact sheets measure their content with a 92% ceiling. */
@Composable
fun WritingSheet(title:String,scopeLabel:String="当前章",onDismiss:()->Unit,canDismiss:Boolean=true,
    footer:(@Composable ColumnScope.()->Unit)?=null,compact:Boolean=false,content:@Composable ColumnScope.()->Unit) {
    val motion=LocalMotionEnabled.current
    val progress=remember{Animatable(0f)}
    var fraction by remember{mutableFloatStateOf(.70f)}
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(Unit){progress.animateTo(1f,if(motion) spring(dampingRatio=1f,stiffness=420f) else tween(120))}
    Dialog(onDismissRequest={if(canDismiss)onDismiss()},properties=DialogProperties(usePlatformDefaultWidth=false,
        decorFitsSystemWindows=false,dismissOnBackPress=false,dismissOnClickOutside=false)) {
        BackHandler{if(canDismiss)onDismiss()}
        BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
            val density=LocalDensity.current
            val keyboard=WindowInsets.ime.getBottom(density)>0
            val tight=keyboard && maxHeight<280.dp
            val target=maxHeight*(if(keyboard) .92f else fraction)
            val animatedHeight by animateDpAsState(target,if(motion)spring(dampingRatio=1f,stiffness=420f)else snap(),label="sheetHeight")
            val screenPx=with(density){maxHeight.toPx()}
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.42f*progress.value)).clickable{if(canDismiss)onDismiss()})
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .then(if(compact) Modifier.heightIn(max=maxHeight*.92f).wrapContentHeight() else Modifier.height(animatedHeight)).semantics{contentDescription="底部弹层："+title}
                .graphicsLayer{translationY=if(motion)size.height*(1f-progress.value)else 0f;alpha=if(motion)1f else progress.value},
                shape=RoundedCornerShape(topStart=28.dp,topEnd=28.dp),color=MaterialTheme.colorScheme.surfaceContainer,shadowElevation=18.dp) {
                Column((if(compact) Modifier.fillMaxWidth().wrapContentHeight() else Modifier.fillMaxSize()).navigationBarsPadding()) {
                    Column(Modifier.fillMaxWidth().pointerInput(compact,canDismiss,screenPx){
                        var dragged=0f
                        val dismissDistance=72.dp.toPx()
                        detectVerticalDragGestures(
                            onDragStart={dragged=0f}, onDragCancel={dragged=0f},
                            onDragEnd={
                                if(compact) { if(dragged>dismissDistance && canDismiss) dismiss() }
                                else if(fraction<.55f) { fraction=.70f;if(canDismiss)dismiss() }
                                else fraction=if(fraction>.81f).92f else .70f
                                dragged=0f
                            }
                        ) {change,drag ->
                            change.consume()
                            if(compact) dragged=(dragged+drag).coerceAtLeast(0f)
                            else fraction=(fraction-drag/screenPx).coerceIn(.48f,.92f)
                        }
                    }) {
                        if(!tight) Box(Modifier.align(Alignment.CenterHorizontally).padding(top=10.dp,bottom=6.dp).size(32.dp,4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.5f),RoundedCornerShape(2.dp)))
                        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            Text(title,style=if(tight)MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            if(!tight && scopeLabel.isNotBlank()) Surface(modifier=Modifier.widthIn(max=120.dp),color=MaterialTheme.colorScheme.surfaceContainerHighest,shape=RoundedCornerShape(10.dp)){Text(scopeLabel,Modifier.padding(horizontal=8.dp,vertical=5.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)}
                            IconButton(enabled=canDismiss,onClick=onDismiss){Icon(Icons.Filled.Close,"关闭"+title)}
                        }
                    }
                    Column(Modifier.weight(1f,fill=!compact).fillMaxWidth(),content=content)
                    footer?.let {Column(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=if(tight)0.dp else 12.dp),content=it)}
                }
            }
        }
    }
}
