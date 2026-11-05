package com.qingling.writing.feature.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/** Threshold raw finger movement, not scroll offsets changed by the chrome animation itself. */
internal class EditorChromeScrollConnection(private val threshold:Float,private val allowed:()->Boolean,
    private val change:(Boolean)->Unit,private val atTop:()->Boolean,
    private val reachedTop:()->Unit={change(true)}):NestedScrollConnection {
    private var distance=0f
    private var switched=false
    private var touching=false
    fun beginGesture(){distance=0f;switched=false;touching=true}
    fun endGesture(){distance=0f;switched=false;touching=false}
    override fun onPreScroll(available:Offset,source:NestedScrollSource):Offset {
        if(source!=NestedScrollSource.UserInput || !allowed()) {distance=0f;return Offset.Zero}
        if(switched && touching)return Offset.Zero
        val delta=available.y
        if(delta==0f)return Offset.Zero
        if(distance*delta<0f)distance=0f
        distance+=delta
        if(kotlin.math.abs(distance)>=threshold) {
            change(distance>0f);distance=0f;switched=true
        }
        return Offset.Zero
    }
    override fun onPostScroll(consumed:Offset,available:Offset,source:NestedScrollSource):Offset {
        // Only real upward document scrolling may invoke the top-edge fallback.
        if(source==NestedScrollSource.UserInput && allowed() && consumed.y>0f && atTop() && !switched) {
            reachedTop();switched=true;distance=0f
        }
        return Offset.Zero
    }
    override suspend fun onPostFling(consumed:Velocity,available:Velocity):Velocity {endGesture();return Velocity.Zero}
}
