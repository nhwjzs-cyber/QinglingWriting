package com.qingling.writing

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.qingling.writing.core.database.WritingDatabase
import com.qingling.writing.core.database.entities.SpaceEntity
import com.qingling.writing.core.model.*
import com.qingling.writing.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class ChapterStructureInstrumentedTest {
    private class Fixture(val db: WritingDatabase) {
        var serial = 0
        val ids = { "structure-${serial++}" };val now = { "2026-11-02T00:00:00Z" }
        val writing = RoomWritingRepository(db,ids,now)
        val outline = RoomOutlineRepository(db,ids,now)
        val repository = ChapterStructureRepository(db,ids,now)
        val planning = PlanningRepository(db,outline,writing,ids,now)
        suspend fun book() = writing.createBook(GuestSpace.ID,"结构测试","","")
        suspend fun text(c: ChapterDocument,body: String) {
            assertTrue(writing.saveDraft(ChapterDraft(c.id,c.title,body,writing.readChapter(c.id).editSeq+1)) is SaveResult.Committed)
        }
    }
    private fun fixture(block: suspend (Fixture) -> Unit): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),WritingDatabase::class.java).build()
        try { db.spaceDao().insert(SpaceEntity(GuestSpace.ID,GuestSpace.KIND_GUEST,null));block(Fixture(db)) }
        finally { db.close() }
    }
    @Test fun splitPreservesBodyHistoryOrderMetadataAndResumePointer() = fixture { f ->
        val c = f.book();val tail = f.outline.createChapter(c.bookId,c.volumeId,"后面的章")
        val body = "前😀\n后  ";f.text(c,body)
        WritingProgressRepository(f.db).complete(c.id,true)
        f.planning.save(f.planning.scoped(c.bookId,PlanningKind.CHAPTER_OUTLINE,chapterId = c.id).copy(body = "原细纲"))
        val plan = f.repository.previewSplit(c.bookId,c.id,body,4)
        val newId = f.repository.apply(plan,"新章")
        val left = f.writing.readChapter(c.id);val right = f.writing.readChapter(newId)
        assertEquals(body,left.body+right.body);assertEquals("前😀\n",left.body)
        val rows = f.outline.observeOutline(c.bookId).first().single().chapters
        assertEquals(listOf(c.id,newId,tail.id),rows.map { it.id });assertEquals(listOf(0L,1L,2L),rows.map { it.position })
        assertFalse(rows.first().completed);assertEquals("原细纲",rows.first().outline);assertEquals("",rows[1].outline)
        assertEquals(body,f.db.revisionDao().observeByChapter(c.id).first().single().body)
        assertEquals(newId,f.writing.resolveLastActiveChapter());assertEquals(newId,f.writing.resolveBookActiveChapter(c.bookId))
        assertEquals(newId,f.db.bookDao().byId(c.bookId)?.lastActiveChapterId)
        assertTrue(f.db.progressDao().days().first().isEmpty())
    }
    @Test fun splitRejectsStalePreviewWithoutChangingAnyChapter() = fixture { f ->
        val c = f.book();f.text(c,"旧内容");val plan = f.repository.previewSplit(c.bookId,c.id,"旧内容",1)
        f.text(c,"新内容")
        try { f.repository.apply(plan,"新章");fail("stale split must fail") } catch (_: IllegalStateException) { }
        assertEquals("新内容",f.writing.readChapter(c.id).body)
        assertEquals(1,f.outline.observeOutline(c.bookId).first().single().chapters.size)
        assertTrue(f.db.revisionDao().observeByChapter(c.id).first().isEmpty())
    }
    @Test fun splitFailureRollsBackBodyHistoryAndNewChapter() = fixture { f ->
        val c = f.book();f.text(c,"甲😀乙")
        val before = f.writing.readChapter(c.id);val plan = f.repository.previewSplit(c.bookId,c.id,before.body,3)
        f.db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_split BEFORE INSERT ON chapters WHEN NEW.title='拒绝' BEGIN SELECT RAISE(ABORT,'test failure'); END")
        try { f.repository.apply(plan,"拒绝");fail("injected failure") } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(before,f.writing.readChapter(c.id))
        assertEquals(1,f.outline.observeOutline(c.bookId).first().single().chapters.size)
        assertTrue(f.db.revisionDao().observeByChapter(c.id).first().isEmpty())
    }
    @Test fun mergeUsesDirectoryOrderKeepsUnselectedAndRecoverableSources() = fixture { f ->
        val a = f.book();val untouched = f.outline.createChapter(a.bookId,a.volumeId,"未选中")
        f.outline.createVolume(a.bookId,"第二卷")
        val volume = f.outline.observeOutline(a.bookId).first().last()
        val b = f.outline.createChapter(a.bookId,volume.id,"乙章");val c = f.outline.createChapter(a.bookId,volume.id,"丙章")
        f.text(a,"甲\n  ");f.text(b,"😀乙");f.text(c,"丙\r\n")
        WritingProgressRepository(f.db).complete(a.id,true)
        f.planning.save(f.planning.scoped(a.bookId,PlanningKind.CHAPTER_OUTLINE,chapterId = b.id).copy(body = "乙细纲"))
        val plan = f.repository.previewMerge(a.bookId,listOf(c.id,a.id,b.id),true)
        assertEquals(listOf(a.id,b.id,c.id),plan.source.documents.map { it.id })
        val expected = ChapterStructure.merge(plan.source.documents,true)
        assertEquals(a.id,f.repository.apply(plan,"合并结果"))
        assertEquals(expected,f.writing.readChapter(a.id).body)
        assertEquals(listOf(a.id,untouched.id),f.outline.observeOutline(a.bookId).first().flatMap { it.chapters }.map { it.id })
        assertFalse(f.db.progressDao().details().first { it.chapterId == a.id }.completed)
        for (row in plan.source.documents) assertEquals(row.body,f.db.revisionDao().observeByChapter(row.id).first().single().body)
        val deletedB = requireNotNull(f.db.outlineDao().chapterById(b.id));val deletedC = requireNotNull(f.db.outlineDao().chapterById(c.id))
        assertNotNull(deletedB.deletedAt);assertNotNull(deletedC.deletedAt);assertNotEquals(deletedB.deletionBatchId,deletedC.deletionBatchId)
        assertTrue(f.planning.observe(a.bookId).first().isEmpty())
        val backup = RoomBackupRepository(f.db,f.ids,f.now).capture()
        assertEquals("乙细纲",backup.planning.single().body);assertTrue(backup.chapters.first { it.id == b.id }.deletedAt != null)
        f.writing.restoreDeletionBatch(a.bookId,requireNotNull(deletedB.deletionBatchId))
        assertEquals("😀乙",f.writing.readChapter(b.id).body)
        assertTrue(f.db.outlineDao().chapterById(c.id)?.deletedAt != null)
        assertEquals("乙细纲",f.planning.observe(a.bookId).first().single().body)
        assertTrue(f.db.progressDao().days().first().isEmpty())
    }
    @Test fun mergeRejectsMovedOrForeignSources() = fixture { f ->
        val a = f.book();val b = f.outline.createChapter(a.bookId,a.volumeId,"乙章");val foreign = f.book()
        try { f.repository.previewMerge(a.bookId,listOf(a.id,foreign.id),false);fail("cross book") } catch (_: IllegalStateException) { }
        val plan = f.repository.previewMerge(a.bookId,listOf(a.id,b.id),false)
        f.outline.moveChapterWithinVolume(b.id,-1)
        try { f.repository.apply(plan,"新名");fail("stale positions") } catch (_: IllegalStateException) { }
        assertEquals("",f.writing.readChapter(a.id).body);assertEquals(2,f.outline.observeOutline(a.bookId).first().single().chapters.size)
    }
    @Test fun mergeFailureAfterTargetWriteRollsBackEverything() = fixture { f ->
        val a = f.book();val b = f.outline.createChapter(a.bookId,a.volumeId,"乙章")
        f.text(a,"甲");f.text(b,"乙")
        val beforeA = f.writing.readChapter(a.id);val beforeB = f.writing.readChapter(b.id)
        val plan = f.repository.previewMerge(a.bookId,listOf(a.id,b.id),false)
        f.db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_merge BEFORE UPDATE OF deleted_at ON chapters WHEN NEW.deleted_at IS NOT NULL BEGIN SELECT RAISE(ABORT,'test failure'); END")
        try { f.repository.apply(plan,"合并");fail("injected failure") } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(beforeA,f.writing.readChapter(a.id));assertEquals(beforeB,f.writing.readChapter(b.id))
        assertTrue(f.db.revisionDao().observeByChapter(a.id).first().isEmpty());assertTrue(f.db.revisionDao().observeByChapter(b.id).first().isEmpty())
        assertEquals(2,f.outline.observeOutline(a.bookId).first().single().chapters.size)
    }
}
