package com.qingling.writing.core.database.dao
import androidx.room.*
import com.qingling.writing.core.database.entities.*
import com.qingling.writing.core.model.InputMetrics
import kotlinx.coroutines.flow.Flow

@Dao interface InputMetricDao {
    @Query("SELECT * FROM input_metric_days ORDER BY day") fun days(): Flow<List<InputMetricDay>>
    @Query("SELECT * FROM input_metric_cursors WHERE chapter_id=:chapter") suspend fun cursor(chapter: String): InputMetricCursor?
    @Upsert suspend fun setCursor(value: InputMetricCursor)
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insertDay(value: InputMetricDay)
    @Query("UPDATE input_metric_days SET inserted_words=inserted_words+:words,active_millis=active_millis+:millis WHERE day=:day")
    suspend fun increment(day: String,words: Long,millis: Long)
    // Called only inside the accepted draft's existing transaction; cumulative watermark survives coalescing/retries.
    suspend fun record(chapter: String,day: String,value: InputMetrics) {
        require(value.inserted>=0 && value.activeMillis>=0)
        val previous=cursor(chapter)?.takeIf {it.baseSeq==value.baseSeq}
        val words=(value.inserted-(previous?.insertedWords ?: 0)).coerceAtLeast(0)
        val millis=(value.activeMillis-(previous?.activeMillis ?: 0)).coerceAtLeast(0)
        insertDay(InputMetricDay(day,0,0));increment(day,words,millis)
        setCursor(InputMetricCursor(chapter,value.baseSeq,value.inserted,value.activeMillis))
    }
}
