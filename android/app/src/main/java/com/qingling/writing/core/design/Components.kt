package com.qingling.writing.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.qingling.writing.R

/** docs/02 §2:列表行最小高 56dp。 */
@Composable
fun ListRow(
    modifier: Modifier = Modifier,
    title: String,
    trailing: String? = null,
    onClick: (() -> Unit)? = null,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val base=modifier.fillMaxWidth().heightIn(min=Dimens.listRowMinHeight)
    Row((if(onClick!=null) base.clickable(enabled=enabled,onClick=onClick) else base)
        .padding(horizontal=Spacing.s24,vertical=Spacing.s12),verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(Spacing.s12)) {
        if(icon!=null) Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest,androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center) {
            Icon(icon,null,Modifier.size(21.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(title,style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onSurface.copy(alpha=if(enabled)1f else .38f))
            if(!subtitle.isNullOrBlank()) Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        if(trailing!=null) Text(trailing,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 返回按钮:48dp 触控目标;图标带 contentDescription(ask 红线)。 */
@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(R.drawable.ic_back),
            contentDescription = stringResource(R.string.action_back),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 居中的空态/占位提示文本。 */
@Composable
fun CenterMessage(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.s24),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().padding(horizontal = Spacing.s32, vertical = Spacing.s40),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (icon != null) {
            Box(
                Modifier.size(54.dp).border(1.2.dp, colors.primary.copy(alpha = .45f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, Modifier.size(24.dp), tint = colors.primary) }
            Spacer(Modifier.height(Spacing.s16))
        }
        Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(Spacing.s4))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Spacing.s8))
            TextButton(onClick = onAction) { Text(actionLabel, color = colors.primary) }
        }
    }
}

