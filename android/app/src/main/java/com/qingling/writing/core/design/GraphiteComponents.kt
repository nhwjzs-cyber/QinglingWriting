package com.qingling.writing.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun relativeEditTime(value: String): String = try {
    val minutes = java.time.Duration.between(java.time.Instant.parse(value), java.time.Instant.now()).toMinutes().coerceAtLeast(0)
    when { minutes < 1 -> "刚刚编辑"; minutes < 60 -> "${minutes} 分钟前"; minutes < 1440 -> "${minutes / 60} 小时前"
        minutes < 10080 -> "${minutes / 1440} 天前"; else -> formatTimestamp(value) }
} catch (_: java.time.DateTimeException) { "" }

/** Stable local seed, no network asset or per-frame bitmap work. */
@Composable
fun SeedBookCover(title: String, seed: String, modifier: Modifier = Modifier, style: com.qingling.writing.core.model.CoverStyle? = null) {
    val hash = remember(title, seed) { (title + seed).hashCode().toLong().and(0x7fffffff) }
    val selectedStyle=style ?: com.qingling.writing.core.model.LocalCoverStyles.current[seed]
    val colors = remember(hash,selectedStyle) {
        val pairs = listOf(0xFF546E5CL to 0xFF35473BL, 0xFF617282L to 0xFF3E4A55L,
            0xFF806854L to 0xFF57453AL, 0xFF6E6E5AL to 0xFF4A4A3CL, 0xFF6E625EL to 0xFF4A423EL)
        pairs[selectedStyle?.color ?: (hash % pairs.size).toInt()].let { listOf(Color(it.first), Color(it.second)) }
    }
    Box(modifier.clip(RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp, topEnd = 7.dp, bottomEnd = 7.dp))
        .background(Brush.verticalGradient(colors)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val ink = Color.White.copy(alpha = .14f)
            if(size.width > 50.dp.toPx()) when (selectedStyle?.motif ?: (hash % 3).toInt()) {
                0 -> { drawLine(ink, Offset(size.width*.78f,size.height*.94f), Offset(size.width*.85f,size.height*.64f), 1.dp.toPx())
                    for (i in 0..2) { val y = size.height*(.72f + i*.07f)
                        drawLine(ink, Offset(size.width*.81f,y), Offset(size.width*.94f,y-size.height*.04f), 1.dp.toPx()) } }
                1 -> drawCircle(ink, size.width*.1f, Offset(size.width*.84f,size.height*.78f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                else -> for (i in 0..2) drawLine(ink, Offset(size.width*.75f,size.height*(.75f+i*.05f)), Offset(size.width*.94f,size.height*(.75f+i*.05f)), 1.dp.toPx())
            }
            drawRect(Color.Black.copy(alpha = .22f), size = androidx.compose.ui.geometry.Size(5.dp.toPx(), size.height))
        }
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val large = maxHeight >= 100.dp
        val vertical = title.removePrefix("演示·").codePoints().limit(if (large) 6L else 4L).toArray()
            .joinToString("\n") { String(Character.toChars(it)) }
        Text(vertical,
            color = Color(0xFFF2F1E9), fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold,
            fontSize = if (large) 13.sp else if(maxHeight < 56.dp) 9.sp else 11.sp,
            lineHeight = if (large) 16.sp else if(maxHeight < 56.dp) 11.sp else 13.sp, maxLines = 6, overflow = TextOverflow.Clip)
        }
    }
}

@Composable
fun PanelGroup(title: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(Modifier.size(3.dp,14.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
        Text(title, style = MaterialTheme.typography.labelMedium, fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ToolRow(icon: ImageVector, title: String, subtitle: String, value: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(21.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if(subtitle.isNotBlank()) Text(subtitle, fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!value.isNullOrEmpty()) Text(value, fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

data class StatData(val label: String, val value: String, val unit: String = "", val isText: Boolean = false)

@Composable
fun StatCell(data: StatData, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(data.label, style = MaterialTheme.typography.labelSmall.copy(fontSize=11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(data.value, style = if(data.isText) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.headlineSmall.copy(fontSize=24.sp,fontWeight=FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface, modifier=Modifier.weight(1f,fill=false), maxLines = if(data.isText) 2 else 1, overflow=TextOverflow.Ellipsis)
            if (data.unit.isNotEmpty()) {
                Spacer(Modifier.width(3.dp))
                Text(data.unit, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

/** 一行统计（建议 ≤3 格），格间发丝竖线。 */
@Composable
fun StatRow(stats: List<StatData>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(Spacing.s16)) {
        stats.chunked(3).forEach { cells ->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                cells.forEachIndexed { index,stat ->
                    if(index>0) Box(Modifier.width(1.dp).height(30.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha=.6f)))
                    StatCell(stat,Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun <T> FilterChips(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s8)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Surface(
                modifier = Modifier.selectable(selected=isSelected,role=androidx.compose.ui.semantics.Role.Tab,onClick={onSelect(option)}),
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) colors.primary.copy(alpha = .12f) else colors.surfaceContainer.copy(alpha = 0f),
                border = BorderStroke(1.dp, if (isSelected) colors.primary else colors.outlineVariant)
            ) {
                Box(Modifier.height(32.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    Text(label(option), style = MaterialTheme.typography.labelLarge.copy(fontSize=12.sp),
                        color = if (isSelected) colors.primary else colors.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

