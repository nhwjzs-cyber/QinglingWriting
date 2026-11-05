package com.qingling.writing

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.qingling.writing.core.database.WritingDatabase
import com.qingling.writing.core.database.entities.SpaceEntity
import com.qingling.writing.core.database.migrations.Migrations
import com.qingling.writing.core.files.BackupCodec
import com.qingling.writing.core.model.*
import com.qingling.writing.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.*

class WritingDashboardInstrumentedTest {
    @Test fun migrationFiveToSixKeepsManuscriptsPlanningAndGlobalStatisticsWithoutInventingBookHistory() {
        val name="dashboard-migration.db";val ctx=ApplicationProvider.getApplicationContext<Context>();ctx.deleteDatabase(name)
        val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),WritingDatabase::class.java)
        helper.createDatabase(name,5).apply {
            execSQL("INSERT INTO spaces(id,kind,account_id) VALUES('s','GUEST',NULL)")
            execSQL("INSERT INTO books(id,space_id,title,author,description,created_at,updated_at,deleted_at,deletion_batch_id,local_seq,synced_local_seq,remote_version,local_dirty) VALUES('b','s','书','','','t','t',NULL,NULL,3,0,NULL,1)")
            execSQL("INSERT INTO volumes(id,book_id,title,position,created_at,updated_at,deleted_at,deletion_batch_id) VALUES('v','b','卷',0,'t','t',NULL,NULL)")
            execSQL("INSERT INTO chapters(id,book_id,volume_id,title,body,position,edit_seq,word_count,created_at,updated_at,deleted_at,deletion_batch_id) VALUES('c','b','v','章','保留原文😀',0,7,5,'t','t',NULL,NULL)")
            execSQL("INSERT INTO writing_days(day,net_words,edits) VALUES('2026-11-02',123,2)")
            execSQL("INSERT INTO planning_entries(id,book_id,chapter_id,volume_id,kind,title,body,updated_at,version) VALUES('p','b','c',NULL,'CHAPTER_OUTLINE','细纲','保留细纲','t',1)")
            close()
        }
        helper.runMigrationsAndValidate(name,6,true,Migrations.MIGRATION_5_6).use { db ->
            db.query("SELECT body,edit_seq FROM chapters").use { c->c.moveToFirst();assertEquals("保留原文😀",c.getString(0));assertEquals(7L,c.getLong(1)) }
            db.query("SELECT body FROM planning_entries").use { c->c.moveToFirst();assertEquals("保留细纲",c.getString(0)) }
            db.query("SELECT net_words FROM writing_days").use { c->c.moveToFirst();assertEquals(123L,c.getLong(0)) }
            db.query("SELECT COUNT(*) FROM book_writing_days").use { c->c.moveToFirst();assertEquals(0,c.getInt(0)) }
        };ctx.deleteDatabase(name)
    }
    private class Fixture(val db: WritingDatabase) {
        var next=0;val ids={"dashboard-${next++}"};val now={"2026-11-02T00:00:00Z"}
        val writing=RoomWritingRepository(db,ids,now);val progress=WritingProgressRepository(db)
    }
    private fun fixture(block: suspend(Fixture)->Unit): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),WritingDatabase::class.java).build()
        try {db.spaceDao().insert(SpaceEntity(GuestSpace.ID,GuestSpace.KIND_GUEST,null));block(Fixture(db))} finally {db.close()}
    }
    @Test fun perBookNetUsesOnlyCommittedEditorWritesAndNeverDoubleCountsRetries()=fixture { f->
        val a=f.writing.createBook(GuestSpace.ID,"甲书","","");val b=f.writing.createBook(GuestSpace.ID,"乙书","","")
        f.writing.saveDraft(ChapterDraft(a.id,a.title,"甲乙",1)) // imported/programmatic
        assertTrue(f.progress.bookDays().first().isEmpty())
        f.writing.saveDraft(ChapterDraft(a.id,a.title,"甲乙丙",2,true))
        f.writing.saveDraft(ChapterDraft(a.id,a.title,"不应覆盖",2,true))
        f.writing.saveDraft(ChapterDraft(b.id,b.title,"丁戊",1,true))
        val rows=f.progress.bookDays().first()
        assertEquals(1L,rows.first{it.bookId==a.bookId}.netWords);assertEquals(1L,rows.first{it.bookId==a.bookId}.edits)
        assertEquals(2L,rows.first{it.bookId==b.bookId}.netWords)
        f.writing.saveDraft(ChapterDraft(a.id,a.title,"甲",3,true))
        assertEquals(-1L,f.progress.bookDays().first().first{it.bookId==a.bookId}.netWords)
    }
    @Test fun bookGoalPersistsInSingleBackupAndRestoresWithoutDuplicatingActivity()=fixture { f->
        val a=f.writing.createBook(GuestSpace.ID,"目标书","","")
        f.writing.saveDraft(ChapterDraft(a.id,a.title,"甲乙丙",1,true));f.progress.setGoal(a.bookId,3000)
        val originalDays=f.progress.bookDays().first()
        val repository=RoomBackupRepository(f.db,f.ids,f.now);val single=repository.captureBook(a.bookId)
        assertEquals(3000L,single.books.single().targetWords)
        val output=ByteArrayOutputStream();BackupCodec().write(single,output)
        repository.restoreCopies(BackupCodec().read(ByteArrayInputStream(output.toByteArray())))
        assertEquals(2,f.progress.goals().first().count{it.targetWords==3000L})
        assertEquals(originalDays,f.progress.bookDays().first())
        f.progress.setGoal(a.bookId,0);assertEquals(0L,f.progress.goals().first().first{it.bookId==a.bookId}.targetWords)
    }
    @Test fun invalidOrDeletedBookGoalDoesNotChangeStoredValue()=fixture { f->
        val a=f.writing.createBook(GuestSpace.ID,"校验书","","");f.progress.setGoal(a.bookId,100)
        try {f.progress.setGoal(a.bookId,10_000_001);fail("range")}catch(_:IllegalArgumentException){}
        assertEquals(100L,f.progress.goals().first().single().targetWords)
        f.writing.softDeleteBook(a.bookId)
        try {f.progress.setGoal(a.bookId,200);fail("deleted")}catch(_:IllegalStateException){}
        assertEquals(100L,f.db.bookGoalDao().capture(GuestSpace.ID).single().targetWords)
    }
}
