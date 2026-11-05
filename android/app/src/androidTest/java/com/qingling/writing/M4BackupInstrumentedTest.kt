package com.qingling.writing

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qingling.writing.core.database.WritingDatabase
import com.qingling.writing.core.database.entities.SpaceEntity
import com.qingling.writing.core.files.BackupCodec
import com.qingling.writing.core.model.*
import com.qingling.writing.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class M4BackupInstrumentedTest {
    private lateinit var db: WritingDatabase
    private lateinit var writing: RoomWritingRepository
    private lateinit var outline: RoomOutlineRepository
    private var ids = 0
    private val time = "2026-11-01T00:00:00Z"
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WritingDatabase::class.java).build()
        db.spaceDao().insert(SpaceEntity(GuestSpace.ID, GuestSpace.KIND_GUEST, null))
        writing = RoomWritingRepository(db, { "original-${ids++}" }, { time })
        outline = RoomOutlineRepository(db, { "original-${ids++}" }, { time })
    }
    @After fun cleanup() { db.close() }
    private fun backups() = RoomBackupRepository(db, { "restored-${ids++}" }, { time })

    @Test fun fullRoundTripCreatesSeparateCopiesWithHistoryCursorAndDeletionBatches() = runBlocking {
        val doc = writing.createBook(GuestSpace.ID, "原书👩‍🚀", "作者", "简介")
        writing.saveDraft(ChapterDraft(doc.id, doc.title, "　原文😀\n\n不能变  ", 1))
        writing.saveEditorPosition(EditorPosition(doc.id, 3, 5, 0))
        RoomHistoryRepository(db, { "history-${ids++}" }, { time }).createManualSnapshot(doc.id)
        val deleted = outline.createChapter(doc.bookId, doc.volumeId, "单独删除")
        outline.softDeleteChapter(deleted.id)
        outline.createVolume(doc.bookId, "空卷")
        val deadBook = writing.createBook(GuestSpace.ID, "回收站书", "", "")
        writing.softDeleteBook(deadBook.bookId)
        val repo = backups(); val source = repo.capture()
        val encoded = ByteArrayOutputStream(); BackupCodec().write(source, encoded)
        val loaded = BackupCodec().read(ByteArrayInputStream(encoded.toByteArray()))
        val before = writing.readChapter(doc.id)
        assertEquals(BackupRestoreResult(2, 3), repo.restoreCopies(loaded))
        val after = repo.capture()
        assertEquals(4, after.books.size); assertEquals(6, after.chapters.size)
        assertEquals(before, writing.readChapter(doc.id))
        val copiedBook = after.books.single { it.id != doc.bookId && it.title.startsWith("原书") }
        val copiedChapter = after.chapters.single { it.bookId == copiedBook.id && it.deletedAt == null }
        assertNotEquals(doc.id, copiedChapter.id); assertEquals(before.body, copiedChapter.body)
        assertEquals(0L, writing.readChapter(copiedChapter.id).editSeq)
        assertEquals(3, writing.readEditorPosition(copiedChapter.id)!!.selectionStart)
        assertEquals(1, db.revisionDao().observeByChapter(copiedChapter.id).first().size)
        val copiedDeleted = after.chapters.single { it.bookId == copiedBook.id && it.deletedAt != null }
        assertNotEquals(source.chapters.single { it.id == deleted.id }.batch, copiedDeleted.batch)
        assertEquals(2, after.books.count { it.deletedAt != null })
    }

    @Test fun constraintFailureRollsBackAllNewRowsAndPreservesOriginals() = runBlocking {
        val one = writing.createBook(GuestSpace.ID, "一", "", "")
        writing.createBook(GuestSpace.ID, "二", "", "")
        val original = backups().capture()
        var calls = 0
        val broken = RoomBackupRepository(db, { if (calls++ == 0) "first-copy" else one.bookId }, { time })
        var failed = false
        try { broken.restoreCopies(original) } catch (_: Exception) { failed = true }
        assertTrue(failed)
        assertEquals(original, backups().capture())
        assertNull(db.bookDao().byId("first-copy"))
    }

    @Test fun corruptCrossBookReferenceFailsBeforeAnyInsert() = runBlocking {
        writing.createBook(GuestSpace.ID, "一", "", "")
        val repo = backups(); val original = repo.capture()
        val invalid = original.copy(chapters = original.chapters.map { it.copy(bookId = "foreign-book") })
        var failed = false
        try { repo.restoreCopies(invalid) } catch (_: IllegalArgumentException) { failed = true }
        assertTrue(failed); assertEquals(original, repo.capture())
    }

    @Test fun repeatedRestoreCreatesIndependentCopiesWithoutChangingPreviousCopies() = runBlocking {
        writing.createBook(GuestSpace.ID, "原书", "", "")
        val repo = backups(); val original = repo.capture()
        repo.restoreCopies(original)
        val previous = repo.capture()
        repo.restoreCopies(original)
        val after = repo.capture()
        assertEquals(3, after.books.size)
        assertTrue(after.books.containsAll(previous.books)); assertTrue(after.chapters.containsAll(previous.chapters))
    }

    @Test fun oversizedSourceIsRejectedBeforeReadingItsRowsIntoTheBackup() = runBlocking {
        val chapter = writing.createBook(GuestSpace.ID, "大小边界", "", "")
        val limited = RoomBackupRepository(db, { "never-written" }, { time }, captureLimitBytes = 1)
        var failed = false
        try { limited.capture() } catch (_: IllegalArgumentException) { failed = true }
        assertTrue(failed)
        assertNotNull(writing.readChapter(chapter.id))
        assertEquals(1, db.backupDao().books(GuestSpace.ID).size)
    }
}
