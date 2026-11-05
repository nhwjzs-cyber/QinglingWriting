package com.qingling.writing

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qingling.writing.core.database.WritingDatabase
import com.qingling.writing.core.database.entities.SpaceEntity
import com.qingling.writing.core.model.*
import com.qingling.writing.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BatchEditInstrumentedTest {
    private lateinit var db: WritingDatabase
    private lateinit var writing: RoomWritingRepository
    private lateinit var outline: RoomOutlineRepository
    private lateinit var history: RoomHistoryRepository
    private lateinit var batch: RoomBatchEditRepository
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), WritingDatabase::class.java).build()
        var id = 0; val ids = { "batch-${id++}" }; val now = { "2026-11-02T00:00:00Z" }
        writing = RoomWritingRepository(db, ids, now); outline = RoomOutlineRepository(db, ids, now)
        history = RoomHistoryRepository(db, ids, now); batch = RoomBatchEditRepository(db, ids, now)
        db.spaceDao().insert(SpaceEntity(GuestSpace.ID, GuestSpace.KIND_GUEST, null))
    }
    @After fun close() { db.close() }
    private suspend fun book(body: String = "alpha alpha"): ChapterDocument {
        val doc = writing.createBook(GuestSpace.ID, "测试书", "", "")
        writing.saveDraft(ChapterDraft(doc.id, doc.title, body, 1))
        return writing.readChapter(doc.id)
    }
    private suspend fun reject(block: suspend () -> Unit) {
        try { block(); fail("Expected rejection") } catch (_: IllegalStateException) { }
    }
    @Test fun replacementSavesHistoryWordCountSequenceAndCanRestore() = runBlocking {
        val first = book(); val second = outline.createChapter(first.bookId, first.volumeId, "第二章")
        writing.saveDraft(ChapterDraft(second.id, second.title, "alpha", 1))
        val beforeSeq = db.bookDao().localSeq(first.bookId)!!
        val p = batch.preview(BatchScope(first.bookId), BatchOperation.Replace("alpha", "乙😀"))
        assertEquals(2, batch.apply(p))
        assertEquals("乙😀 乙😀", writing.readChapter(first.id).body)
        assertEquals(2L, writing.readChapter(first.id).editSeq)
        assertEquals(4L, db.outlineDao().chapterById(first.id)!!.wordCount)
        assertEquals(beforeSeq + 1, db.bookDao().localSeq(first.bookId))
        val revisions = history.observeRevisions(first.id).first()
        assertEquals("alpha alpha", revisions.single().body)
        assertEquals(Revision.Source.BEFORE_CHANGE, revisions.single().source)
        assertEquals("alpha alpha", history.restoreRevision(first.id, revisions.single().id).body)
    }
    @Test fun formatVolumeDoesNotTouchOtherVolumeOrDeletedChapter() = runBlocking {
        val first = book(" 未选卷 ")
        outline.createVolume(first.bookId, "第二卷")
        val volume = outline.observeOutline(first.bookId).first().last()
        val target = outline.createChapter(first.bookId, volume.id, "目标")
        val deleted = outline.createChapter(first.bookId, volume.id, "已删")
        writing.saveDraft(ChapterDraft(target.id, target.title, " 正文  \n\n另一段", 1))
        writing.saveDraft(ChapterDraft(deleted.id, deleted.title, " 不改 ", 1)); outline.softDeleteChapter(deleted.id)
        val p = batch.preview(BatchScope(first.bookId, volume.id), BatchOperation.Format(FormatOptions()))
        assertEquals(1, batch.apply(p))
        assertEquals("　　正文\n\n　　另一段", writing.readChapter(target.id).body)
        assertEquals(" 未选卷 ", writing.readChapter(first.id).body)
        assertEquals(" 不改 ", db.outlineDao().chapterById(deleted.id)!!.body)
    }
    @Test fun editedPreviouslyUnaffectedChapterMakesWholePreviewStale() = runBlocking {
        val first = book(); val second = outline.createChapter(first.bookId, first.volumeId, "原本未命中")
        val p = batch.preview(BatchScope(first.bookId), BatchOperation.Replace("alpha", "beta"))
        writing.saveDraft(ChapterDraft(second.id, second.title, "alpha newly typed", 1))
        reject { batch.apply(p) }
        assertEquals("alpha alpha", writing.readChapter(first.id).body)
        assertTrue(history.observeRevisions(first.id).first().isEmpty())
    }
    @Test fun newChapterInvalidatesPreviewAndApplyingTwiceIsRejected() = runBlocking {
        val first = book()
        val stale = batch.preview(BatchScope(first.bookId), BatchOperation.Replace("alpha", "beta"))
        outline.createChapter(first.bookId, first.volumeId, "新章")
        reject { batch.apply(stale) }
        val fresh = batch.preview(BatchScope(first.bookId), BatchOperation.Replace("alpha", "beta"))
        batch.apply(fresh)
        reject { batch.apply(fresh) }
        assertEquals(1, history.observeRevisions(first.id).first().size)
    }
    @Test fun writeFailureOnSecondChapterRollsBackFirstChangeAndAllHistory() = runBlocking {
        val first = book(); val second = outline.createChapter(first.bookId, first.volumeId, "第二章")
        writing.saveDraft(ChapterDraft(second.id, second.title, "alpha", 1))
        val p = batch.preview(BatchScope(first.bookId), BatchOperation.Replace("alpha", "beta"))
        val seq = db.bookDao().localSeq(first.bookId)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_batch BEFORE UPDATE OF body ON chapters WHEN OLD.id='${second.id}' BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try { batch.apply(p); fail("Expected database error") } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals("alpha alpha", writing.readChapter(first.id).body)
        assertEquals("alpha", writing.readChapter(second.id).body)
        assertTrue(history.observeRevisions(first.id).first().isEmpty())
        assertTrue(history.observeRevisions(second.id).first().isEmpty())
        assertEquals(seq, db.bookDao().localSeq(first.bookId))
    }
    @Test fun forgedChangesAndCrossBookVolumeAreRejected() = runBlocking {
        val first = book(); val other = book("other")
        val p = batch.preview(BatchScope(first.bookId), BatchOperation.Replace("alpha", "beta"))
        reject { batch.apply(p.copy(changes = p.changes.map { it.copy(after = "forged") })) }
        reject { batch.preview(BatchScope(first.bookId, other.volumeId), BatchOperation.Replace("a", "b")) }
        assertEquals("alpha alpha", writing.readChapter(first.id).body)
        assertEquals("other", writing.readChapter(other.id).body)
    }
    @Test fun deletingBookRejectsPreviewWithoutCreatingHistory() = runBlocking {
        val first = book()
        val p = batch.preview(BatchScope(first.bookId), BatchOperation.Replace("alpha", "beta"))
        writing.softDeleteBook(first.bookId)
        reject { batch.apply(p) }
        assertEquals("alpha alpha", db.outlineDao().chapterById(first.id)!!.body)
        assertTrue(history.observeRevisions(first.id).first().isEmpty())
    }
}
