package com.qingling.writing.data

import androidx.room.withTransaction
import com.qingling.writing.core.database.WritingDatabase
import com.qingling.writing.core.model.*
import com.qingling.writing.domain.repository.*

/** Import only creates a new book. Any failed chapter rolls the whole transaction back. */
class TxtImportRepository(private val db: WritingDatabase, private val writing: WritingRepository,
    private val outline: OutlineRepository) {
    suspend fun importBook(title: String, chapters: List<ImportedChapter>): String {
        require(chapters.isNotEmpty() && chapters.size <= TxtImport.MAX_CHAPTERS)
        require(chapters.sumOf { it.body.length.toLong() } <= TxtImport.MAX_BYTES)
        return db.withTransaction {
            val first = writing.createBook(GuestSpace.ID, TxtImport.title(title), "", "")
            chapters.forEachIndexed { index, chapter ->
                val doc = if (index == 0) first.also { outline.renameChapter(it.id, TxtImport.title(chapter.title, 120)) }
                    else outline.createChapter(first.bookId, first.volumeId, TxtImport.title(chapter.title, 120))
                check(writing.saveDraft(ChapterDraft(doc.id, chapter.title, chapter.body, 1)) is SaveResult.Committed) {
                    "保存失败，已取消整次导入，请检查剩余空间"
                }
            }
            first.bookId
        }
    }
}
