package com.qingling.writing
import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.qingling.writing.core.database.WritingDatabase
import com.qingling.writing.core.database.migrations.Migrations
import com.qingling.writing.core.database.entities.SpaceEntity
import com.qingling.writing.core.files.BackupCodec
import com.qingling.writing.core.model.*
import com.qingling.writing.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.util.Base64
class Design036DataTest {
    @Test fun migrationEightToNinePreservesManuscriptAndRecoversHiddenInspiration() {
        val ctx=ApplicationProvider.getApplicationContext<Context>();val name="design036-migration.db";ctx.deleteDatabase(name)
        val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),WritingDatabase::class.java)
        val old="〔轻灵速记·1〕"+listOf("c","旧书 · 第一章","2","true","false","false").joinToString("|"){Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray())}+"\n旧伏笔😀  "
        helper.createDatabase(name,8).apply {
            execSQL("INSERT INTO spaces VALUES('s','GUEST',NULL)")
            for((id,deleted,batch)in listOf(Triple("b",null,null),Triple("gone","2026-11-03T00:00:00Z","batch")))execSQL("INSERT INTO books(id,space_id,title,author,description,created_at,updated_at,deleted_at,deletion_batch_id,local_seq,synced_local_seq,remote_version,local_dirty,last_active_chapter_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",arrayOf<Any?>(id,"s","旧书","作者","简介","2026-11-01T00:00:00Z","2026-11-03T00:00:00Z",deleted,batch,17,3,"remote",1,if(id=="b")"c"else null))
            execSQL("INSERT INTO volumes VALUES('v','b','第一卷',0,'t','t',NULL,NULL)")
            execSQL("INSERT INTO chapters VALUES('c','b','v','第一章','原稿😀  ',0,42,4,'t','t',NULL,NULL)")
            execSQL("INSERT INTO planning_entries VALUES('p','b',NULL,NULL,'INSPIRATION','旧伏笔',?,'2026-11-01T00:00:00Z',4)",arrayOf<Any?>(old))
            execSQL("INSERT INTO planning_entries VALUES('hidden','gone',NULL,NULL,'INSPIRATION','被隐藏的便签','旧灵感','2026-11-01T00:00:00Z',2)")
            close()
        }
        helper.runMigrationsAndValidate(name,9,true,Migrations.MIGRATION_8_9).use{db->
            db.query("SELECT body,edit_seq FROM chapters WHERE id='c'").use{assertTrue(it.moveToFirst());assertEquals("原稿😀  ",it.getString(0));assertEquals(42L,it.getLong(1))}
            db.query("SELECT local_seq,synced_local_seq,remote_version FROM books WHERE id='b'").use{it.moveToFirst();assertEquals(17L,it.getLong(0));assertEquals(3L,it.getLong(1));assertEquals("remote",it.getString(2))}
            db.query("SELECT body,book_id FROM planning_entries WHERE id='p'").use{it.moveToFirst();assertEquals(old,it.getString(0));assertEquals("b",it.getString(1))}
            db.query("SELECT body,book_id,version FROM planning_entries WHERE id='hidden'").use{it.moveToFirst();assertEquals("旧灵感",it.getString(0));assertTrue(it.isNull(1));assertEquals(3L,it.getLong(2))}
        }
        ctx.deleteDatabase(name)
    }
    @Test fun ownerMovesAreVersionedAndBookDeletionMustMigrateFirst()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),WritingDatabase::class.java).build()
        try {
            var n=0;val ids={"scope-"+n++};val now={"2026-11-03T00:00:00Z"}
            db.spaceDao().insert(SpaceEntity(GuestSpace.ID,GuestSpace.KIND_GUEST,null))
            val w=RoomWritingRepository(db,ids,now);val o=RoomOutlineRepository(db,ids,now);val p=PlanningRepository(db,o,w,ids,now)
            val a=w.createBook(GuestSpace.ID,"甲书","","");val b=w.createBook(GuestSpace.ID,"乙书","","")
            val first=p.capture("旧灯塔😀",a.bookId,a.id,"甲书 · 第一章",4,true)
            val general=p.capture("独立于作品的灵感",bookId="")
            val moved=p.moveNote(first,b.bookId)
            assertEquals(first.id,moved.id);assertEquals(first.body,moved.body)
            try{p.moveNote(first,"");fail("stale move")}catch(_:PlanningConflict){}
            try{w.softDeleteBook(b.bookId);fail("must choose migration destination")}catch(_:IllegalStateException){}
            assertNotNull(w.readChapter(b.id))
            p.moveNotesAndDeleteBook(b.bookId,"")
            val after=p.load(moved.id)!!;assertEquals("",after.bookId);assertEquals(2,p.inbox().first().size)
            assertNotNull(db.bookDao().byId(b.bookId)!!.deletedAt)
            try{p.saveNoteWithOwner(after,"不应写入的内容",b.bookId);fail("deleted target")}catch(_:IllegalArgumentException){}
            assertEquals(after,p.load(after.id));assertEquals(general,p.load(general.id))
        }finally{db.close()}
    }
    @Test fun generalOnlyBackupRoundTripAndVersionFiveCompatibility()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),WritingDatabase::class.java).build()
        try {
            var n=0;val ids={"backup-"+n++};val now={"2026-11-03T00:00:00Z"}
            val w=RoomWritingRepository(db,ids,now);val o=RoomOutlineRepository(db,ids,now);val p=PlanningRepository(db,o,w,ids,now)
            val first=p.capture("通用箱也要完整备份😀",bookId="")
            val backup=RoomBackupRepository(db,ids,now);val snapshot=backup.capture()
            assertTrue(snapshot.books.isEmpty());assertEquals(1,snapshot.planning.size)
            val bytes=ByteArrayOutputStream();BackupCodec().write(snapshot,bytes)
            backup.restoreCopies(BackupCodec().read(ByteArrayInputStream(bytes.toByteArray())))
            assertEquals(2,p.inbox().first().size);assertEquals(first.body,p.inbox().first().first{it.id!=first.id}.body)
            try{BackupCodec(writeVersion=5).write(snapshot,ByteArrayOutputStream());fail("old writer must reject general notes")}catch(_:IllegalArgumentException){}
            val chapter=w.createBook(GuestSpace.ID,"旧格式书","","");p.capture("旧格式便签",chapter.bookId)
            val old=ByteArrayOutputStream();BackupCodec(writeVersion=5).write(backup.captureBook(chapter.bookId),old)
            val decoded=BackupCodec().read(ByteArrayInputStream(old.toByteArray()));assertEquals(1,decoded.books.size);assertEquals(1,decoded.planning.size)
        }finally{db.close()}
    }
    @Test fun emptyAndAllDeletedBooksCanSeedExactlyOneLiveChapter()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),WritingDatabase::class.java).build()
        try{
            var n=0;val ids={"empty-"+n++};val now={"2026-11-03T00:00:00Z"}
            db.spaceDao().insert(SpaceEntity(GuestSpace.ID,GuestSpace.KIND_GUEST,null))
            val w=RoomWritingRepository(db,ids,now);val o=RoomOutlineRepository(db,ids,now)
            val book=w.createEmptyBook(GuestSpace.ID,"存量零章书","","")
            val both=coroutineScope{listOf(async{w.ensureFirstChapter(book)},async{w.ensureFirstChapter(book)}).awaitAll()}
            assertEquals(both[0].id,both[1].id);assertEquals(1,o.observeOutline(book).first().single().chapters.size)
            val first=both[0];w.saveDraft(ChapterDraft(first.id,first.title,"删除前原稿😀  ",1));o.softDeleteChapter(first.id)
            val next=w.ensureFirstChapter(book);assertNotEquals(first.id,next.id);assertEquals("",next.body)
            assertEquals("删除前原稿😀  ",db.outlineDao().chapterById(first.id)!!.body)
            assertEquals(1,o.observeOutline(book).first().single().chapters.size)
        }finally{db.close()}
    }
}
