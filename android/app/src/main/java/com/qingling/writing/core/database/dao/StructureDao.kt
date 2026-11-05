package com.qingling.writing.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.qingling.writing.core.model.BatchDocument

@Dao
interface StructureDao {
    @Query("""SELECT COALESCE(SUM(length(CAST(c.body AS BLOB))),0) FROM chapters c JOIN volumes v ON v.id=c.volume_id
        WHERE c.book_id=:book AND c.id IN (:ids) AND c.deleted_at IS NULL AND v.deleted_at IS NULL""")
    suspend fun size(book: String,ids: List<String>): Long
    @Query("""SELECT c.id,c.volume_id AS volumeId,v.title AS volumeTitle,v.position AS volumePosition,
        c.title,c.position,c.edit_seq AS editSeq,c.body FROM chapters c JOIN volumes v ON v.id=c.volume_id
        WHERE c.book_id=:book AND c.id IN (:ids) AND c.deleted_at IS NULL AND v.deleted_at IS NULL
        ORDER BY v.position,v.id,c.position,c.id""")
    suspend fun selected(book: String,ids: List<String>): List<BatchDocument>
    @Query("UPDATE books SET last_active_chapter_id=:chapter WHERE id=:book")
    suspend fun setActive(book: String,chapter: String)
}
