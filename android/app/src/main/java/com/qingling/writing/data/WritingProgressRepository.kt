package com.qingling.writing.data
import androidx.room.withTransaction
import com.qingling.writing.core.database.WritingDatabase
import com.qingling.writing.core.database.entities.ChapterDetailsEntity

class WritingProgressRepository(private val db: WritingDatabase) {
    fun chapterInfo(chapterId:String) = kotlinx.coroutines.flow.combine(
        db.outlineDao().observeChapterById(chapterId),db.progressDao().observeDetails()
    ) { chapter,details -> chapter?.let {
        com.qingling.writing.core.model.ChapterInfo(it.createdAt,it.updatedAt,details.firstOrNull { d -> d.chapterId==chapterId }?.completed ?: false)
    } }
    fun days() = db.progressDao().days()
    fun inputDays() = db.inputMetricDao().days()
    fun bookDays() = db.bookGoalDao().days()
    fun goals() = db.bookGoalDao().goals()
    suspend fun setGoal(bookId: String,words: Long) = db.withTransaction {
        require(words in 0..10_000_000) { "请输入 0 到 10000000 的整数，0 为关闭目标" }
        val book = checkNotNull(db.bookDao().byId(bookId)) { "作品已不存在" }
        check(book.deletedAt == null && book.spaceId == com.qingling.writing.core.model.GuestSpace.ID) { "作品已不可编辑" }
        db.bookGoalDao().setGoal(com.qingling.writing.core.database.entities.BookGoalEntity(bookId,words))
        check(db.bookDao().bumpLocalSeq(bookId,java.time.Instant.now().toString()) == 1)
    }
    suspend fun complete(chapterId: String, value: Boolean) = db.withTransaction {
        val chapter = checkNotNull(db.outlineDao().chapterById(chapterId))
        check(chapter.deletedAt == null)
        db.progressDao().setDetails(ChapterDetailsEntity(chapterId,value))
        check(db.bookDao().bumpLocalSeq(chapter.bookId, java.time.Instant.now().toString()) == 1)
    }
}
