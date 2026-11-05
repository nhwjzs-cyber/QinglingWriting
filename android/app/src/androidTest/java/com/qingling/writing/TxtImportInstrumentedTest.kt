package com.qingling.writing

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qingling.writing.core.database.WritingDatabase
import com.qingling.writing.core.database.entities.SpaceEntity
import com.qingling.writing.core.model.*
import com.qingling.writing.data.*
import com.qingling.writing.domain.repository.WritingRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TxtImportInstrumentedTest {
    @Test fun importIsOrderedAndFailureRollsBackWholeBook() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, WritingDatabase::class.java).build()
        try {
            var counter = 0
            val writing = RoomWritingRepository(db, { "import-${counter++}" }, { "2026-11-01T00:00:00Z" })
            val outline = RoomOutlineRepository(db, { "import-${counter++}" }, { "2026-11-01T00:00:00Z" })
            db.spaceDao().insert(SpaceEntity(GuestSpace.ID, GuestSpace.KIND_GUEST, null))
            val chapters = listOf(ImportedChapter("第一章", "正文😀"), ImportedChapter("第二章", "另一段"))
            val book = TxtImportRepository(db, writing, outline).importBook("新书", chapters)
            val tree = outline.observeOutline(book).first()
            assertEquals(chapters.map { it.title }, tree.single().chapters.map { it.title })
            assertEquals(chapters.map { it.body }, tree.single().chapters.map { writing.readChapter(it.id).body })
            var writes = 0
            val failing = object : WritingRepository by writing {
                override suspend fun saveDraft(draft: ChapterDraft): SaveResult {
                    if (++writes == 2) return SaveResult.Failed(draft.chapterId, draft.editSeq, SaveFailure.DISK_FULL)
                    return writing.saveDraft(draft)
                }
            }
            try {
                TxtImportRepository(db, failing, outline).importBook("应回滚的书", chapters)
                fail("Expected rollback")
            } catch (_: IllegalStateException) { }
            assertEquals(1, db.bookDao().countInSpace(GuestSpace.ID))
            assertEquals(chapters.map { it.body }, tree.single().chapters.map { writing.readChapter(it.id).body })
        } finally { db.close() }
    }
}
