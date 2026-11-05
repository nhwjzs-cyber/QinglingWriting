package com.qingling.writing.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.qingling.writing.core.database.entities.EditorPositionEntity

@Dao
interface EditorPositionDao {

    @Upsert
    suspend fun upsert(position: EditorPositionEntity)

    @Query("SELECT * FROM editor_positions WHERE chapter_id = :chapterId")
    suspend fun byChapter(chapterId: String): EditorPositionEntity?
}
