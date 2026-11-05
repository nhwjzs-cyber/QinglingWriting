package com.qingling.writing.core.design

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Labels live above the filled field and never cut into an outline. */
@Composable
fun AppTextField(
    value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, readOnly: Boolean = false, singleLine: Boolean = false,
    label: (@Composable () -> Unit)? = null, placeholder: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null, isError: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null, trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    minLines: Int = 1, maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        label?.let { ProvideTextStyle(MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)) { it() } }
        TextField(value, onValueChange, modifier = Modifier.fillMaxWidth(), enabled = enabled,
            readOnly = readOnly, singleLine = singleLine, placeholder = placeholder,
            supportingText = supportingText, isError = isError, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, visualTransformation = visualTransformation,
            minLines = minLines, maxLines = maxLines, shape = RoundedCornerShape(14.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                errorContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent, errorIndicatorColor = Color.Transparent))
    }
}

@Composable
fun AppChoice(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
        .padding(3.dp).clip(RoundedCornerShape(9.dp))
        .background(if(selected) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else Color.Transparent)
        .selectable(selected, enabled = enabled, role = Role.Tab, onClick = onClick)
        .heightIn(min = 44.dp).padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        ProvideTextStyle(MaterialTheme.typography.labelMedium.copy(color =
            (if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                .copy(alpha = if(enabled) 1f else .38f))) { label() }
    }
}

@Composable
fun AppSegments(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier=Modifier) {
    val motion = LocalMotionEnabled.current
    BoxWithConstraints(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(4.dp)) {
        val count = options.size.coerceAtLeast(1)
        val cellWidth = maxWidth / count
        val left by androidx.compose.animation.core.animateDpAsState(cellWidth * selected.coerceIn(0, count - 1),
            if(motion) AppMotion.panelSpring() else androidx.compose.animation.core.snap(), label="segmentPosition")
        Box(Modifier.offset(x=left).width(cellWidth).height(36.dp).clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha=.12f)))
        Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
            options.forEachIndexed {index,label ->
                val ink by androidx.compose.animation.animateColorAsState(
                    if(index==selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    if(motion) androidx.compose.animation.core.tween(260) else androidx.compose.animation.core.snap(), label="segmentInk")
                Box(Modifier.weight(1f).height(36.dp).selectable(index==selected, role=Role.Tab, onClick={onSelect(index)}), contentAlignment=Alignment.Center) {
                    Text(label,fontSize=12.sp,color=ink,maxLines=1)
                }
            }
        }
    }
}

/** No Material stop marker: zero always draws only the neutral track. */
@Composable
fun QuietProgress(progress: Float, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f)
    val ink = MaterialTheme.colorScheme.primary
    Canvas(modifier.fillMaxWidth().height(3.dp)) {
        drawLine(track, Offset(0f,size.height/2), Offset(size.width,size.height/2),size.height,
            cap = androidx.compose.ui.graphics.StrokeCap.Round)
        if(progress.isFinite() && progress >= .01f) drawLine(ink, Offset(0f,size.height/2),
            Offset(size.width * progress.coerceIn(0f,1f), size.height/2),size.height,
            cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

@Composable
fun AppSecondaryButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit) {
    FilledTonalButton(onClick,modifier.heightIn(min=44.dp),enabled=enabled,shape=RoundedCornerShape(14.dp),
        colors=ButtonDefaults.filledTonalButtonColors(containerColor=MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor=MaterialTheme.colorScheme.onSurface),content=content)
}

@Composable
fun ConfirmAction(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit) = AppSecondaryButton(onClick,modifier,enabled,content)

/** Weak navigation/cancel actions use neutral ink; primary actions use filled buttons. */
@Composable
fun QuietTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    content: @Composable RowScope.() -> Unit) {
    TextButton(onClick,modifier,enabled=enabled,contentPadding=contentPadding,
        colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.onSurfaceVariant),content=content)
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier.height(44.dp),
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceContainer
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = Spacing.s12),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, null, Modifier.size(19.dp), tint = colors.onSurfaceVariant)
            Spacer(Modifier.width(Spacing.s8))
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicTextField(
                    value = value, onValueChange = onValueChange, singleLine = true, enabled = enabled,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }, modifier=Modifier.size(28.dp), enabled=enabled) {
                    Icon(Icons.Filled.Close, "清除搜索", Modifier.size(16.dp), tint = colors.onSurfaceVariant)
                }
            }
        }
    }
}

