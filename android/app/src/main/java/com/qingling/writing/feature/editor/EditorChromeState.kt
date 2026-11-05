package com.qingling.writing.feature.editor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class EditorChromeState {
    var topMode by mutableStateOf(EditorTopMode.FULL)
        private set
    private var typing=false
    private var atTop=true
    private var focus=false
    private var selection=false
    fun environment(ime:Boolean,body:Boolean,title:Boolean,focused:Boolean,selected:Boolean,top:Boolean,short:Boolean){
        val beginsTyping=ime&&(body||title)&&!typing
        val reachesTop=top&&!atTop
        typing=ime&&(body||title);atTop=top;focus=focused;selection=selected
        topMode=when{focus->EditorTopMode.THIN;selection->EditorTopMode.FULL
            top&&short->EditorTopMode.FULL
            reachesTop||top&&!ime->EditorTopMode.FULL
            beginsTyping->EditorTopMode.THIN
            else->topMode}
    }
    fun scroll(full:Boolean){if(!focus&&!selection)topMode=if(full)EditorTopMode.FULL else EditorTopMode.THIN}
    fun reachedTop(){if(!focus)topMode=EditorTopMode.FULL}
    fun selectionEntered(){selection=true;topMode=EditorTopMode.FULL}
}
