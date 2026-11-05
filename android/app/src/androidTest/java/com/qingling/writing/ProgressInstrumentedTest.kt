package com.qingling.writing
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.qingling.writing.core.database.WritingDatabase
import com.qingling.writing.core.database.entities.SpaceEntity
import com.qingling.writing.core.database.migrations.Migrations
import com.qingling.writing.core.model.*
import com.qingling.writing.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.Assert.*

class ProgressInstrumentedTest {
    @Test fun migrationPreservesManuscriptAndAddsEmptyProgressTables() {
        val name = "progress-migration-test.db"
        val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),WritingDatabase::class.java)
        val ctx = ApplicationProvider.getApplicationContext<Context>(); ctx.deleteDatabase(name)
        helper.createDatabase(name,3).apply {
            execSQL("INSERT INTO spaces(id,kind,account_id) VALUES('s','GUEST',NULL)")
            execSQL("INSERT INTO books(id,space_id,title,author,description,created_at,updated_at,deleted_at,deletion_batch_id,local_seq,synced_local_seq,remote_version,local_dirty) VALUES('b','s','书','','','t','t',NULL,NULL,3,0,NULL,1)")
            execSQL("INSERT INTO volumes(id,book_id,title,position,created_at,updated_at,deleted_at,deletion_batch_id) VALUES('v','b','卷',0,'t','t',NULL,NULL)")
            execSQL("INSERT INTO chapters(id,book_id,volume_id,title,body,position,edit_seq,word_count,created_at,updated_at,deleted_at,deletion_batch_id) VALUES('c','b','v','章','保留正文',0,7,4,'t','t',NULL,NULL)")
            close()
        }
        helper.runMigrationsAndValidate(name,4,true,Migrations.MIGRATION_3_4).use { db ->
            db.query("SELECT body,edit_seq FROM chapters WHERE id='c'").use { c -> assertTrue(c.moveToFirst()); assertEquals("保留正文",c.getString(0)); assertEquals(7L,c.getLong(1)) }
            db.query("SELECT COUNT(*) FROM chapter_details").use { c -> c.moveToFirst(); assertEquals(0,c.getInt(0)) }
        }
        ctx.deleteDatabase(name)
    }
    @Test fun duplicateCreatesIndependentDraftAfterOriginalWithoutCountingWriting() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),WritingDatabase::class.java).build()
        try {
            var id = 0; val ids = { "copy-${id++}" }; val now = { "2026-11-02T00:00:00Z" }
            db.spaceDao().insert(SpaceEntity(GuestSpace.ID,GuestSpace.KIND_GUEST,null))
            val writing = RoomWritingRepository(db,ids,now)
            val outline = RoomOutlineRepository(db,ids,now)
            val original = writing.createBook(GuestSpace.ID,"复制测试","","")
            writing.saveDraft(ChapterDraft(original.id,original.title,"正文😀\n下一行",1))
            WritingProgressRepository(db).complete(original.id,true)
            val tail = outline.createChapter(original.bookId,original.volumeId,"末章")
            val copy = outline.duplicateChapter(original.id,"副本")
            assertNotEquals(original.id,copy.id)
            assertEquals("正文😀\n下一行",copy.body); assertEquals(0L,copy.editSeq)
            val chapters = outline.observeOutline(original.bookId).first().single().chapters
            assertEquals(listOf(original.id,copy.id,tail.id),chapters.map { it.id })
            assertEquals(listOf(0L,1L,2L),chapters.map { it.position })
            assertTrue(chapters.first().completed); assertFalse(chapters[1].completed)
            assertTrue(db.progressDao().days().first().isEmpty())
            writing.saveDraft(ChapterDraft(copy.id,copy.title,"改副本",1))
            assertEquals("正文😀\n下一行",writing.readChapter(original.id).body)
            outline.softDeleteChapter(original.id)
            try { outline.duplicateChapter(original.id,"不应生成"); fail("deleted source") }
            catch (_: com.qingling.writing.domain.NotFoundException) { }
            assertEquals(2,outline.observeOutline(original.bookId).first().single().chapters.size)
        } finally { db.close() }
    }
    @Test fun onlyCommittedEditorDraftsCountAndCompletionSurvivesBackupRestore() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),WritingDatabase::class.java).build()
        try {
            var id = 0; val ids = { "progress-${id++}" }; val now = { "2026-11-02T00:00:00Z" }
            db.spaceDao().insert(SpaceEntity(GuestSpace.ID,GuestSpace.KIND_GUEST,null))
            val writing = RoomWritingRepository(db,ids,now)
            val chapter = writing.createBook(GuestSpace.ID,"测试书","","")
            writing.saveDraft(ChapterDraft(chapter.id,chapter.title,"甲乙",1)) // import/programmatic is excluded
            assertTrue(db.progressDao().days().first().isEmpty())
            writing.saveDraft(ChapterDraft(chapter.id,chapter.title,"甲乙丙",2,true))
            writing.saveDraft(ChapterDraft(chapter.id,chapter.title,"不得覆盖",2,true)) // stale retry does not count twice
            assertEquals(1L,db.progressDao().days().first().single().netWords)
            val progress = WritingProgressRepository(db); progress.complete(chapter.id,true)
            val backup = RoomBackupRepository(db,ids,now)
            val captured = backup.capture(); assertTrue(captured.chapters.single().completed)
            backup.restoreCopies(captured)
            assertEquals(2,db.progressDao().details().count { it.completed })
            assertEquals(1L,db.progressDao().days().first().single().edits)
            assertEquals(1,writing.observeBooks(GuestSpace.ID).first().first { it.id == chapter.bookId }.completedCount)
        } finally { db.close() }
    }
}
