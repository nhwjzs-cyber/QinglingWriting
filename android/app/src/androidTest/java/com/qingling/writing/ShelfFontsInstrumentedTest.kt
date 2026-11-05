package com.qingling.writing
import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.qingling.writing.core.database.WritingDatabase
import com.qingling.writing.core.database.entities.SpaceEntity
import com.qingling.writing.core.database.migrations.Migrations
import com.qingling.writing.core.files.*
import com.qingling.writing.core.model.*
import com.qingling.writing.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.util.UUID

class ShelfFontsInstrumentedTest {
    @Test fun migrationSevenToEightPreservesBodyGoalAndMetrics() {
        val name="shelf-migration.db";val ctx=ApplicationProvider.getApplicationContext<Context>();ctx.deleteDatabase(name)
        val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),WritingDatabase::class.java)
        helper.createDatabase(name,7).apply {
            execSQL("INSERT INTO spaces(id,kind,account_id) VALUES('s','GUEST',NULL)")
            execSQL("INSERT INTO books(id,space_id,title,author,description,created_at,updated_at,deleted_at,deletion_batch_id,local_seq,synced_local_seq,remote_version,local_dirty) VALUES('b','s','书','','','t','t',NULL,NULL,3,0,NULL,1)")
            execSQL("INSERT INTO volumes(id,book_id,title,position,created_at,updated_at,deleted_at,deletion_batch_id) VALUES('v','b','卷',0,'t','t',NULL,NULL)")
            execSQL("INSERT INTO chapters(id,book_id,volume_id,title,body,position,edit_seq,word_count,created_at,updated_at,deleted_at,deletion_batch_id) VALUES('c','b','v','章','保留原文😀',0,7,5,'t','t',NULL,NULL)")
            execSQL("INSERT INTO book_goals(book_id,target_words) VALUES('b',5000)")
            execSQL("INSERT INTO input_metric_days(day,inserted_words,active_millis) VALUES('2026-11-02',176,39479)")
            close()
        }
        helper.runMigrationsAndValidate(name,8,true,Migrations.MIGRATION_7_8).use {db->
            db.query("SELECT body FROM chapters").use {it.moveToFirst();assertEquals("保留原文😀",it.getString(0))}
            db.query("SELECT target_words FROM book_goals").use {it.moveToFirst();assertEquals(5000L,it.getLong(0))}
            db.query("SELECT inserted_words FROM input_metric_days").use {it.moveToFirst();assertEquals(176L,it.getLong(0))}
            db.query("SELECT COUNT(*) FROM shelf_flags").use {it.moveToFirst();assertEquals(0,it.getInt(0))}
        };ctx.deleteDatabase(name)
    }
    @Test fun archiveHidesAutoResumeKeepsManuscriptAndSurvivesBackupAsFreshCopy()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),WritingDatabase::class.java).build()
        try {
            var n=0;val ids={"shelf-${n++}"};val now={"2026-11-02T00:00:00Z"}
            db.spaceDao().insert(SpaceEntity(GuestSpace.ID,GuestSpace.KIND_GUEST,null))
            val w=RoomWritingRepository(db,ids,now);val c=w.createBook(GuestSpace.ID,"作品","C","")
            w.saveDraft(ChapterDraft(c.id,c.title,"原稿😀",1));w.recordActive(c.id)
            val time=db.bookDao().byId(c.bookId)!!.updatedAt
            w.pinBook(c.bookId,true);w.archiveBook(c.bookId,true)
            assertEquals(time,db.bookDao().byId(c.bookId)!!.updatedAt)
            assertNull(w.resolveLastActiveChapter());assertEquals(c.id,w.resolveBookActiveChapter(c.bookId))
            assertEquals("原稿😀",w.readChapter(c.id).body)
            val summary=w.observeBooks(GuestSpace.ID).first().single();assertTrue(summary.pinned);assertTrue(summary.archived);assertEquals("C",summary.author)
            val backups=RoomBackupRepository(db,ids,now);val captured=backups.captureBook(c.bookId)
            val output=ByteArrayOutputStream();BackupCodec().write(captured,output);backups.restoreCopies(BackupCodec().read(ByteArrayInputStream(output.toByteArray())))
            assertEquals(2,w.observeBooks(GuestSpace.ID).first().count{it.pinned && it.archived})
            w.archiveBook(c.bookId,false);assertEquals(c.id,w.resolveLastActiveChapter())
            w.softDeleteBook(c.bookId)
            try{w.pinBook(c.bookId,false);fail("deleted")}catch(_:com.qingling.writing.domain.NotFoundException){}
        } finally {db.close()}
    }
    @Test fun realFontImportDeduplicatesLoadsAndRemovesFromIsolatedStore()=runBlocking {
        val ctx=ApplicationProvider.getApplicationContext<Context>();val root=File(ctx.cacheDir,"font-test-${UUID.randomUUID()}")
        val store=ImportedFontStore(ctx,root)
        try {
            val font=File("/system/fonts").listFiles().orEmpty().first {f->f.extension=="ttf" && f.length()<=FontFileValidator.MAX_BYTES && runCatching {FontFileValidator.validate(f.readBytes())}.isSuccess}
            val id=store.import(Uri.fromFile(font));assertEquals(64,id.length);assertEquals(1,store.fonts.value.size);assertNotNull(store.load(id))
            assertEquals(id,store.import(Uri.fromFile(font)));assertEquals(1,store.fonts.value.size)
            assertNull(store.load("../outside"))
            store.remove(id);assertTrue(store.fonts.value.isEmpty());assertNull(store.load(id))
        } finally {root.listFiles().orEmpty().forEach{it.delete()};root.delete()}
    }
    @Test fun invalidFontDoesNotReplaceExistingSelectionOrLeavePendingFile()=runBlocking {
        val ctx=ApplicationProvider.getApplicationContext<Context>();val root=File(ctx.cacheDir,"font-invalid-${UUID.randomUUID()}")
        val invalid=File(ctx.cacheDir,"invalid-${UUID.randomUUID()}.ttf").apply {writeText("not a font")}
        try {
            val store=ImportedFontStore(ctx,root)
            try{store.import(Uri.fromFile(invalid));fail("invalid")}catch(_:IllegalArgumentException){}
            assertTrue(store.fonts.value.isEmpty());assertTrue(root.listFiles().orEmpty().isEmpty())
        } finally {invalid.delete();root.listFiles().orEmpty().forEach{it.delete()};root.delete()}
    }
}
