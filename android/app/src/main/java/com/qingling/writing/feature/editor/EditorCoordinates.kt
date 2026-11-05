package com.qingling.writing.feature.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingling.writing.domain.validation.WordCounter

/** UTF-16 selections are normalized at surrogate boundaries before applying the existing word rule. */
internal data class CursorCoordinates(val position: Long, val total: Long, val selected: Long, val selectionStart: Long, val hasSelection: Boolean) {
    companion object {
        fun from(index: WordCounter.Index, start: Int, end: Int): CursorCoordinates {
            val a = index.before(minOf(start, end)); val b = index.before(maxOf(start, end))
            return CursorCoordinates(index.before(end), index.total, b - a, a, start != end)
        }
    }
    fun label(mode: Int, today: Long): String = when (mode) {
        1 -> "本章进度 " + (if (total == 0L) 0 else (position * 100 / total).toInt()) + "%"
        2 -> "今日净增 " + today + " 字"
        else -> if (hasSelection) "已选 " + selected + " 字 · 起于第 " + (selectionStart + 1) + " 字"
            else "第 " + position + " 字 · 全章 " + total
    }
}

@Composable
internal fun EditorCoordinates(session: EditorSession, mode: Int, today: Long, onCycle: () -> Unit, modifier: Modifier = Modifier) {
    val state = session.editorState
    val text = state.text.toString()
    val index = remember(text) { WordCounter.index(text) }
    val coords = CursorCoordinates.from(index, state.selection.start, state.selection.end)
    val label = coords.label(mode, today)
    Text(label, modifier.clickable(onClickLabel = "切换坐标、进度或今日字数", onClick = onCycle)
        .semantics { contentDescription = "光标坐标：" + label }.padding(horizontal = 6.dp, vertical = 2.dp),
        fontSize = 10.sp, lineHeight = 13.sp, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
