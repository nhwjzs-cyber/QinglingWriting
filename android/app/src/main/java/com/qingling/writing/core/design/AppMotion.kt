package com.qingling.writing.core.design

import android.animation.ValueAnimator
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

object AppMotion {
    fun <T> panelSpring(): androidx.compose.animation.core.SpringSpec<T> = androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 500f)
    fun <T> drawerSpring(): androidx.compose.animation.core.SpringSpec<T> = androidx.compose.animation.core.spring(dampingRatio = .94f, stiffness = 500f)
    const val FAST = 120
    const val BASE = 180
    const val PAGE = 320
    const val SHEET = 300
    const val SHEET_EXIT = 280
    val EASE = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    val SHEET_EASE = CubicBezierEasing(0.16f, 0.8f, 0.24f, 1f)
    val PAGE_EASE = CubicBezierEasing(0.2f, 0.75f, 0.25f, 1f)
}
val LocalMotionEnabled = staticCompositionLocalOf { true }

/** Refresh once on resume, never query accessibility services on every frame. */
@Composable
fun MotionPolicy(reduced: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    fun systemEnabled(): Boolean = ValueAnimator.areAnimatorsEnabled() &&
        !(context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager).isTouchExplorationEnabled
    var enabled by remember { mutableStateOf(systemEnabled()) }
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) enabled = systemEnabled()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    CompositionLocalProvider(LocalMotionEnabled provides (!reduced && enabled), content = content)
}

@Composable
fun ChromeAction(icon: ImageVector, label: String, onClick: () -> Unit, active: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed && LocalMotionEnabled.current) 0.96f else 1f,
        animationSpec = if (LocalMotionEnabled.current) tween(AppMotion.FAST) else snap(), label = "iconPress")
    IconButton(onClick = onClick, interactionSource = interaction,
        modifier = Modifier.padding(horizontal = 3.dp).size(44.dp).graphicsLayer { alpha = scale.value },
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)) {
        Icon(icon, contentDescription = label)
    }
}
