package com.qingling.writing

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.qingling.writing.core.database.WritingDatabase
import com.qingling.writing.core.database.entities.SpaceEntity
import com.qingling.writing.core.model.GuestSpace
import com.qingling.writing.data.RoomHistoryRepository
import com.qingling.writing.data.RoomWritingRepository
import com.qingling.writing.data.StorageManager
import com.qingling.writing.core.database.entities.RevisionEntity
import com.qingling.writing.core.model.ChapterDraft
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryRetentionInstrumentedTest {
    @Test fun cleanupChecksPreviewAndKeepsOtherHistoryAndManuscript() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val db=Room.inMemoryDatabaseBuilder(context,WritingDatabase::class.java).build()
        try {
            var n=0;val ids={"cleanup-${n++}"};val time={"2026-01-01T00:00:00Z"}
            db.spaceDao().insert(SpaceEntity(GuestSpace.ID,GuestSpace.KIND_GUEST,null))
            val writing=RoomWritingRepository(db,ids,time)
            val chapter=writing.createBook(GuestSpace.ID,"测试","","")
            db.spaceDao().insert(SpaceEntity("another-space",GuestSpace.KIND_GUEST,null))
            val other=writing.createBook("another-space","另一空间","","")
            writing.saveDraft(ChapterDraft(chapter.id,chapter.title,"正文保留",1))
            suspend fun add(id: String,source: String,date: String) {
                db.revisionDao().insert(RevisionEntity(id,chapter.id,chapter.title,"历史内容", "hash",source,date))
            }
            repeat(25) {add("auto-$it","AUTO",Instant.parse("2026-01-01T00:00:00Z").plusSeconds(it.toLong()).toString())}
            repeat(25) {db.revisionDao().insert(RevisionEntity("other-$it",other.id,other.title,"其他空间", "hash","AUTO",Instant.parse("2026-01-01T00:00:00Z").plusSeconds(it.toLong()).toString()))}
            add("manual","MANUAL",time());add("before","BEFORE_RESTORE",time())
            val manager=StorageManager(context,db)
            val report=manager.report();assertEquals(27L,report.historyCount)
            assertEquals("正文保留".toByteArray(Charsets.UTF_8).size.toLong(),report.manuscriptBytes)
            val before=manager.preview(20);assertEquals((0..4).map {"auto-$it"},before.ids);assertTrue(before.estimatedBytes>0)
            add("new-auto","AUTO","2026-01-01T01:00:00Z")
            try {manager.apply(before);fail("stale preview")} catch(_: IllegalStateException) { }
            assertEquals(28,db.revisionDao().observeByChapter(chapter.id).first().size)
            val current=manager.preview(20);assertEquals(6,manager.apply(current))
            val rows=db.revisionDao().observeByChapter(chapter.id).first()
            assertEquals(20,rows.count{it.source=="AUTO"});assertEquals(1,rows.count{it.source=="MANUAL"})
            assertEquals(1,rows.count{it.source=="BEFORE_RESTORE"});assertEquals("正文保留",writing.readChapter(chapter.id).body)
            assertEquals(25,db.revisionDao().observeByChapter(other.id).first().size)
        } finally {db.close()}
    }
    @Test fun configuredRetentionTrimsOnlyAutomaticSnapshots() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),WritingDatabase::class.java).build()
        try {
            var number=0;val ids={"retention-${number++}"};var seconds=0L
            val now={Instant.parse("2026-01-01T00:00:00Z").plusSeconds(seconds).toString()}
            db.spaceDao().insert(SpaceEntity(GuestSpace.ID,GuestSpace.KIND_GUEST,null))
            val chapter=RoomWritingRepository(db,ids,now).createBook(GuestSpace.ID,"保留测试","","")
            var keep=20
            val history=RoomHistoryRepository(db,ids,now) {keep}
            history.createManualSnapshot(chapter.id)
            repeat(25) { seconds+=31;history.createAutoSnapshotIfDue(chapter.id,chapter.title,"稿$it") }
            var rows=db.revisionDao().observeByChapter(chapter.id).first()
            assertEquals(20,rows.count{it.source=="AUTO"});assertEquals(1,rows.count{it.source=="MANUAL"})
            keep=50
            repeat(21) { seconds+=31;history.createAutoSnapshotIfDue(chapter.id,chapter.title,"续$it") }
            rows=db.revisionDao().observeByChapter(chapter.id).first()
            assertEquals(41,rows.count{it.source=="AUTO"});assertEquals(1,rows.count{it.source=="MANUAL"})
        } finally {db.close()}
    }
}
