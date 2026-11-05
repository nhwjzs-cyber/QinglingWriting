package com.qingling.writing

import com.qingling.writing.core.files.TxtWriter
import com.qingling.writing.core.model.ExportChapter
import com.qingling.writing.core.model.ExportSnapshot
import com.qingling.writing.core.model.ExportVolume
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * fixtures 字节级对齐(docs/06 M3 通过条件):export-input.json + export-expected.txt。
 * 无 BOM、LF 换行、\r\n → \n、空章保留、前导空格保留、目录序非选择序、末尾两个 LF。
 */
class TxtWriterFixturesTest {

    // 与 fixtures/export-input.json 一致的测试数据
    private val snapshot = ExportSnapshot(
        bookTitle = "晚风来信",
        bookLocalSeq = 0L,
        volumes = listOf(
            ExportVolume(
                id = "v1", title = "第一卷",
                chapters = listOf(
                    ExportChapter(id = "c1", volumeId = "v1", title = "第一章",
                        body = "  风来了。\r\n下一行🙂"),
                    ExportChapter(id = "c2", volumeId = "v1", title = "第二章", body = ""),
                ),
            ),
            ExportVolume(
                id = "v2", title = "第二卷",
                chapters = listOf(
                    ExportChapter(id = "c3", volumeId = "v2", title = "第三章", body = "收尾。\n"),
                ),
            ),
        ),
    )

    /** fixtures/export-expected.txt 的精确字节内容(UTF-8, LF, 无 BOM)。 */
    private val expectedBytes = (
        "晚风来信\n" +
            "\n" +
            "第一卷\n" +
            "\n" +
            "第一章\n" +
            "  风来了。\n" +
            "下一行🙂\n" +
            "\n" +
            "第二章\n" +
            "\n" +
            "\n" +
            "第二卷\n" +
            "\n" +
            "第三章\n" +
            "收尾。\n" +
            "\n"
        ).toByteArray(Charsets.UTF_8)

    @Test
    fun `fixtures byte-level alignment with all titles enabled`() {
        val actual = TxtWriter.write(snapshot, includeBookTitle = true, includeVolumeTitles = true, includeChapterTitles = true)
        assertArrayEquals(
            "导出字节必须与 fixtures/export-expected.txt 逐字节一致",
            expectedBytes, actual,
        )
    }

    @Test
    fun `output has no BOM`() {
        val actual = TxtWriter.write(snapshot, true, true, true)
        assertTrue("不得有 UTF-8 BOM (EF BB BF)", actual.size < 3 || actual[0] != 0xEF.toByte() || actual[1] != 0xBB.toByte() || actual[2] != 0xBF.toByte())
    }

    @Test
    fun `output uses only LF line endings`() {
        val text = String(TxtWriter.write(snapshot, true, true, true), Charsets.UTF_8)
        assertTrue("不得包含 \\r", !text.contains('\r'))
    }

    @Test
    fun `empty chapter body preserved as blank line`() {
        val text = String(TxtWriter.write(snapshot, true, true, true), Charsets.UTF_8)
        // 第二章(空正文)在输出中应只产生标题行和空白行
        assertTrue(text.contains("第二章\n\n"))
    }

    @Test
    fun `leading spaces preserved in body`() {
        val text = String(TxtWriter.write(snapshot, true, true, true), Charsets.UTF_8)
        assertTrue(text.contains("  风来了。"))
    }

    @Test
    fun `without book title`() {
        val actual = TxtWriter.write(snapshot, includeBookTitle = false, includeVolumeTitles = true, includeChapterTitles = true)
        val text = String(actual, Charsets.UTF_8)
        assertTrue("不应包含书名", !text.contains("晚风来信"))
        assertTrue(text.startsWith("第一卷\n"))
    }

    @Test
    fun `without volume titles`() {
        val actual = TxtWriter.write(snapshot, includeBookTitle = true, includeVolumeTitles = false, includeChapterTitles = true)
        val text = String(actual, Charsets.UTF_8)
        assertTrue("不应包含卷名", !text.contains("第一卷"))
        assertTrue(!text.contains("第二卷"))
        assertTrue("章名仍在", text.contains("第一章"))
    }

    @Test
    fun `without chapter titles`() {
        val actual = TxtWriter.write(snapshot, includeBookTitle = true, includeVolumeTitles = true, includeChapterTitles = false)
        val text = String(actual, Charsets.UTF_8)
        assertTrue("不应包含章名", !text.contains("第一章\n"))
        assertTrue("正文仍在", text.contains("风来了"))
    }
}
