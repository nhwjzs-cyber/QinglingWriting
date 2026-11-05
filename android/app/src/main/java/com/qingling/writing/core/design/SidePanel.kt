package com.qingling.writing.core.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import com.qingling.writing.core.design.AppMotion

/** One owner for both panels; a new command or drag interrupts the previous transition. */
internal class SidePanelState(private val scope: CoroutineScope, private val enabled: () -> Boolean = { true }) {
    var side by mutableStateOf<SidePanelSide?>(null)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    private var animation: Job? = null
    private var targetSide: SidePanelSide? = null

    fun open(target: SidePanelSide) {
        stop()
        targetSide = target
        if (side != null && side != target && progress > 0f) {
            animation = scope.launch {
                animateProgress(0f, CLOSE_MS)
                side = target
                animateProgress(1f, OPEN_MS)
            }
        } else {
            side = target
            animateTo(1f, OPEN_MS)
        }
    }

    fun close() { targetSide = null; animateTo(0f, CLOSE_MS) }
    suspend fun dismiss(): Boolean { close(); animation?.join(); return side == null }
    fun finishMotion() {
        stop()
        side = targetSide
        progress = if (side == null) 0f else 1f
    }
    fun beginDrag() = stop()

    fun dragBy(deltaPx: Float, leftWidthPx: Float, rightWidthPx: Float) {
        stop()
        if (side == null) side = if (deltaPx >= 0) SidePanelSide.LEFT else SidePanelSide.RIGHT
        val sign = if (side == SidePanelSide.LEFT) 1f else -1f
        val width = if (side == SidePanelSide.LEFT) leftWidthPx else rightWidthPx
        progress = (progress + sign * deltaPx / width.coerceAtLeast(1f)).coerceIn(0f, 1f)
    }

    fun settle(velocityDpS: Float) {
        val openingVelocity = velocityDpS * if (side == SidePanelSide.LEFT) 1f else -1f
        val target = when {
            openingVelocity >= 800f -> 1f
            openingVelocity <= -800f -> 0f
            progress >= 0.35f -> 1f
            else -> 0f
        }
        targetSide = if (target == 0f) null else side
        animateTo(target, (140 + 100 * abs(target - progress)).toInt())
    }

    private fun stop() {
        animation?.cancel()
        animation = null
    }

    private fun animateTo(target: Float, duration: Int) {
        stop()
        if (side == null) return
        if (progress == target) {
            if (target == 0f) side = null
            return
        }
        animation = scope.launch {
            animateProgress(target, duration)
            // Cancelled closes cannot reach this line and hide a newer panel.
            if (target == 0f) side = null
        }
    }

    private suspend fun animateProgress(target: Float, duration: Int) {
        Animatable(progress).animateTo(target, if(enabled()) AppMotion.panelSpring() else tween(120)) {
            progress = value
        }
    }

    companion object {
        const val OPEN_MS = AppMotion.SHEET
        const val CLOSE_MS = AppMotion.SHEET_EXIT
    }
}

internal enum class SidePanelSide { LEFT, RIGHT }
