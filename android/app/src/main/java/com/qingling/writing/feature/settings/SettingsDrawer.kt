package com.qingling.writing.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.qingling.writing.core.design.SidePanelSide
import com.qingling.writing.core.design.SidePanelState
import com.qingling.writing.core.design.panelGestures
import androidx.compose.ui.unit.dp

/** Overlay on the current home/works page. Closed panels leave no invisible touch layer. */
@Composable
internal fun SettingsDrawer(
    state: SidePanelState,
    onOpenAppearance: () -> Unit,
    onOpenBackup: () -> Unit,
) {
    if (state.side == null) return
    BackHandler { state.close() }
    val motion=com.qingling.writing.core.design.LocalMotionEnabled.current
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = minOf((maxWidth - 48.dp).coerceAtLeast(0.dp), maxWidth * 0.82f, 420.dp)
        val widthPx = with(density) { width.toPx() }
        Box(Modifier.fillMaxSize().panelGestures(state, widthPx, widthPx, allowBodyDrag = false)) {
            Box(Modifier.fillMaxSize()
                .drawBehind { drawRect(Color.Black.copy(alpha = 0.42f * state.progress)) }
                .pointerInput(state) { detectTapGestures { state.close() } })
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp, shadowElevation = 18.dp,
                shape = RectangleShape,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(width)
                    .graphicsLayer { translationX = if(motion)widthPx * (1f - state.progress)else 0f;alpha=if(motion)1f else state.progress }) {
                SettingsScreen(onBack = { state.close() }, onOpenAppearance = onOpenAppearance,
                    onOpenBackup = onOpenBackup, asPanel = true)
            }
        }
    }
}
