package com.qingling.writing

import com.qingling.writing.domain.validation.TitleError
import com.qingling.writing.domain.validation.TitleValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 标题校验 JVM 单测(docs/05 §6 必测项;docs/01 §3:书名 ≤80 码点、章节标题 ≤120 码点,
 * trim 后非空、不含 CR/LF)。码点口径:emoji 等代理对按 1 个码点计。
 */
class TitleValidatorTest {

    @Test
    fun `book title trims surrounding whitespace`() {
        val result = TitleValidator.checkBookTitle("  山间来信  ")
        assertNull(result.error)
        assertEquals("山间来信", result.value)
    }

    @Test
    fun `blank title is rejected`() {
        assertEquals(TitleError.EMPTY, TitleValidator.checkBookTitle("   ").error)
        assertEquals(TitleError.EMPTY, TitleValidator.checkBookTitle("").error)
        assertEquals(TitleError.EMPTY, TitleValidator.checkBookTitle("\t\n").error)
    }

    @Test
    fun `line break inside title is rejected`() {
        assertEquals(TitleError.CONTAINS_LINE_BREAK, TitleValidator.checkBookTitle("第一行\n第二行").error)
        assertEquals(TitleError.CONTAINS_LINE_BREAK, TitleValidator.checkBookTitle("第一行\r第二行").error)
    }

    @Test
    fun `book title at 80 code points passes`() {
        val title = "书".repeat(80)
        assertNull(TitleValidator.checkBookTitle(title).error)
    }

    @Test
    fun `book title over 80 code points is rejected`() {
        val title = "书".repeat(81)
        assertEquals(TitleError.TOO_LONG, TitleValidator.checkBookTitle(title).error)
    }

    @Test
    fun `surrogate pairs count as one code point`() {
        // “🚀✍️” 的 UTF-16 长度大于码点数:80 个 emoji = 80 码点,应通过(书名)。
        val emoji = "\uD83D\uDE80" // 🚀 = 2 code units, 1 code point
        assertNull(TitleValidator.checkBookTitle(emoji.repeat(80)).error)
        assertEquals(TitleError.TOO_LONG, TitleValidator.checkBookTitle(emoji.repeat(81)).error)
    }

    @Test
    fun `section title at 120 code points passes and over is rejected`() {
        assertNull(TitleValidator.checkSectionTitle("章".repeat(120)).error)
        assertEquals(TitleError.TOO_LONG, TitleValidator.checkSectionTitle("章".repeat(121)).error)
    }
}
