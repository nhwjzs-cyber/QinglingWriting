package com.qingling.writing.feature.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.util.VelocityTracker
import com.qingling.writing.core.design.AppMotion
import kotlinx.coroutines.*
import kotlin.reflect.KProperty

/** Finger updates are direct; only release/tap settles with the shared spring. */
@Stable
internal class WritingToolDrawerState(private val scope: CoroutineScope, private val motion: () -> Boolean) {
    private var targetOpen by mutableStateOf(false)
    var progress by mutableFloatStateOf(0f)
        private set
    var dragging by mutableStateOf(false)
        private set
    private var job: Job? = null
    val visible: Boolean get() = targetOpen || progress > .0001f
    var open: Boolean
        get() = targetOpen
        set(value) { settle(value, 0f) }
    operator fun getValue(owner: Any?, property: KProperty<*>) = open
    operator fun setValue(owner: Any?, property: KProperty<*>, value: Boolean) { open=value }
    fun beginDrag() { job?.cancel(); dragging=true }
    fun dragBy(delta: Float, height: Float) {
        progress=(progress-delta/height.coerceAtLeast(1f)).coerceIn(0f,1f)
        targetOpen=progress>0f
    }
    fun release(velocity: Float, height: Float) {
        dragging=false
        val v=-velocity/height.coerceAtLeast(1f)
        settle(when { v>.7f -> true; v<-.7f -> false; else -> progress>=.35f },v)
    }
    private fun settle(value: Boolean, velocity: Float) {
        job?.cancel(); dragging=false; targetOpen=value
        if(!motion()) { progress=if(value)1f else 0f;return }
        job=scope.launch {
            Animatable(progress).animateTo(if(value)1f else 0f,AppMotion.drawerSpring(),initialVelocity=velocity) {
                progress=this.value.coerceIn(0f,1f)
            }
        }
    }
}
internal fun Modifier.toolDrawerDrag(state: WritingToolDrawerState, height: ()->Float): Modifier = composed {
    val currentHeight by rememberUpdatedState(height)
    pointerInput(state) {
        val tracker=VelocityTracker()
        detectVerticalDragGestures(onDragStart={tracker.resetTracking();state.beginDrag()},
            onVerticalDrag={change,amount->tracker.addPosition(change.uptimeMillis,change.position);change.consume();state.dragBy(amount,currentHeight())},
            onDragEnd={state.release(tracker.calculateVelocity().y,currentHeight())},
            onDragCancel={state.release(0f,currentHeight())})
    }
}
internal enum class EditorTopMode { FULL, THIN }
