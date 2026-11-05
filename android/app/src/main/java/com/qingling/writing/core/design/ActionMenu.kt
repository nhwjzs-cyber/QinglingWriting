package com.qingling.writing.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*

/** Window coordinates of the owning panel; menus can never escape it. */
val LocalMenuBounds = staticCompositionLocalOf<Rect?> { null }
val MenuCopyIcon: ImageVector by lazy {
    ImageVector.Builder("Copy",24.dp,24.dp,24f,24f).apply {
        path(fill=null,stroke=SolidColor(Color.Black),strokeLineWidth=1.8f,
            strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round) {
            moveTo(9f,4f);lineTo(20f,4f);lineTo(20f,16f);lineTo(9f,16f);close()
            moveTo(6f,8f);lineTo(4f,8f);lineTo(4f,21f);lineTo(15f,21f);lineTo(15f,19f)
        }
    }.build()
}

@Composable
fun MenuBoundary(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var bounds by remember { mutableStateOf<Rect?>(null) }
    Box(modifier.onGloballyPositioned { bounds = it.boundsInWindow() }) {
        CompositionLocalProvider(LocalMenuBounds provides bounds) { content() }
    }
}

@Composable
fun AppMenu(expanded: Boolean, onDismissRequest: () -> Unit,
    context: String? = null, content: @Composable ColumnScope.() -> Unit) {
    var anchor by remember { mutableStateOf(Rect.Zero) }
    val boundary = LocalMenuBounds.current
    Box(Modifier.size(1.dp).onGloballyPositioned { anchor = it.boundsInWindow() })
    if (!expanded) return
    val density = LocalDensity.current
    val origin = remember { object : PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
            layoutDirection: LayoutDirection, popupContentSize: IntSize) = IntOffset.Zero
    } }
    Popup(popupPositionProvider = origin, onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true, usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .42f))
            .clickable(role = Role.Button, onClick = onDismissRequest)) {
            val frame = boundary ?: Rect(0f,0f,constraints.maxWidth.toFloat(),constraints.maxHeight.toFloat())
            val inset = with(density) { 12.dp.toPx() }
            val safeLeft = maxOf(inset,frame.left + inset)
            val safeRight = minOf(constraints.maxWidth - inset,frame.right - inset)
            val widthPx = minOf(with(density) { 264.dp.toPx() },(safeRight-safeLeft).coerceAtLeast(1f))
            var height by remember { mutableIntStateOf(0) }
            val x = if(boundary != null) maxOf(safeLeft,safeRight-widthPx)
                else (anchor.right-widthPx).coerceIn(safeLeft, maxOf(safeLeft,safeRight-widthPx))
            val top = maxOf(inset, frame.top + inset)
            val bottom = minOf(constraints.maxHeight-inset,frame.bottom-inset)
            val y = anchor.top.coerceIn(top,maxOf(top,bottom-height))
            Surface(Modifier.offset { IntOffset(x.toInt(),y.toInt()) }
                .width(with(density) { widthPx.toDp() })
                .heightIn(max = with(density) { (bottom-top).coerceAtLeast(1f).toDp() })
                .onSizeChanged { height = it.height }
                .semantics { contentDescription = "操作菜单" }
                .clickable {},
                shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shadowElevation = 18.dp) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                    context?.let { Text(it, Modifier.padding(horizontal=16.dp,vertical=10.dp),
                        style=MaterialTheme.typography.labelMedium, color=MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                    content()
                }
            }
        }
    }
}

@Composable
fun AppMenuItem(text: @Composable () -> Unit, onClick: () -> Unit,
    icon: ImageVector = Icons.Filled.MoreVert, danger: Boolean = false, enabled: Boolean = true) {
    val ink = (if(danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        .copy(alpha=if(enabled) 1f else .38f)
    Row(Modifier.fillMaxWidth().heightIn(min=44.dp).clickable(enabled=enabled,onClick=onClick)
        .padding(horizontal=16.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Icon(icon,null,Modifier.size(18.dp),tint=ink)
        CompositionLocalProvider(LocalContentColor provides ink) {
            ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text() }
        }
    }
}

@Composable fun MenuRule() = HorizontalDivider(Modifier.padding(horizontal=16.dp,vertical=6.dp),
    color=MaterialTheme.colorScheme.onSurface.copy(alpha=.05f))
