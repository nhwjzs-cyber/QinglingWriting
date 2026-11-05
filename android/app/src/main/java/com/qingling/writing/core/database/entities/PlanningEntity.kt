package com.qingling.writing.core.database.entities

import androidx.room.*
import com.qingling.writing.core.model.PlanningEntry

@Entity(tableName = "planning_entries", foreignKeys = [
    ForeignKey(entity = BookEntity::class,parentColumns = ["id"],childColumns = ["book_id"],onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = ChapterEntity::class,parentColumns = ["id"],childColumns = ["chapter_id"],onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = VolumeEntity::class,parentColumns = ["id"],childColumns = ["volume_id"],onDelete = ForeignKey.CASCADE)
],indices = [Index("book_id"),Index(value = ["chapter_id"],unique = true),Index(value = ["volume_id"],unique = true)])
data class PlanningEntity(@PrimaryKey val id: String,@ColumnInfo(name = "book_id") val bookId: String?,
    @ColumnInfo(name = "chapter_id") val chapterId: String?,@ColumnInfo(name = "volume_id") val volumeId: String?,
    val kind: String,val title: String,val body: String,@ColumnInfo(name = "updated_at") val updatedAt: String,val version: Long) {
    fun toEntry() = PlanningEntry(id,bookId.orEmpty(),chapterId,volumeId,kind,title,body,updatedAt,version)
}
fun PlanningEntry.toEntity() = PlanningEntity(id,bookId.takeIf{it.isNotEmpty()},chapterId,volumeId,kind,title,body,updatedAt,version)
