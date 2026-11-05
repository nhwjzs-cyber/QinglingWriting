package com.qingling.writing.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 章(local-schema.sql 的 chapters)。
 * 复合外键 (volume_id, book_id) → volumes(id, book_id):把章钉死在「同书的卷」上,
 * 跨书卷章引用在数据库层被拒绝(docs/06 M1 通过条件);正文与 edit_seq 字段 M1 建列,
 * 写入链路(防旧写)M2 接入。
 */
@Entity(
    tableName = "chapters",
    indices = [
        Index(value = ["volume_id", "position", "id"], name = "index_chapters_volume_order"),
        Index(value = ["volume_id", "book_id"], name = "index_chapters_volume_book"),
        Index(value = ["book_id"], name = "index_chapters_book"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = VolumeEntity::class,
            parentColumns = ["id", "book_id"],
            childColumns = ["volume_id", "book_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
)
data class ChapterEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "volume_id") val volumeId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "body") val body: String,
    @ColumnInfo(name = "position") val position: Long,
    @ColumnInfo(name = "edit_seq") val editSeq: Long,
    @ColumnInfo(name = "word_count") val wordCount: Long,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?,
    @ColumnInfo(name = "deletion_batch_id") val deletionBatchId: String?,
    @ColumnInfo(name = "automatic_number", defaultValue = "0") val automaticNumber: Long = 0,
    @ColumnInfo(name = "title_locked", defaultValue = "1") val titleLocked: Boolean = true,
)
