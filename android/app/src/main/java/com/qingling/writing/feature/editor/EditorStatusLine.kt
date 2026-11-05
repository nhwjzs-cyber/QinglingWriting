package com.qingling.writing.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingling.writing.core.design.BackButton
import com.qingling.writing.core.model.LocalSaveState

@Composable
internal fun EditorSaveIndicator(viewModel: EditorViewModel, compact: Boolean = false) {
    val state by viewModel.saveState.collectAsStateWithLifecycle()
    val color = when (state) { is LocalSaveState.Failed -> MaterialTheme.colorScheme.error
        LocalSaveState.Idle, is LocalSaveState.Saved -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.onSurfaceVariant }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(5.dp).background(color, CircleShape))
        Text(when (state) { is LocalSaveState.Saved -> "已保存"; is LocalSaveState.Saving -> "保存中…"
            is LocalSaveState.Failed -> "保存失败"; else -> "已保存" }, fontSize = if (compact) 10.sp else 11.sp,
            color = color, maxLines = 1)
    }
}

@Composable
internal fun EditorThinStatusLine(viewModel: EditorViewModel, ready: EditorUiState.Ready, onBook: () -> Unit) {
    val words by viewModel.wordCount.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides androidx.compose.ui.unit.Dp.Unspecified) {
        Row(Modifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(ready.bookTitle + " · " + ready.chapterTitle, Modifier.weight(1f).clickable(onClickLabel="打开书库",onClick=onBook).padding(start=22.dp,end=8.dp),
                fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            EditorSaveIndicator(viewModel, true)
            Text(words.toString() + " 字", Modifier.padding(start = 10.dp, end = 12.dp), fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}
