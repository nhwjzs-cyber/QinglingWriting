package com.qingling.writing.core.database.dao
import androidx.room.*
import com.qingling.writing.core.database.entities.*
import kotlinx.coroutines.flow.Flow

@Dao interface ProgressDao {
    @Query("SELECT * FROM chapter_details") fun observeDetails(): Flow<List<ChapterDetailsEntity>>
    @Query("SELECT * FROM chapter_details") suspend fun details(): List<ChapterDetailsEntity>
    @Upsert suspend fun setDetails(value: ChapterDetailsEntity)
    @Query("SELECT * FROM writing_days ORDER BY day") fun days(): Flow<List<WritingDayEntity>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertDay(value: WritingDayEntity)
    @Query("UPDATE writing_days SET net_words=net_words+:delta, edits=edits+1 WHERE day=:day")
    suspend fun increment(day: String, delta: Long)
    @Transaction suspend fun record(day: String, delta: Long) {
        insertDay(WritingDayEntity(day,0,0)); increment(day,delta)
    }
}
