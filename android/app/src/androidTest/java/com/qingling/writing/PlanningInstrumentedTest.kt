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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.*

class PlanningInstrumentedTest {
    @Test fun migrationFourToFivePreservesBodyCompletionAndStatistics() {
        val name = "planning-migration.db"
        val ctx = ApplicationProvider.getApplicationContext<Context>();ctx.deleteDatabase(name)
        val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),WritingDatabase::class.java)
        helper.createDatabase(name,4).apply {
            execSQL("INSERT INTO spaces(id,kind,account_id) VALUES('s','GUEST',NULL)")
            execSQL("INSERT INTO books(id,space_id,title,author,description,created_at,updated_at,deleted_at,deletion_batch_id,local_seq,synced_local_seq,remote_version,local_dirty) VALUES('b','s','书','','','t','t',NULL,NULL,3,0,NULL,1)")
            execSQL("INSERT INTO volumes(id,book_id,title,position,created_at,updated_at,deleted_at,deletion_batch_id) VALUES('v','b','卷',0,'t','t',NULL,NULL)")
            execSQL("INSERT INTO chapters(id,book_id,volume_id,title,body,position,edit_seq,word_count,created_at,updated_at,deleted_at,deletion_batch_id) VALUES('c','b','v','章','原文😀',0,7,3,'t','t',NULL,NULL)")
            execSQL("INSERT INTO chapter_details(chapter_id,completed) VALUES('c',1)")
            execSQL("INSERT INTO writing_days(day,net_words,edits) VALUES('2026-11-02',18,4)")
            close()
        }
        helper.runMigrationsAndValidate(name,5,true,Migrations.MIGRATION_4_5).use { db ->
            db.query("SELECT body,edit_seq FROM chapters WHERE id='c'").use { c -> assertTrue(c.moveToFirst());assertEquals("原文😀",c.getString(0));assertEquals(7L,c.getLong(1)) }
            db.query("SELECT completed FROM chapter_details").use { c -> c.moveToFirst();assertEquals(1,c.getInt(0)) }
            db.query("SELECT net_words FROM writing_days").use { c -> c.moveToFirst();assertEquals(18,c.getInt(0)) }
        }
        ctx.deleteDatabase(name)
    }
    @Test fun planningGuardsStaleWritesAndConvertsNotesAtomicallyWithoutWritingStats() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),WritingDatabase::class.java).build()
        try {
            var seq = 0; val ids = { "plan-${seq++}" };val now = { "2026-11-02T00:00:00Z" }
            db.spaceDao().insert(SpaceEntity(GuestSpace.ID,GuestSpace.KIND_GUEST,null))
            val w = RoomWritingRepository(db,ids,now);val o = RoomOutlineRepository(db,ids,now)
            val repo = PlanningRepository(db,o,w,ids,now)
            val c = w.createBook(GuestSpace.ID,"原书","","")
            val foreign = w.createBook(GuestSpace.ID,"另一本","","")
            val initial = repo.scoped(c.bookId,PlanningKind.INSPIRATION)
            val first = repo.save(initial.copy(title = "  灵感  ",body = "保留😀\n\n原始空格  "))
            val latest = repo.save(first.copy(body = "新版😀\n  "))
            try { repo.save(first.copy(body = "过期覆盖"));fail("must reject stale write") } catch (_: PlanningConflict) { }
            assertEquals(latest,repo.load(first.id))
            assertEquals(latest.id,repo.search(c.bookId,"新版😀",PlanningKind.INSPIRATION.name).first().single().id)
            assertTrue(repo.search(c.bookId,"%","").first().isEmpty()) // Search wildcards are literal.
            assertTrue(repo.search(foreign.bookId,"新版","").first().isEmpty())
            try { repo.toChapter(first,c.volumeId);fail("stale conversion") } catch (_: PlanningConflict) { }
            try { repo.toChapter(latest,foreign.volumeId);fail("cross book") } catch (_: com.qingling.writing.domain.NotFoundException) { }
            assertEquals(1,o.observeOutline(c.bookId).first().single().chapters.size)
            val copy = repo.toChapter(latest,c.volumeId)
            assertEquals(latest.body,copy.body);assertEquals(latest,repo.load(latest.id))
            assertTrue(db.progressDao().days().first().isEmpty())
            val bad = repo.scoped(c.bookId,PlanningKind.CHAPTER_OUTLINE,chapterId = foreign.id)
            try { repo.save(bad.copy(body = "跨书"));fail("cross book outline") } catch (_: IllegalArgumentException) { }
            assertEquals(2,o.observeOutline(c.bookId).first().single().chapters.size)
        } finally { db.close() }
    }
    @Test fun backupRestoresPlanningWithFreshIdsAndDeletedOwnersHideUntilRestored() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),WritingDatabase::class.java).build()
        try {
            var seq = 0;val ids = { "round-${seq++}" };val now = { "2026-11-02T00:00:00Z" }
            db.spaceDao().insert(SpaceEntity(GuestSpace.ID,GuestSpace.KIND_GUEST,null))
            val w = RoomWritingRepository(db,ids,now);val o = RoomOutlineRepository(db,ids,now);val p = PlanningRepository(db,o,w,ids,now)
            val c = w.createBook(GuestSpace.ID,"备份书","","")
            for (kind in PlanningKind.entries) p.save(p.scoped(c.bookId,kind,
                chapterId = c.id.takeIf { kind == PlanningKind.CHAPTER_OUTLINE },
                volumeId = c.volumeId.takeIf { kind == PlanningKind.VOLUME_SUMMARY }).copy(body = "${kind.name}😀\n  "))
            val tree = o.observeOutline(c.bookId).first().single()
            assertTrue(tree.summary.startsWith("VOLUME_SUMMARY"));assertTrue(tree.chapters.single().outline.startsWith("CHAPTER_OUTLINE"))
            val backup = RoomBackupRepository(db,ids,now)
            val captured = backup.capture();assertEquals(8,captured.planning.size)
            val bytes = ByteArrayOutputStream();BackupCodec().write(captured,bytes)
            backup.restoreCopies(BackupCodec().read(ByteArrayInputStream(bytes.toByteArray())))
            val all = db.planningDao().capture(GuestSpace.ID);assertEquals(16,all.size)
            val restored = all.filter { it.bookId != c.bookId };assertEquals(8,restored.size)
            assertTrue(restored.none { it.id in captured.planning.map { it.id } })
            assertTrue(restored.filter { it.chapterId != null }.all { it.chapterId != c.id })
            assertEquals(captured.planning.map { it.body }.sorted(),restored.map { it.body }.sorted())
            o.softDeleteChapter(c.id)
            assertEquals(7,p.observe(c.bookId).first().size)
            assertEquals(16,backup.capture().planning.size) // Soft-deleted outline stays in backup.
            val recycle = RoomRecycleBinRepository(db)
            val deletion = recycle.observeRecycleBin(GuestSpace.ID).first().filter { it.bookId == c.bookId }
            val plan = recycle.preparePurge(GuestSpace.ID,deletion)
            assertEquals(1,plan.planningIds.size)
            recycle.purge(plan)
            assertEquals(15,backup.capture().planning.size)
            assertEquals(8,db.planningDao().capture(GuestSpace.ID).count { it.bookId != c.bookId })
            try { RoomBackupRepository(db,ids,now,captureLimitBytes = 1).capture();fail("size guard") } catch (_: IllegalArgumentException) { }
        } finally { db.close() }
    }
}
