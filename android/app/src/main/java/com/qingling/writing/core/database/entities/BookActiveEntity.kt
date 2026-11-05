package com.qingling.writing.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * 每书最近激活章(UX v0.2 §1.1):点书即续写到该书最后激活的章。
 * 一书一行;章被删时由仓储层回退到同书首存活章。
 */
@Entity(
    tableName = "book_active",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
)
data class BookActiveEntity(
    @PrimaryKey @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "last_active_chapter_id") val lastActiveChapterId: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
)
