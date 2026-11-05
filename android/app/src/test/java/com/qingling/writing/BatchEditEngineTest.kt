package com.qingling.writing

import com.qingling.writing.core.model.*
import com.qingling.writing.core.processing.BatchEditEngine
import org.junit.Assert.*
import org.junit.Test

class BatchEditEngineTest {
    private fun source(vararg bodies: String) = BatchSource(BatchScope("book"), "书", bodies.mapIndexed { i, text ->
        BatchDocument("c$i", "v", "卷", 0, "章$i", i.toLong(), 1, text)
    })
    @Test fun literalReplacementCountsNonOverlappingMatchesAndSkipsUnchangedChapters() {
        val p = BatchEditEngine.prepare(source("%_. %_.", "未命中"), BatchOperation.Replace("%_.", "$1"))
        assertEquals(1, p.changes.size); assertEquals(2, p.changes.single().hits)
        assertEquals("$1 $1", p.changes.single().after)
    }
    @Test fun unicodeCaseAndDeletionAreLiteral() {
        val p = BatchEditEngine.prepare(source("甲😀甲😀", "Alpha alpha"), BatchOperation.Replace("😀", ""))
        assertEquals("甲甲", p.changes.single().after)
        assertEquals(2, p.changes.single().hits)
        val case = BatchEditEngine.prepare(source("Alpha alpha"), BatchOperation.Replace("alpha", "beta"))
        assertEquals("Alpha beta", case.changes.single().after)
    }
    @Test fun replacementsDoNotOverlapOrReapplyToInsertedText() {
        val p = BatchEditEngine.prepare(source("aaaaa"), BatchOperation.Replace("aa", "aaa"))
        assertEquals(2, p.changes.single().hits); assertEquals("aaaaaaa", p.changes.single().after)
    }
    @Test fun formattingSharesSingleChapterRulesAndNeverMutatesSource() {
        val s = source(" 第一段  \r\n\r\n第二段\t")
        val options = FormatOptions(true, true, ParagraphSpacing.ONE_BLANK_LINE)
        val p = BatchEditEngine.prepare(s, BatchOperation.Format(options))
        assertEquals(com.qingling.writing.feature.editor.ChapterFormatter.format(s.documents.single().body, options), p.changes.single().after)
        assertEquals(" 第一段  \r\n\r\n第二段\t", s.documents.single().body)
    }
    @Test fun noOpProducesNoChangesAndEmptyFindIsRejected() {
        assertTrue(BatchEditEngine.prepare(source("abc"), BatchOperation.Replace("a", "a")).changes.isEmpty())
        assertTrue(BatchEditEngine.prepare(source("a\r\nb"), BatchOperation.Format(FormatOptions(false, false))).changes.isEmpty())
        assertThrows(IllegalArgumentException::class.java) { BatchEditEngine.prepare(source("abc"), BatchOperation.Replace("", "x")) }
    }
    @Test fun chapterAndParagraphLimitsRejectBeforeFormatting() {
        assertThrows(IllegalArgumentException::class.java) { BatchEditEngine.prepare(source(*Array(1001) { "a" }), BatchOperation.Format(FormatOptions())) }
        assertThrows(IllegalArgumentException::class.java) { BatchEditEngine.prepare(source("a\n".repeat(50001)), BatchOperation.Format(FormatOptions())) }
    }
    @Test fun sourceAndExpandedCharacterLimitAreBounded() {
        assertThrows(IllegalArgumentException::class.java) { BatchEditEngine.prepare(source("a".repeat(BatchEditEngine.MAX_BYTES + 1)), BatchOperation.Replace("x", "y")) }
        assertThrows(IllegalArgumentException::class.java) { BatchEditEngine.prepare(source("a".repeat(10000)), BatchOperation.Replace("a", "b".repeat(1000))) }
    }
    @Test fun expandedUtf8ByteLimitIsSeparateFromCharacterLimit() {
        assertThrows(IllegalArgumentException::class.java) { BatchEditEngine.prepare(source("a".repeat(100000)), BatchOperation.Replace("a", "界".repeat(30))) }
    }
}
