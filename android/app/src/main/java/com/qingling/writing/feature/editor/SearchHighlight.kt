package com.qingling.writing.feature.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

internal fun searchHighlight(text: String, query: String) = buildAnnotatedString {
    append(text)
    TextSearch.find(text, query).ranges.forEach { match ->
        addStyle(SpanStyle(background = Color(0xFFFFD36A), color = Color(0xFF202020)), match.start, match.end)
    }
}
