package com.qingling.writing.feature.editor

import androidx.compose.animation.core.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

/** Animate only the draw layer. IME, selection, hit testing and scroll remain owned by BasicTextField. */
@Composable
internal fun Modifier.writingCursor(state: TextFieldState, scroll: ScrollState,
    layout: () -> TextLayoutResult?, color: Color, fontSize: Int, motion: Boolean, highlight: Boolean = false): Modifier {
    var focused by remember(state) { mutableStateOf(false) }
    val opacity: State<Float> = if ((focused || state.text.isEmpty()) && motion) {
        rememberInfiniteTransition(label = "writingCursor").animateFloat(.12f, 1f,
            infiniteRepeatable(tween(550, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "cursorOpacity")
    } else rememberUpdatedState(1f)
    return onFocusChanged { focused = it.isFocused }.drawWithContent {
        if (highlight && focused) layout()?.let { result ->
            val line = result.getLineForOffset(state.selection.end.coerceIn(0, result.layoutInput.text.length))
            clipRect { drawRect(color.copy(alpha = .05f), Offset(0f,result.getLineTop(line) - scroll.value),
                Size(size.width,result.getLineBottom(line) - result.getLineTop(line))) }
        }
        drawContent()
        if (!motion || (!focused && state.text.isNotEmpty()) || !state.selection.collapsed) return@drawWithContent
        val text = layout() ?: return@drawWithContent
        val rect = text.getCursorRect(state.selection.end.coerceIn(0, text.layoutInput.text.length))
        val height = minOf(rect.height, fontSize.sp.toPx() * 1.35f)
        val width = 2.5.dp.toPx()
        val x = rect.left.coerceIn(width / 2, (size.width - width / 2).coerceAtLeast(width / 2)) - width / 2
        val y = rect.top - scroll.value + (rect.height - height) / 2
        if (y + height < 0 || y > size.height) return@drawWithContent
        clipRect {
            for (spread in 3 downTo 1) {
                val halo = spread.dp.toPx()
                drawRoundRect(color.copy(alpha = .025f * opacity.value), Offset(x - halo, y - halo),
                    Size(width + halo * 2, height + halo * 2), CornerRadius(width + halo))
            }
            drawRoundRect(color.copy(alpha = opacity.value), Offset(x,y), Size(width,height), CornerRadius(width / 2))
        }
    }
}
