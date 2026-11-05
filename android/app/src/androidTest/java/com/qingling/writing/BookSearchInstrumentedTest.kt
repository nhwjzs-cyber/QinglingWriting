package com.qingling.writing

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qingling.writing.core.database.WritingDatabase
import com.qingling.writing.core.database.entities.SpaceEntity
import com.qingling.writing.core.model.GuestSpace
import com.qingling.writing.data.RoomOutlineRepository
import com.qingling.writing.data.RoomWritingRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookSearchInstrumentedTest {
    @Test fun searchIsLiteralOrderedAndExcludesDeletedAndOtherBooks() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, WritingDatabase::class.java).build()
        try {
            var id = 0
            val writing = RoomWritingRepository(db, { "test-${id++}" }, { "2026-11-01T00:00:00Z" })
            val outline = RoomOutlineRepository(db, { "test-${id++}" }, { "2026-11-01T00:00:00Z" })
            db.spaceDao().insert(SpaceEntity(GuestSpace.ID, GuestSpace.KIND_GUEST, null))
            val first = writing.createBook(GuestSpace.ID, "甲书", "", "")
            val second = outline.createChapter(first.bookId, first.volumeId, "正文命中")
            val deleted = outline.createChapter(first.bookId, first.volumeId, "被删 %_😀")
            val other = writing.createBook(GuestSpace.ID, "乙书", "", "")
            outline.renameChapter(first.id, "标题命中 %_😀")
            outline.renameChapter(other.id, "%_😀")
            db.outlineDao().saveChapterBodyGuarded(second.id, "上下文 %_😀 后文", 7, 1, "2026-11-01T00:00:00Z")
            outline.softDeleteChapter(deleted.id)
            val hits = db.outlineDao().searchBook(first.bookId, "%_😀")
            assertEquals(listOf(first.id, second.id), hits.map { it.chapterId })
            assertTrue(hits.last().snippet.contains("%_😀"))
            assertTrue(db.outlineDao().searchBook(first.bookId, "不存在").isEmpty())
            assertTrue(db.outlineDao().searchBook(first.bookId, "title").isEmpty())
            db.bookDao().softDelete(first.bookId, "2026-11-01T00:00:00Z", "batch")
            assertTrue(db.outlineDao().searchBook(first.bookId, "%_😀").isEmpty())
        } finally { db.close() }
    }
}
