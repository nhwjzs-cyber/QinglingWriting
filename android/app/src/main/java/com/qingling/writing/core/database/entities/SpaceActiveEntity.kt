package com.qingling.writing.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

/**
 * 每空间最近激活记录(UX v0.2 §1.1):冷启动直达上次有效编辑章节。
 * chapter 更新时同步写本表;book 上次激活章由 books.last_active_chapter_id 承载(同 v3 迁移新增列)。
 */
@Entity(
    tableName = "space_active",
    primaryKeys = ["space_id"],
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = SpaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["space_id"],
            onDelete = androidx.room.ForeignKey.RESTRICT,
        ),
    ],
)
data class SpaceActiveEntity(
    @ColumnInfo(name = "space_id") val spaceId: String,
    /** 上次激活的书;书被删时由仓储层安全回退,不强制清除本行。 */
    @ColumnInfo(name = "last_active_book_id") val lastActiveBookId: String?,
    /** 上次激活的章;章被删时由仓储层回退到同书首存活章。 */
    @ColumnInfo(name = "last_active_chapter_id") val lastActiveChapterId: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
)
