package com.qingling.writing.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 历史版本(local-schema.sql 的 revisions;M2 schema v2 落地)。
 * source 取值域与 SQL 参考 CHECK 一致;非法值由仓储层保证(Room 无法声明 CHECK,差异已声明)。
 * 删除章为软删除,FK RESTRICT 不会级联清历史——墓碑连同历史一起保留(docs/01 P06:删除保留 30 天可恢复)。
 */
@Entity(
    tableName = "revisions",
    indices = [
        Index(value = ["chapter_id", "created_at"], name = "index_revisions_chapter_time"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapter_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
)
data class RevisionEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "chapter_id") val chapterId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "body") val body: String,
    /** 正文 SHA-256,十六进制小写,UTF-8。 */
    @ColumnInfo(name = "content_sha256") val contentSha256: String,
    /** AUTO / MANUAL / BEFORE_RESTORE / BEFORE_CHANGE / IMPORTED */
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
)
