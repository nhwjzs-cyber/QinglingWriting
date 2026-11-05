package com.qingling.writing.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 书(local-schema.sql 的 books)。
 * 标题 CHECK(trim 非空/无 CR/LF)与删除配对 CHECK((deleted_at IS NULL) = (deletion_batch_id IS NULL))
 * 由 domain.validation 与数据层事务保证(Room 无任意 CHECK,差异在 M1 验收记录说明);
 * sync 四列(local_seq/synced_local_seq/remote_version/local_dirty)M1 不使用,M5 启用。
 */
@Entity(
    tableName = "books",
    indices = [
        Index(value = ["space_id", "updated_at"], name = "index_books_space_updated"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = SpaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["space_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
)
data class BookEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "space_id") val spaceId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "author") val author: String,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?,
    @ColumnInfo(name = "deletion_batch_id") val deletionBatchId: String?,
    @ColumnInfo(name = "local_seq") val localSeq: Long,
    @ColumnInfo(name = "synced_local_seq") val syncedLocalSeq: Long,
    @ColumnInfo(name = "remote_version") val remoteVersion: String?,
    @ColumnInfo(name = "local_dirty") val localDirty: Boolean,
    /** 每书最近激活章(UX v0.2;v3 迁移新增,创建书时由仓储写入)。 */
    @ColumnInfo(name = "last_active_chapter_id", defaultValue = "NULL") val lastActiveChapterId: String? = null,
    @ColumnInfo(name = "chapter_number_counter", defaultValue = "0") val chapterNumberCounter: Long = 0,
)
