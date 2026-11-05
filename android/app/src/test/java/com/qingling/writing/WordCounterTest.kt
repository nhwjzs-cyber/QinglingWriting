package com.qingling.writing

import com.qingling.writing.domain.validation.WordCounter
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 字数口径 JVM 单测(docs/01 §7 / docs/05 §6):按 Unicode 码点遍历,不计 Unicode 空白,
 * 包含标点与 emoji 组成码点。
 */
class WordCounterTest {

    @Test
    fun `empty text counts zero`() {
        assertEquals(0L, WordCounter.count(""))
    }

    @Test
    fun `whitespace only counts zero`() {
        assertEquals(0L, WordCounter.count(" \t\n\r\u3000")) // 含全角空格 U+3000
    }

    @Test
    fun `cjk counts per code point`() {
        assertEquals(4L, WordCounter.count("山间来信"))
    }

    @Test
    fun `whitespace between words is not counted`() {
        assertEquals(2L, WordCounter.count("a b"))
        assertEquals(3L, WordCounter.count("山 间 来")) // 中文间的空格同样不计
        assertEquals(2L, WordCounter.count("a\r\nb"))
    }

    @Test
    fun `emoji counts as one code point`() {
        assertEquals(1L, WordCounter.count("\uD83D\uDE80")) // 🚀
        assertEquals(3L, WordCounter.count("写\uD83D\uDE80作"))
    }

    @Test
    fun `punctuation is counted`() {
        assertEquals(3L, WordCounter.count("山,。"))
    }
}
