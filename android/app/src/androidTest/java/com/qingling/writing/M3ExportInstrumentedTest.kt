package com.qingling.writing

import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.qingling.writing.core.database.WritingDatabase
import com.qingling.writing.core.database.entities.SpaceEntity
import com.qingling.writing.core.files.ExportFileStore
import com.qingling.writing.core.files.TxtWriter
import com.qingling.writing.core.model.*
import com.qingling.writing.data.RoomExportRepository
import com.qingling.writing.data.RoomOutlineRepository
import com.qingling.writing.data.RoomWritingRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class M3ExportInstrumentedTest {
    private lateinit var context: Context
    private lateinit var db: WritingDatabase
    private lateinit var writing: RoomWritingRepository
    private lateinit var outline: RoomOutlineRepository
    private var counter = 0
    private fun id() = "export-${counter++}"
    @Before fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, WritingDatabase::class.java).build()
        writing = RoomWritingRepository(db, ::id, { "2026-11-01T00:00:00Z" })
        outline = RoomOutlineRepository(db, ::id, { "2026-11-01T00:00:00Z" })
        db.spaceDao().insert(SpaceEntity(GuestSpace.ID, GuestSpace.KIND_GUEST, null))
    }
    @After fun cleanup() { db.close() }

    @Test fun databaseSnapshotUsesDirectoryOrderAndExcludesDeletedAndForeignChapters() = runBlocking {
        val first = writing.createBook(GuestSpace.ID, "导出测试", "", "")
        val second = outline.createChapter(first.bookId, first.volumeId, "9在前")
        val deleted = outline.createChapter(first.bookId, first.volumeId, "已删除")
        outline.moveChapterWithinVolume(second.id, -1)
        writing.saveDraft(ChapterDraft(second.id, second.title, "  空格😀\n\n", 1))
        outline.softDeleteChapter(deleted.id)
        outline.createVolume(first.bookId, "第二卷")
        val volume2 = outline.observeOutline(first.bookId).first().last()
        val third = outline.createChapter(first.bookId, volume2.id, "A在后")
        val foreign = writing.createBook(GuestSpace.ID, "其他书", "", "")
        val selection = ExportSelection(first.bookId, linkedSetOf(third.id, deleted.id, first.id, foreign.id, second.id))
        val before = writing.readChapter(second.id)
        val result = RoomExportRepository(db).capture(selection)
        assertEquals(listOf(second.id, first.id, third.id), result.volumes.flatMap { it.chapters }.map { it.id })
        assertEquals("", result.volumes[0].chapters[1].body)
        assertEquals("  空格😀\n\n", result.volumes[0].chapters[0].body)
        assertEquals(before, writing.readChapter(second.id))
        outline.softDeleteVolume(volume2.id)
        assertFalse(RoomExportRepository(db).capture(selection).volumes.flatMap { it.chapters }.any { it.id == third.id })
    }

    @Test fun sourceEditsAfterCaptureCannotChangeStagedOutput() = runBlocking {
        val doc = writing.createBook(GuestSpace.ID, "固定快照", "", "")
        writing.saveDraft(ChapterDraft(doc.id, doc.title, "旧正文", 1))
        val snapshot = RoomExportRepository(db).capture(ExportSelection(doc.bookId, setOf(doc.id)))
        writing.saveDraft(ChapterDraft(doc.id, doc.title, "新正文", 2))
        val output = TxtWriter.write(snapshot, true, true, true).toString(Charsets.UTF_8)
        assertTrue(output.contains("旧正文")); assertFalse(output.contains("新正文"))
        assertEquals("新正文", writing.readChapter(doc.id).body)
    }

    @Test fun shareUriCanBeReadByAnotherUidWithTemporaryPermission() {
        val files = ExportFileStore(File(context.cacheDir, "exports"))
        val expected = "跨应用读取😀\n".toByteArray(Charsets.UTF_8)
        val staged = files.stage("分享测试", expected)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.exports", staged.file)
        assertEquals("content", uri.scheme)
        context.grantUriPermission("com.android.shell", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("content read --uri $uri")
            val actual = ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
            assertArrayEquals(expected, actual)
            assertThrows(IllegalArgumentException::class.java) {
                FileProvider.getUriForFile(context, "${context.packageName}.exports", File(context.filesDir, "writing.db"))
            }
        } finally {
            context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            staged.file.delete(); staged.file.parentFile?.delete()
        }
    }
}
