package com.qingling.writing.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 卷(local-schema.sql 的 volumes)。
 * UNIQUE(id, book_id) 是章表复合外键 (volume_id, book_id) 的引用目标——这是
 * 「章必属同书的卷」(docs/01 §4)的数据库级保证;RESTRICT 拒绝跨书引用。
 * 无 UNIQUE(position):临时同位次合法(local-schema.sql 尾注),重排不留空洞。
 */
@Entity(
    tableName = "volumes",
    indices = [
        Index(value = ["id", "book_id"], name = "index_volumes_id_book_id", unique = true),
        Index(value = ["book_id", "position", "id"], name = "index_volumes_book_order"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
)
data class VolumeEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "position") val position: Long,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?,
    @ColumnInfo(name = "deletion_batch_id") val deletionBatchId: String?,
)
