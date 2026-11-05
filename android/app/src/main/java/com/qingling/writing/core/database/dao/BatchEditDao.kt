package com.qingling.writing.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.qingling.writing.core.model.BatchDocument

@Dao
interface BatchEditDao {
    @Query("SELECT c.id || ':' || c.edit_seq || ':' || COALESCE(c.deleted_at,'') || ':' || COALESCE(d.completed,0) FROM chapters c LEFT JOIN chapter_details d ON c.id=d.chapter_id ORDER BY c.id")
    fun observeVersions(): kotlinx.coroutines.flow.Flow<List<String>>
    @Query("SELECT c.id FROM chapters c JOIN volumes v ON c.volume_id=v.id WHERE c.book_id=:bookId AND c.deleted_at IS NULL AND v.deleted_at IS NULL ORDER BY v.position,v.id,c.position,c.id")
    fun chapterOrder(bookId: String): kotlinx.coroutines.flow.Flow<List<String>>
    @Query("""SELECT COUNT(*) FROM chapters c JOIN volumes v ON v.id=c.volume_id
        WHERE c.book_id=:book AND c.deleted_at IS NULL AND v.deleted_at IS NULL AND (:volume IS NULL OR v.id=:volume)""")
    suspend fun count(book: String, volume: String?): Int
    @Query("""SELECT COALESCE(SUM(length(CAST(c.body AS BLOB))),0) FROM chapters c JOIN volumes v ON v.id=c.volume_id
        WHERE c.book_id=:book AND c.deleted_at IS NULL AND v.deleted_at IS NULL AND (:volume IS NULL OR v.id=:volume)""")
    suspend fun size(book: String, volume: String?): Long
    @Query("""SELECT c.id, c.volume_id AS volumeId, v.title AS volumeTitle, v.position AS volumePosition,
        c.title, c.position, c.edit_seq AS editSeq, c.body FROM chapters c JOIN volumes v ON v.id=c.volume_id
        WHERE c.book_id=:book AND c.deleted_at IS NULL AND v.deleted_at IS NULL AND (:volume IS NULL OR v.id=:volume)
        ORDER BY v.position,v.id,c.position,c.id""")
    suspend fun documents(book: String, volume: String?): List<BatchDocument>
}
