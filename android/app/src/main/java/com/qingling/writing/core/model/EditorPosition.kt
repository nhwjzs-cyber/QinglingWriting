package com.qingling.writing.core.model

/**
 * 编辑器光标/滚动位置恢复数据(docs/03 §4)。
 * selection 为 UTF-16 偏移,恢复时按文本长度 clamp;与字数口径分开。
 */
data class EditorPosition(
    val chapterId: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val scrollOffset: Int,
)
