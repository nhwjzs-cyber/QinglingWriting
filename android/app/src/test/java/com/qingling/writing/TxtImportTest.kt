package com.qingling.writing

import com.qingling.writing.core.model.TxtImport
import org.junit.Assert.*
import org.junit.Test

class TxtImportTest {
    @Test fun longHeadingsAreNotLostWhenTitleIsTruncated() {
        val heading = "第" + "一".repeat(130) + "章 结尾"
        val chapter = TxtImport.split(heading + "\n正文", "书", true).single()
        assertEquals(120, chapter.title.codePointCount(0, chapter.title.length))
        assertTrue(chapter.body.startsWith(heading + "\n"))
    }
    @Test fun utf8BomAndLineEndings() {
        val decoded = TxtImport.decode("\uFEFF中文\r\n第二行\r第三行".toByteArray())
        assertEquals("UTF-8", decoded.encoding)
        assertEquals("中文\n第二行\n第三行", decoded.text)
    }
    @Test fun gbkFallbackAndManualOverride() {
        val bytes = "第一章 中文稿件".toByteArray(charset("GB18030"))
        assertEquals("第一章 中文稿件", TxtImport.decode(bytes).text)
        assertEquals("GB18030", TxtImport.decode(bytes).encoding)
        assertThrows(Exception::class.java) { TxtImport.decode(bytes, "UTF-8") }
    }
    @Test fun utf16Bom() {
        assertEquals("正文😀", TxtImport.decode("正文😀".toByteArray(charset("UTF-16"))).text)
    }
    @Test fun splitRetainsPreambleEmptyChapterAndLastBody() {
        val chapters = TxtImport.split("简介\n第一章 起点\n正文\n第二回\n第三节 结尾\n终点", "书", true)
        assertEquals(listOf("前言", "第一章 起点", "第二回", "第三节 结尾"), chapters.map { it.title })
        assertEquals("简介\n", chapters.first().body)
        assertEquals("", chapters[2].body)
        assertEquals("终点", chapters.last().body)
    }
    @Test fun disabledOrMissingHeadingsPreserveFullBody() {
        val text = "正文里提到第一章不应分开\n第十二章 标题\n正文"
        assertEquals(text, TxtImport.split(text, "文件名", false).single().body)
        assertEquals("普通正文", TxtImport.split("普通正文", "文件名", true).single().body)
    }
    @Test fun limitsAndBinaryFilesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { TxtImport.decode(ByteArray(TxtImport.MAX_BYTES + 1)) }
        assertThrows(IllegalArgumentException::class.java) { TxtImport.decode(byteArrayOf(0, 1)) }
        assertThrows(IllegalArgumentException::class.java) { TxtImport.decode(" \n".toByteArray()) }
        assertThrows(IllegalArgumentException::class.java) {
            TxtImport.split((1..1001).joinToString("\n") { "第${it}章" }, "书", true)
        }
    }
    @Test fun titleDoesNotSplitSurrogatePair() {
        val title = TxtImport.title("😀".repeat(100))
        assertEquals(80, title.codePointCount(0, title.length))
        assertEquals(160, title.length)
    }
}
