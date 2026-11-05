package com.qingling.writing.feature.export

import com.qingling.writing.core.files.ExportFileStore
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExportFileStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun store() = ExportFileStore(temporary.newFolder())

    @Test fun frozenFileDoesNotChangeWhenInputBytesChange() {
        val files = store()
        val data = "  正文😀\n\n".toByteArray(Charsets.UTF_8)
        val expected = data.copyOf()
        val staged = files.stage("我的书", data)
        data.fill(0)
        val output = ByteArrayOutputStream()
        files.copyTo(staged.token) { output }
        assertArrayEquals(expected, output.toByteArray())
    }
    @Test fun writeAndCloseFailuresAreNotSuccess() {
        val files = store(); val staged = files.stage("书", byteArrayOf(1, 2))
        assertThrows(IOException::class.java) { files.copyTo(staged.token) { null } }
        assertThrows(IOException::class.java) { files.copyTo(staged.token) { object : OutputStream() {
            override fun write(b: Int) { throw IOException("full") }
        } } }
        assertThrows(IOException::class.java) { files.copyTo(staged.token) { object : ByteArrayOutputStream() {
            override fun close() { throw IOException("disconnected") }
        } } }
        assertEquals(2L, files.resolve(staged.token).length())
    }
    @Test fun namesCannotEscapeExportDirectoryAndDuplicateTitlesStaySeparate() {
        val files = store()
        val first = files.stage("../a\\b:*?\"<>|\n", byteArrayOf(1))
        val second = files.stage("../a\\b:*?\"<>|\n", byteArrayOf(2))
        assertNotEquals(first.token, second.token)
        assertEquals(first.fileName, second.fileName)
        assertThrows(IllegalArgumentException::class.java) { files.resolve("../../writing.db") }
        assertEquals("未命名作品.txt", ExportFileStore.safeFileName(" ... "))
    }
    @Test fun boundedPreviewDoesNotCutAnEmojiAndFullFileRemains() {
        val files = store(); val text = "ab😀cdef"
        val staged = files.stage("书", text.toByteArray(Charsets.UTF_8))
        val preview = files.preview(staged.token, 3)
        assertEquals("ab", preview.text)
        assertTrue(preview.truncated)
        assertEquals(text, staged.file.readText(Charsets.UTF_8))
    }
    @Test fun lostCacheIsReported() {
        val files = store(); val staged = files.stage("书", byteArrayOf(1))
        staged.file.delete()
        assertThrows(IOException::class.java) { files.resolve(staged.token) }
    }
}
