package com.qingling.writing.core.design

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp

/** Lock to horizontal drag only after system touch slop; keep vertical scroll and text selection. */
@Composable
internal fun Modifier.panelGestures(
    panels: SidePanelState, leftWidthPx: Float, rightWidthPx: Float, allowBodyDrag: Boolean,
): Modifier {
    val allowed by rememberUpdatedState(allowBodyDrag)
    val left by rememberUpdatedState(leftWidthPx)
    val right by rememberUpdatedState(rightWidthPx)
    return pointerInput(panels) {
        val tracker = VelocityTracker()
        val systemEdge = 16.dp.toPx()
        val gutter = Spacing.editorGutter.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val x = down.position.x
            // Observe the complete editor before its text field/chrome. Only claim an
            // inward horizontal drag, so taps, selection and vertical scroll still reach
            // the original child instead of hitting an invisible overlay.
            if (panels.side == null && (x < 48.dp.toPx() || x > size.width - 48.dp.toPx())) {
                val fromLeft = x < size.width / 2f
                var dragging = false
                tracker.resetTracking()
                tracker.addPosition(down.uptimeMillis, down.position)
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (event.changes.count { it.pressed } > 1) break
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (!change.pressed) break
                        val dx = change.position.x - down.position.x
                        val dy = change.position.y - down.position.y
                        if (!dragging) {
                            if (kotlin.math.abs(dy) > viewConfiguration.touchSlop) break
                            if (kotlin.math.abs(dx) <= viewConfiguration.touchSlop) continue
                            if ((fromLeft && dx < 0f) || (!fromLeft && dx > 0f)) break
                            panels.beginDrag()
                            dragging = true
                            panels.dragBy(dx, left, right)
                        } else {
                            panels.dragBy(change.position.x - change.previousPosition.x, left, right)
                        }
                        change.consume()
                    }
                } finally {
                    if (dragging) panels.settle(tracker.calculateVelocity().x / density)
                }
                return@awaitEachGesture
            }
            val inGutter = x < gutter || x > size.width - gutter
            val inHandle = x < 48.dp.toPx() || x > size.width - 48.dp.toPx()
            val permitted = (inHandle && allowed) || panels.side != null ||
                (x > systemEdge && x < size.width - systemEdge && (allowed || inGutter))
            if (permitted) {
                var firstDelta = 0f
                val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                    firstDelta = over
                    change.consume()
                }
                if (start != null) {
                    tracker.resetTracking()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    tracker.addPosition(start.uptimeMillis, start.position)
                    panels.beginDrag()
                    panels.dragBy(firstDelta, left, right)
                    val completed = horizontalDrag(start.id) { change ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        panels.dragBy(change.position.x - change.previousPosition.x, left, right)
                        change.consume()
                    }
                    panels.settle(if (completed) tracker.calculateVelocity().x / density else 0f)
                }
            }
        }
    }
}

