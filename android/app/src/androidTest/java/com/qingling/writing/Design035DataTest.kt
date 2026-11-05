package com.qingling.writing
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.qingling.writing.core.database.WritingDatabase
import com.qingling.writing.core.database.entities.SpaceEntity
import com.qingling.writing.core.files.BackupCodec
import com.qingling.writing.core.model.*
import com.qingling.writing.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.*
class Design035DataTest {
    @Test fun notesRoundTripRemapsAnchorsAndGuardsConflicts()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),WritingDatabase::class.java).build()
        try {
            var seq=0;val ids={"design-"+seq++};val now={"2026-11-03T00:00:00Z"}
            db.spaceDao().insert(SpaceEntity(GuestSpace.ID,GuestSpace.KIND_GUEST,null))
            val writing=RoomWritingRepository(db,ids,now);val outline=RoomOutlineRepository(db,ids,now)
            val repo=PlanningRepository(db,outline,writing,ids,now)
            val chapter=writing.createBook(GuestSpace.ID,"夜航笔记","","")
            val original="夜里两点，灯塔的光扫过海面。\n银簪的来历😀  "
            writing.saveDraft(ChapterDraft(chapter.id,chapter.title,original,1))
            val note=repo.capture("银簪的来历",chapter.bookId,chapter.id,"夜航笔记 · 第一章",14,true)
            val updated=repo.updateNote(note,QuickNote.decode(note.body).copy(resolved=true))
            try {repo.updateNote(note,QuickNote.decode(note.body).copy(text="过期覆盖"));fail("stale save") } catch(_:PlanningConflict){}
            assertEquals(original,writing.readChapter(chapter.id).body)
            val backup=RoomBackupRepository(db,ids,now)
            val captured=backup.captureBook(chapter.bookId)
            val bytes=ByteArrayOutputStream();BackupCodec().write(captured,bytes)
            backup.restoreCopies(BackupCodec().read(ByteArrayInputStream(bytes.toByteArray())))
            val restored=repo.inbox().first().single{it.bookId!=chapter.bookId}
            val anchor=QuickNote.decode(restored.body)
            assertNotEquals(chapter.id,anchor.chapterId)
            assertEquals(restored.bookId,writing.readChapter(anchor.chapterId).bookId)
            assertEquals(original,writing.readChapter(anchor.chapterId).body)
            assertTrue(anchor.resolved);assertTrue(anchor.foreshadow);assertEquals(14,anchor.offset)
            repo.updateNote(updated,QuickNote.decode(updated.body).copy(deleted=true))
            assertEquals(1,repo.inbox().first().size)
            val empty=writing.createEmptyBook(GuestSpace.ID,"空书","","")
            assertTrue(outline.observeOutline(empty).first().isEmpty())
            assertNull(writing.resolveBookActiveChapter(empty))
            assertFalse(outline.observeOutline(chapter.bookId).first().single().chapters.single().completed)
            WritingProgressRepository(db).complete(chapter.id,true)
            assertTrue(outline.observeOutline(chapter.bookId).first().single().chapters.single().completed)
        } finally {db.close()}
    }
    @Test fun captureIntoEmptyLibraryIsAtomicAndPortable()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),WritingDatabase::class.java).build()
        try {
            var seq=0;val ids={"empty-"+seq++};val now={"2026-11-03T00:00:00Z"}
            val w=RoomWritingRepository(db,ids,now);val o=RoomOutlineRepository(db,ids,now)
            val p=PlanningRepository(db,o,w,ids,now)
            val note=p.capture("没有作品时也能捕获😀")
            assertEquals("没有作品时也能捕获😀",QuickNote.decode(note.body).text)
            assertEquals(0,w.observeBooks(GuestSpace.ID).first().size)
            assertEquals(1,RoomBackupRepository(db,ids,now).capture().planning.size)
            try{p.capture(" ");fail("blank") }catch(_:IllegalArgumentException){}
            assertEquals(1,p.inbox().first().size)
        }finally{db.close()}
    }
}
