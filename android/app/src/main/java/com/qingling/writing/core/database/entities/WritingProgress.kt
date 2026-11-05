package com.qingling.writing.core.database.entities

import androidx.room.*

@Entity(tableName = "chapter_details", foreignKeys = [ForeignKey(entity = ChapterEntity::class,
    parentColumns = ["id"], childColumns = ["chapter_id"], onDelete = ForeignKey.CASCADE)])
data class ChapterDetailsEntity(@PrimaryKey @ColumnInfo(name = "chapter_id") val chapterId: String,
    val completed: Boolean)

@Entity(tableName = "writing_days")
data class WritingDayEntity(@PrimaryKey val day: String, @ColumnInfo(name = "net_words") val netWords: Long, val edits: Long)
