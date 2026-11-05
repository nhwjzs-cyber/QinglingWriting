package com.qingling.writing.core.database.dao

import androidx.room.*
import com.qingling.writing.core.database.entities.*
import kotlinx.coroutines.flow.Flow

@Dao interface BookGoalDao {
    @Query("SELECT g.* FROM book_goals g JOIN books b ON b.id=g.book_id WHERE b.deleted_at IS NULL")
    fun goals(): Flow<List<BookGoalEntity>>
    @Query("SELECT d.* FROM book_writing_days d JOIN books b ON b.id=d.book_id WHERE b.deleted_at IS NULL ORDER BY d.day")
    fun days(): Flow<List<BookWritingDayEntity>>
    @Query("SELECT g.* FROM book_goals g JOIN books b ON b.id=g.book_id WHERE b.space_id=:space AND (:bookId IS NULL OR b.id=:bookId)")
    suspend fun capture(space: String,bookId: String? = null): List<BookGoalEntity>
    @Upsert suspend fun setGoal(goal: BookGoalEntity)
    @Query("SELECT 'goal:' || book_id || ':' || target_words FROM book_goals ORDER BY book_id")
    fun versions(): Flow<List<String>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertDay(day: BookWritingDayEntity)
    @Query("UPDATE book_writing_days SET net_words=net_words+:delta,edits=edits+1 WHERE book_id=:book AND day=:day")
    suspend fun increment(book: String,day: String,delta: Long)
    @Transaction suspend fun record(book: String,day: String,delta: Long) {
        insertDay(BookWritingDayEntity(book,day,0,0));increment(book,day,delta)
    }
}
