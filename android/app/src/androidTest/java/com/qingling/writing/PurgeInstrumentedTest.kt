package com.qingling.writing

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qingling.writing.core.database.WritingDatabase
import com.qingling.writing.core.database.entities.SpaceEntity
import com.qingling.writing.core.model.*
import com.qingling.writing.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PurgeInstrumentedTest {
    private lateinit var db: WritingDatabase
    private lateinit var writing: RoomWritingRepository
    private lateinit var outline: RoomOutlineRepository
    private lateinit var recycle: RoomRecycleBinRepository
    private lateinit var history: RoomHistoryRepository
    @Before fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WritingDatabase::class.java).build()
        var id = 0
        val ids = { "purge-${id++}" }
        val now = { "2026-11-01T00:00:00Z" }
        writing = RoomWritingRepository(db, ids, now)
        outline = RoomOutlineRepository(db, ids, now)
        history = RoomHistoryRepository(db, ids, now)
        recycle = RoomRecycleBinRepository(db)
        db.spaceDao().insert(SpaceEntity(GuestSpace.ID, GuestSpace.KIND_GUEST, null))
    }
    @After fun close() { db.close() }
    private suspend fun book(name: String = "书") = writing.createBook(GuestSpace.ID, name, "", "")
    private suspend fun entries() = recycle.observeRecycleBin(GuestSpace.ID).first()
    private suspend fun reject(block: suspend () -> Unit) {
        try { block(); fail("Must reject stale or foreign selection") } catch (_: IllegalStateException) { }
    }

    @Test fun chapterPurgeClearsHistoryCursorAndActivePointersButKeepsSibling() = runBlocking {
        val first = book()
        val second = outline.createChapter(first.bookId, first.volumeId, "第二章")
        history.createManualSnapshot(first.id)
        writing.saveEditorPosition(EditorPosition(first.id, 0, 0, 7))
        writing.recordActive(first.id)
        outline.softDeleteChapter(first.id)
        val plan = recycle.preparePurge(GuestSpace.ID, entries())
        assertEquals(1, plan.revisionCount)
        recycle.purge(plan)
        assertNull(db.outlineDao().chapterById(first.id))
        assertEquals(0, db.purgeDao().revisionCount(listOf(first.id)))
        assertNull(writing.readEditorPosition(first.id))
        assertEquals(second.id, writing.resolveBookActiveChapter(first.bookId))
        assertNotNull(db.bookDao().byId(first.bookId))
    }
    @Test fun deletedBookIncludesOlderDeletedDescendantsAndPreservesOtherBook() = runBlocking {
        val first = book()
        val older = outline.createChapter(first.bookId, first.volumeId, "早删")
        val other = book("其他书")
        outline.softDeleteChapter(older.id)
        writing.recordActive(first.id)
        writing.softDeleteBook(first.bookId)
        val selection = entries().filter { it.kind == RecycleEntry.Kind.BOOK }
        val plan = recycle.preparePurge(GuestSpace.ID, selection)
        assertEquals(setOf(first.id, older.id), plan.chapterIds.toSet())
        recycle.purge(plan)
        assertNull(db.bookDao().byId(first.bookId))
        assertNull(db.outlineDao().chapterById(older.id))
        assertNotNull(db.outlineDao().chapterById(other.id))
        // Removing the last-opened whole book returns to the home screen, not a different book.
        assertNull(writing.resolveLastActiveChapter())
        assertEquals(other.id, writing.resolveBookActiveChapter(other.bookId))
    }
    @Test fun volumePurgeIncludesOldDeletionButPreservesLiveOtherVolume() = runBlocking {
        val first = book()
        val older = outline.createChapter(first.bookId, first.volumeId, "早删")
        outline.createVolume(first.bookId, "第二卷")
        val secondVolume = outline.observeOutline(first.bookId).first().last()
        val keep = outline.createChapter(first.bookId, secondVolume.id, "保留")
        outline.softDeleteChapter(older.id)
        outline.softDeleteVolume(first.volumeId)
        val selected = entries().filter { it.kind == RecycleEntry.Kind.VOLUME }
        recycle.purge(recycle.preparePurge(GuestSpace.ID, selected))
        assertNull(db.outlineDao().volumeById(first.volumeId))
        assertNull(db.outlineDao().chapterById(older.id))
        assertNotNull(db.outlineDao().chapterById(keep.id))
    }
    @Test fun restoredSelectionRejectsWholeOperationBeforeAnyDeletion() = runBlocking {
        val first = book()
        val second = book("另一书")
        writing.softDeleteBook(first.bookId); writing.softDeleteBook(second.bookId)
        val selected = entries()
        val plan = recycle.preparePurge(GuestSpace.ID, selected)
        val restore = selected.first { it.bookId == second.bookId }
        writing.restoreDeletionBatch(restore.bookId, restore.batchId)
        reject { recycle.purge(plan) }
        assertNotNull(db.bookDao().byId(first.bookId))
        assertNotNull(db.outlineDao().chapterById(second.id))
    }
    @Test fun laterDeletedItemsAreOutsideConfirmedClearSnapshot() = runBlocking {
        val first = book(); val second = book("后删")
        writing.softDeleteBook(first.bookId)
        val plan = recycle.preparePurge(GuestSpace.ID, entries())
        writing.softDeleteBook(second.bookId)
        recycle.purge(plan)
        assertNull(db.bookDao().byId(first.bookId))
        assertNotNull(db.bookDao().byId(second.bookId))
    }
    @Test fun forgedCrossSpaceAndTamperedScopeAreRejected() = runBlocking {
        val first = book()
        writing.softDeleteBook(first.bookId)
        reject { recycle.preparePurge("different-space", entries()) }
        val plan = recycle.preparePurge(GuestSpace.ID, entries())
        reject { recycle.purge(plan.copy(chapterIds = emptyList())) }
        assertNotNull(db.outlineDao().chapterById(first.id))
    }
    @Test fun bulkDeletionUsesChunksAndKeepsUnselectedBook() = runBlocking {
        val first = book(); val other = book("保留")
        val source = db.outlineDao().chapterById(first.id)!!
        db.withTransaction {
            repeat(205) { i -> db.outlineDao().insertChapter(source.copy(id = "chunk-$i", position = i + 1L)) }
        }
        writing.softDeleteBook(first.bookId)
        val plan = recycle.preparePurge(GuestSpace.ID, entries())
        assertEquals(206, plan.chapterIds.size)
        recycle.purge(plan)
        assertTrue(db.purgeDao().chaptersOfBook(first.bookId).isEmpty())
        assertNotNull(db.outlineDao().chapterById(other.id))
    }
    @Test fun databaseFailureAfterChildDeletionRollsBackHistoryAndBody() = runBlocking {
        val first = book()
        history.createManualSnapshot(first.id)
        writing.saveEditorPosition(EditorPosition(first.id, 0, 0, 9))
        writing.softDeleteBook(first.bookId)
        val plan = recycle.preparePurge(GuestSpace.ID, entries())
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_purge BEFORE DELETE ON books BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try { recycle.purge(plan); fail("Expected simulated storage failure")
        } catch (_: android.database.sqlite.SQLiteException) { }
        assertNotNull(db.outlineDao().chapterById(first.id))
        assertEquals(1, db.purgeDao().revisionCount(listOf(first.id)))
        assertNotNull(writing.readEditorPosition(first.id))
        assertNotNull(db.bookDao().byId(first.bookId))
    }
}
