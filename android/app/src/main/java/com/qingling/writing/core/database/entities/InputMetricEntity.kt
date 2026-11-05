package com.qingling.writing.core.database.entities
import androidx.room.*

@Entity(tableName="input_metric_days")
data class InputMetricDay(@PrimaryKey val day: String,@ColumnInfo(name="inserted_words") val insertedWords: Long,@ColumnInfo(name="active_millis") val activeMillis: Long)

@Entity(tableName="input_metric_cursors",foreignKeys=[ForeignKey(entity=ChapterEntity::class,parentColumns=["id"],childColumns=["chapter_id"],onDelete=ForeignKey.CASCADE)])
data class InputMetricCursor(@PrimaryKey @ColumnInfo(name="chapter_id") val chapterId: String,@ColumnInfo(name="base_seq") val baseSeq: Long,
    @ColumnInfo(name="inserted_words") val insertedWords: Long,@ColumnInfo(name="active_millis") val activeMillis: Long)
