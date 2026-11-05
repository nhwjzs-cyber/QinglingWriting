package com.qingling.writing
import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.qingling.writing.core.database.WritingDatabase
import com.qingling.writing.core.database.entities.SpaceEntity
import com.qingling.writing.core.database.migrations.Migrations
import com.qingling.writing.core.model.*
import com.qingling.writing.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class PartialCompletionInstrumentedTest {
    @Test fun migrationSixToSevenKeepsBookGoalsAndManuscript() {
        val name="partial-migration.db";val ctx=ApplicationProvider.getApplicationContext<Context>();ctx.deleteDatabase(name)
        val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),WritingDatabase::class.java)
        helper.createDatabase(name,6).apply {
            execSQL("INSERT INTO spaces(id,kind,account_id) VALUES('s','GUEST',NULL)")
            execSQL("INSERT INTO books(id,space_id,title,author,description,created_at,updated_at,deleted_at,deletion_batch_id,local_seq,synced_local_seq,remote_version,local_dirty) VALUES('b','s','书','','','t','t',NULL,NULL,3,0,NULL,1)")
            execSQL("INSERT INTO volumes(id,book_id,title,position,created_at,updated_at,deleted_at,deletion_batch_id) VALUES('v','b','卷',0,'t','t',NULL,NULL)")
            execSQL("INSERT INTO chapters(id,book_id,volume_id,title,body,position,edit_seq,word_count,created_at,updated_at,deleted_at,deletion_batch_id) VALUES('c','b','v','章','保留原文😀',0,7,5,'t','t',NULL,NULL)")
            execSQL("INSERT INTO book_goals(book_id,target_words) VALUES('b',5000)")
            execSQL("INSERT INTO book_writing_days(book_id,day,net_words,edits) VALUES('b','2026-11-02',6,3)")
            close()
        }
        helper.runMigrationsAndValidate(name,7,true,Migrations.MIGRATION_6_7).use { db ->
            db.query("SELECT body FROM chapters").use {c->c.moveToFirst();assertEquals("保留原文😀",c.getString(0))}
            db.query("SELECT target_words FROM book_goals").use {c->c.moveToFirst();assertEquals(5000L,c.getLong(0))}
            db.query("SELECT net_words FROM book_writing_days").use {c->c.moveToFirst();assertEquals(6L,c.getLong(0))}
            db.query("SELECT COUNT(*) FROM input_metric_days").use {c->c.moveToFirst();assertEquals(0,c.getInt(0))}
        };ctx.deleteDatabase(name)
    }
    private fun fixture(block: suspend(WritingDatabase,RoomWritingRepository,RoomOutlineRepository)->Unit)=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),WritingDatabase::class.java).build()
        var n=0;val ids={"partial-${n++}"};val now={"2026-11-02T00:00:00Z"}
        try { db.spaceDao().insert(SpaceEntity(GuestSpace.ID,GuestSpace.KIND_GUEST,null));block(db,RoomWritingRepository(db,ids,now),RoomOutlineRepository(db,ids,now)) } finally {db.close()}
    }
    @Test fun coalescedCumulativeMetricsRetryAndNewSessionNeverDoubleCount()=fixture {db,w,_->
        val c=w.createBook(GuestSpace.ID,"统计测试","","")
        w.saveDraft(ChapterDraft(c.id,c.title,"甲乙丙",3,true,InputMetrics(0,3,4000)))
        w.saveDraft(ChapterDraft(c.id,c.title,"甲乙丙",3,true,InputMetrics(0,3,4000)))
        w.saveDraft(ChapterDraft(c.id,c.title,"甲乙",4,true,InputMetrics(0,3,6000)))
        w.saveDraft(ChapterDraft(c.id,c.title,"甲乙丁",5,true,InputMetrics(4,1,0)))
        val row=db.inputMetricDao().days().first().single()
        assertEquals(4L,row.insertedWords);assertEquals(6000L,row.activeMillis)
        w.saveDraft(ChapterDraft(c.id,c.title,"程序替换",6))
        assertEquals(row,db.inputMetricDao().days().first().single())
    }
    @Test fun volumePositionAndCrossVolumeChapterPositionPersistWithoutGaps()=fixture {db,w,o->
        val c=w.createBook(GuestSpace.ID,"排序测试","","")
        o.createVolume(c.bookId,"第二卷");o.createVolume(c.bookId,"第三卷")
        val all=o.observeOutline(c.bookId).first();val second=all.first{it.title=="第二卷"};val third=all.first{it.title=="第三卷"}
        o.placeVolume(c.bookId,third.id,0)
        assertEquals(listOf(third.id,c.volumeId,second.id),o.observeOutline(c.bookId).first().map{it.id})
        assertEquals(listOf(0L,1L,2L),o.observeOutline(c.bookId).first().map{it.position})
        val end=o.createChapter(c.bookId,second.id,"末章")
        o.moveChapter(c.id,second.id,0)
        val rows=o.observeOutline(c.bookId).first().first{it.id==second.id}.chapters
        assertEquals(listOf(c.id,end.id),rows.map{it.id});assertEquals(listOf(0L,1L),rows.map{it.position})
        val other=w.createBook(GuestSpace.ID,"另书","","")
        try{o.placeVolume(other.bookId,third.id,0);fail("cross book")}catch(_:com.qingling.writing.domain.NotFoundException){}
        assertEquals(3,db.outlineDao().activeVolumes(c.bookId).size)
    }
}
