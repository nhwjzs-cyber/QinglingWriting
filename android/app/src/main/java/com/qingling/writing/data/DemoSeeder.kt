package com.qingling.writing.data

import android.content.Context
import com.qingling.writing.R
import com.qingling.writing.core.database.WritingDatabase
import com.qingling.writing.core.model.GuestSpace
import com.qingling.writing.domain.repository.OutlineRepository
import com.qingling.writing.domain.repository.WritingRepository
import com.qingling.writing.domain.validation.WordCounter
import kotlinx.coroutines.flow.first

/**
 * 演示数据种子(仅 debug 构建调用,docs/05 §5:演示数据不进 release)。
 * 与 M0 内存演示不同,M1 起演示书是真实的 Room 行——正文本就在本地数据库里,
 * 因此它不再"演示持久化",而是持久化了的演示内容:书名一律「演示·」前缀以便识别。
 * release 构建永远不会调用本类,书架为空态(docs/01 §3.1)。
 */
internal object DemoSeeder {

    suspend fun seedIfEmpty(writing: WritingRepository, outline: OutlineRepository, db: WritingDatabase, context: Context) {
        if (db.bookDao().countInSpace(GuestSpace.ID) > 0) return

        val book1 = writing.createBook(
            GuestSpace.ID,
            context.getString(R.string.demo_book_title_1),
            author = "",
            description = "",
        )
        // 把默认「第一章」重命名为有内容的演示标题(走真实重命名链路)。
        outline.renameChapter(book1.id, context.getString(R.string.demo_chapter_title_1_1))
        val book2 = writing.createBook(
            GuestSpace.ID,
            context.getString(R.string.demo_book_title_2),
            author = "",
            description = "",
        )
        outline.renameChapter(book2.id, context.getString(R.string.demo_chapter_title_2_1))
        val book3 = writing.createBook(
            GuestSpace.ID,
            context.getString(R.string.demo_book_title_3),
            author = "",
            description = "",
        )
        outline.renameChapter(book3.id, context.getString(R.string.demo_chapter_title_3_1))
        val chapter1_2 = outline.createChapter(
            book1.bookId,
            book1.volumeId,
            context.getString(R.string.demo_chapter_title_1_2),
        )

        seedBody(db, book1.id, context.getString(R.string.demo_chapter_body_1_1))
        seedBody(db, chapter1_2.id, context.getString(R.string.demo_chapter_body_1_2))
        seedBody(db, book2.id, context.getString(R.string.demo_chapter_body_2_1))
        seedBody(db, book3.id, context.getString(R.string.demo_chapter_body_3_1))
    }

    /** 写入演示正文并按口径计字数(仅 debug;正式正文写入是 M2 的 SaveCoordinator)。 */
    private suspend fun seedBody(db: WritingDatabase, chapterId: String, body: String) {
        db.outlineDao().seedChapterBodyForDemo(chapterId, body, WordCounter.count(body))
    }
}
