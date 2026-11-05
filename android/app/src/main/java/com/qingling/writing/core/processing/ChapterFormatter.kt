package com.qingling.writing.core.processing

import com.qingling.writing.core.model.FormatOptions
import com.qingling.writing.core.model.ParagraphSpacing

/** Each existing newline remains a paragraph boundary; never merge lines or replace punctuation. */
object ChapterFormatter {
    fun format(source: String, options: FormatOptions): String {
        // An entirely disabled rule set is byte-for-byte identity, including CRLF.
        if (!options.indentTwoCharacters && !options.trimLineEnds && options.spacing == ParagraphSpacing.KEEP) return source
        val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n').map { original ->
            var line = if (options.trimLineEnds) original.trimEnd(' ', '\t', '\u3000') else original
            if (options.indentTwoCharacters && line.any { !it.isWhitespace() }) {
                line = "\u3000\u3000" + line.trimStart(' ', '\t', '\u3000')
            }
            line
        }
        return when (options.spacing) {
            ParagraphSpacing.KEEP -> lines.joinToString("\n")
            ParagraphSpacing.COMPACT -> lines.filter { it.isNotBlank() }.joinToString("\n")
            ParagraphSpacing.ONE_BLANK_LINE -> lines.filter { it.isNotBlank() }.joinToString("\n\n")
        }
    }
}
