package com.qingling.writing

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.qingling.writing.core.database.WritingDatabase
import com.qingling.writing.core.database.entities.SpaceEntity
import com.qingling.writing.core.files.BackupCodec
import com.qingling.writing.core.files.DocxWriter
import com.qingling.writing.core.model.*
import com.qingling.writing.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class DocumentExportInstrumentedTest {
    private class Fixture(val db: WritingDatabase) {
        var seq = 0;val ids = { "document-${seq++}" };val now = { "2026-11-02T00:00:00Z" }
        val writing = RoomWritingRepository(db,ids,now)
        val outline = RoomOutlineRepository(db,ids,now)
    }
    private fun fixture(block: suspend (Fixture) -> Unit): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),WritingDatabase::class.java).build()
        try { db.spaceDao().insert(SpaceEntity(GuestSpace.ID,GuestSpace.KIND_GUEST,null));block(Fixture(db)) }
        finally { db.close() }
    }
    @Test fun singleBookIncludesHistoryDeletedChildrenPlanningAndIgnoresLargeOtherBooks() = fixture { f ->
        val a = f.writing.createBook(GuestSpace.ID,"小书","","");val b = f.writing.createBook(GuestSpace.ID,"大书","","")
        f.writing.saveDraft(ChapterDraft(a.id,a.title,"保留😀",1))
        val large = "其他内容".repeat(10_000);f.writing.saveDraft(ChapterDraft(b.id,b.title,large,1))
        val deleted = f.outline.createChapter(a.bookId,a.volumeId,"被删章");f.outline.softDeleteChapter(deleted.id)
        RoomHistoryRepository(f.db,f.ids,f.now).createManualSnapshot(a.id)
        WritingProgressRepository(f.db).complete(a.id,true)
        PlanningRepository(f.db,f.outline,f.writing,f.ids,f.now).save(
            PlanningEntry("note",a.bookId,null,null,"INSPIRATION","便签","单书保留",f.now()))
        val size = f.db.backupDao().conservativeSize(GuestSpace.ID,a.bookId)
        val repository = RoomBackupRepository(f.db,f.ids,f.now,captureLimitBytes = size)
        try { repository.capture();fail("whole library must exceed this limit") } catch (_: IllegalArgumentException) { }
        val single = repository.captureBook(a.bookId)
        assertEquals(listOf(a.bookId),single.books.map { it.id });assertEquals(2,single.chapters.size)
        assertNotNull(single.chapters.first { it.id == deleted.id }.deletedAt)
        assertTrue(single.chapters.first { it.id == a.id }.completed)
        assertEquals("保留😀",single.revisions.single().body);assertEquals("单书保留",single.planning.single().body)
        val output = ByteArrayOutputStream();BackupCodec().write(single,output)
        repository.restoreCopies(BackupCodec().read(ByteArrayInputStream(output.toByteArray())))
        assertEquals(3,f.writing.observeBooks(GuestSpace.ID).first().size)
        assertEquals(large,f.writing.readChapter(b.id).body)
        try { repository.captureBook("missing");fail("missing scope must not back up all") } catch (_: IllegalArgumentException) { }
    }
    @Test fun selectedExportChunksKeepDirectoryOrderAndProduceReadableDocx() = fixture { f ->
        val a = f.writing.createBook(GuestSpace.ID,"书名<&","","")
        f.writing.saveDraft(ChapterDraft(a.id,a.title,"　甲&<😀\n\n乙\t尾  ",1))
        val ids = mutableListOf(a.id)
        f.db.withTransaction { repeat(204) { i -> ids += f.outline.createChapter(a.bookId,a.volumeId,"章-$i").id } }
        val repository = RoomExportRepository(f.db)
        val snapshot = repository.capture(ExportSelection(a.bookId,ids.reversed().toSet()))
        assertEquals(ids,snapshot.volumes.flatMap { it.chapters }.map { it.id })
        val bytes = DocxWriter.write(snapshot,true,true,true)
        var document: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip -> while(true) {
            val entry = zip.nextEntry ?: break;if(entry.name == "word/document.xml") document = zip.readBytes()
        } }
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val xml = factory.newDocumentBuilder().parse(ByteArrayInputStream(requireNotNull(document)))
        assertTrue(xml.documentElement.textContent.contains("甲&<😀"))
        assertTrue(xml.documentElement.textContent.indexOf("章-1") < xml.documentElement.textContent.indexOf("章-203"))
        try { repository.capture(ExportSelection(a.bookId,(1..1001).map { "fake-$it" }.toSet()));fail("chapter count limit") } catch (_: IllegalArgumentException) { }
    }
    @Test fun oversizedChapterIsRejectedBeforeMaterializingItsBody() = fixture { f ->
        val a = f.writing.createBook(GuestSpace.ID,"大稿","","")
        val body = "a".repeat(8*1024*1024+1)
        f.db.outlineDao().saveChapterBodyGuarded(a.id,body,body.length.toLong(),1,f.now())
        try { RoomExportRepository(f.db).capture(ExportSelection(a.bookId,setOf(a.id)));fail("body size guard") }
        catch (_: IllegalArgumentException) { }
    }
}
