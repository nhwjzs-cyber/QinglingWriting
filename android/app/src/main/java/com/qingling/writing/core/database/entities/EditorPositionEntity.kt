package com.qingling.writing.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * 编辑器光标/滚动位置(local-schema.sql 的 editor_positions;M2 schema v2 落地)。
 * selection 为 UTF-16 偏移,恢复时按文本长度 clamp(docs/03 §4),与字数口径无关。
 */
@Entity(
    tableName = "editor_positions",
    foreignKeys = [
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapter_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
)
data class EditorPositionEntity(
    /** 章节即主键:一章一条最新位置。 */
    @PrimaryKey @ColumnInfo(name = "chapter_id") val chapterId: String,
    @ColumnInfo(name = "selection_start") val selectionStart: Long,
    @ColumnInfo(name = "selection_end") val selectionEnd: Long,
    @ColumnInfo(name = "scroll_offset") val scrollOffset: Long,
)
