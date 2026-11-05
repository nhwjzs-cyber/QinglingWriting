package com.qingling.writing.feature.editor

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.ui.text.TextRange

/** Only a newly inserted paragraph break is transformed; regular spaces and IME typing are untouched. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
internal object ParagraphIndentation {
    const val PREFIX="　　"
    val onInput=InputTransformation {
        if(length==0 && originalText.isNotEmpty()) {
            append(PREFIX)
            selection=TextRange(PREFIX.length)
        } else {
            // Snapshot before editing: TextFieldBuffer.changes is a live change list.
            val starts=(0 until changes.changeCount).mapNotNull { index ->
                val range=changes.getRange(index)
                val added=asCharSequence().subSequence(range.min,range.max).toString()
                if(added=="\n" || added=="\r\n") range.max else null
            }.distinct().sortedDescending()
            starts.forEach { start ->
                val text=asCharSequence()
                var existing=0
                while(start+existing<length && text[start+existing]=='　' && existing<2) existing++
                val customSpace=start<length && (text[start]==' ' || text[start]=='\t')
                val extra=if(customSpace) "" else PREFIX.drop(existing)
                val cursor=selection
                if(extra.isNotEmpty()) replace(start,start,extra)
                if(cursor.collapsed && cursor.start==start && !customSpace) selection=TextRange(start+2)
            }
        }
    }
}
