package com.qingling.writing.feature.editor
import androidx.compose.ui.text.TextRange
import org.junit.Assert.*
import org.junit.Test
class ParagraphSelectionTest {
    @Test fun paragraphSelectionKeepsSurrogatePairsAndBounds() {
        val text="第一段😀\n\n最后一段"
        assertEquals(TextRange(0,5),paragraphRange(text,3))
        assertEquals(TextRange(6,6),paragraphRange(text,6))
        assertEquals(TextRange(7,11),paragraphRange(text,100))
        assertEquals(TextRange(0,0),paragraphRange("",-1))
    }
}
