package com.qingling.writing.feature.editor

import com.qingling.writing.core.model.FormatOptions

object ChapterFormatter {
    fun format(source: String, options: FormatOptions) = com.qingling.writing.core.processing.ChapterFormatter.format(source, options)
}
