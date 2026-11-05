package com.qingling.writing

import com.qingling.writing.feature.editor.ChapterFormatter
import com.qingling.writing.core.model.*
import org.junit.Assert.*
import org.junit.Test

class ChapterFormatterTest {
    @Test fun disabledRulesPreserveAllBytes() {
        val text = " \t甲\r\n\r\n乙　 \r\n"
        assertEquals(text, ChapterFormatter.format(text, FormatOptions(false, false)))
    }
    @Test fun indentIsIdempotentAndPreservesInternalSpacesAndPunctuation() {
        val original = "  甲  乙🙂，\n\t第二段\n　　第三段"
        val result = ChapterFormatter.format(original, FormatOptions())
        assertEquals("　　甲  乙🙂，\n　　第二段\n　　第三段", result)
        assertEquals(result, ChapterFormatter.format(result, FormatOptions()))
    }
    @Test fun blankRulesDoNotMergeNonemptyParagraphs() {
        val source = "\n甲\n \n\n乙\n"
        assertEquals("甲\n乙", ChapterFormatter.format(source, FormatOptions(false, true, ParagraphSpacing.COMPACT)))
        assertEquals("甲\n\n乙", ChapterFormatter.format(source, FormatOptions(false, true, ParagraphSpacing.ONE_BLANK_LINE)))
    }
    @Test fun keepBlankLinesAndTrailingNewline() {
        assertEquals("　　甲\n\n　　乙\n", ChapterFormatter.format("甲 \r\n \r\n乙\r\n", FormatOptions()))
    }
    @Test fun emptyAndWhitespaceDoNotBecomeIndentedParagraphs() {
        assertEquals("", ChapterFormatter.format("", FormatOptions()))
        assertEquals("\n", ChapterFormatter.format(" \n\t　", FormatOptions()))
    }
    @Test fun rulesCanBeChosenIndependently() {
        assertEquals("　　甲  ", ChapterFormatter.format("  甲  ", FormatOptions(true, false)))
        assertEquals("  甲", ChapterFormatter.format("  甲  ", FormatOptions(false, true)))
    }
    @Test fun longChapterPreservesAllNonWhitespaceCodePoints() {
        val text = (1..5000).joinToString("\n\n") { "第${it}段，👩‍🚀正文！" }
        val result = ChapterFormatter.format(text, FormatOptions(spacing = ParagraphSpacing.ONE_BLANK_LINE))
        assertEquals(text.filterNot { it.isWhitespace() }, result.filterNot { it.isWhitespace() })
        assertEquals(result, ChapterFormatter.format(result, FormatOptions(spacing = ParagraphSpacing.ONE_BLANK_LINE)))
    }
    @Test fun appearanceRejectsInvalidHexAndClampsStoredValues() {
        assertNull(normalizeHex("#XYZABC"))
        assertEquals("ABCDEF", normalizeHex(" #aBcDef "))
        val a = EditorAppearance(fontSize = 900, lineHeightPercent = -1, gutter = 0,
            backgroundHex = "wrong", imageDimPercent = 500).normalized()
        assertEquals(32, a.fontSize); assertEquals(120, a.lineHeightPercent)
        assertEquals(12, a.gutter); assertEquals("121212", a.backgroundHex); assertEquals(90, a.imageDimPercent)
    }
}
