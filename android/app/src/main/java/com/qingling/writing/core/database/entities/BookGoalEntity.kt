package com.qingling.writing.core.database.entities

import androidx.room.*

@Entity(tableName = "book_goals",foreignKeys = [ForeignKey(entity = BookEntity::class,parentColumns = ["id"],childColumns = ["book_id"],onDelete = ForeignKey.CASCADE)])
data class BookGoalEntity(@PrimaryKey @ColumnInfo(name = "book_id") val bookId: String,@ColumnInfo(name = "target_words") val targetWords: Long)

@Entity(tableName = "book_writing_days",primaryKeys = ["book_id","day"],foreignKeys = [ForeignKey(entity = BookEntity::class,parentColumns = ["id"],childColumns = ["book_id"],onDelete = ForeignKey.CASCADE)])
data class BookWritingDayEntity(@ColumnInfo(name = "book_id") val bookId: String,val day: String,
    @ColumnInfo(name = "net_words") val netWords: Long,val edits: Long)
