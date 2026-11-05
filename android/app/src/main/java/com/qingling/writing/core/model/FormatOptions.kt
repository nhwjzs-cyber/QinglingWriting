package com.qingling.writing.core.model

enum class ParagraphSpacing { KEEP, COMPACT, ONE_BLANK_LINE }
data class FormatOptions(
    val indentTwoCharacters: Boolean = true,
    val trimLineEnds: Boolean = true,
    val spacing: ParagraphSpacing = ParagraphSpacing.KEEP,
)
