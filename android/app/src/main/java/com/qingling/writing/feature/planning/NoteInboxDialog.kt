package com.qingling.writing.feature.planning
import androidx.compose.runtime.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
@Composable
fun NoteInboxDialog(onDismiss:()->Unit,bookId:String?=null,initialTab:Int=0,initialChapterId:String?=null,
    onInsert:(suspend(String)->Boolean)?=null,onOpenSource:(String,Int,Int)->Unit={_,_,_->},currentChapterId:String?=null,onOpenGlobal:(()->Unit)?=null,onCapture:((Boolean)->Unit)?=null,currentSourceTitle:String="") {
    var global by remember{mutableStateOf(bookId==null)}
    val openSource:(String,Int,Int)->Unit={id,start,end->onDismiss();onOpenSource(id,start,end)}
    if(global)Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false,dismissOnClickOutside=false)){
        InspirationPage(onOpenSource=openSource,onClose=onDismiss)
    }else BookInspirationSheet(bookId!!,onDismiss,onOpenGlobal={if(onOpenGlobal!=null){onDismiss();onOpenGlobal()}else global=true},onOpenSource=openSource,
        currentChapterId=currentChapterId,initialChapterId=initialChapterId,initialTab=initialTab,onInsert=onInsert,onCapture=onCapture,currentSourceTitle=currentSourceTitle)
}
