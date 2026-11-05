package com.qingling.writing.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.qingling.writing.core.database.entities.*

@Dao
interface BackupDao {
    /** Check the source in SQL before materializing potentially large history strings in the app heap. */
    @Query("""
        SELECT
        IFNULL((SELECT SUM(length(CAST(title AS BLOB)) + length(CAST(author AS BLOB)) + length(CAST(description AS BLOB)) + 1024) FROM books WHERE space_id = :spaceId AND (:bookId IS NULL OR id=:bookId)), 0) +
        IFNULL((SELECT SUM(length(CAST(v.title AS BLOB)) + 1024) FROM volumes v JOIN books b ON b.id = v.book_id WHERE b.space_id = :spaceId AND (:bookId IS NULL OR b.id=:bookId)), 0) +
        IFNULL((SELECT SUM(length(CAST(c.title AS BLOB)) + length(CAST(c.body AS BLOB)) + 1024) FROM chapters c JOIN books b ON b.id = c.book_id WHERE b.space_id = :spaceId AND (:bookId IS NULL OR b.id=:bookId)), 0) +
        IFNULL((SELECT SUM(length(CAST(r.title AS BLOB)) + length(CAST(r.body AS BLOB)) + 1024) FROM revisions r JOIN chapters c ON c.id = r.chapter_id JOIN books b ON b.id = c.book_id WHERE b.space_id = :spaceId AND (:bookId IS NULL OR b.id=:bookId)), 0) +
        IFNULL((SELECT COUNT(*) * 128 FROM editor_positions p JOIN chapters c ON c.id = p.chapter_id JOIN books b ON b.id = c.book_id WHERE b.space_id = :spaceId AND (:bookId IS NULL OR b.id=:bookId)), 0) +
        IFNULL((SELECT SUM(length(CAST(p.title AS BLOB)) + length(CAST(p.body AS BLOB)) + 1024) FROM planning_entries p JOIN books b ON b.id=p.book_id WHERE b.space_id=:spaceId AND (:bookId IS NULL OR b.id=:bookId)), 0)
    """)
    suspend fun conservativeSize(spaceId: String,bookId: String? = null): Long
    @Query("SELECT * FROM books WHERE space_id = :spaceId AND (:bookId IS NULL OR id=:bookId) ORDER BY created_at, id")
    suspend fun books(spaceId: String,bookId: String? = null): List<BookEntity>
    @Query("SELECT v.* FROM volumes v JOIN books b ON b.id = v.book_id WHERE b.space_id = :spaceId AND (:bookId IS NULL OR b.id=:bookId) ORDER BY v.book_id, v.position, v.id")
    suspend fun volumes(spaceId: String,bookId: String? = null): List<VolumeEntity>
    @Query("SELECT c.* FROM chapters c JOIN books b ON b.id = c.book_id WHERE b.space_id = :spaceId AND (:bookId IS NULL OR b.id=:bookId) ORDER BY c.volume_id, c.position, c.id")
    suspend fun chapters(spaceId: String,bookId: String? = null): List<ChapterEntity>
    @Query("SELECT r.* FROM revisions r JOIN chapters c ON c.id = r.chapter_id JOIN books b ON b.id = c.book_id WHERE b.space_id = :spaceId AND (:bookId IS NULL OR b.id=:bookId) ORDER BY r.created_at, r.id")
    suspend fun revisions(spaceId: String,bookId: String? = null): List<RevisionEntity>
    @Query("SELECT p.* FROM editor_positions p JOIN chapters c ON c.id = p.chapter_id JOIN books b ON b.id = c.book_id WHERE b.space_id = :spaceId AND (:bookId IS NULL OR b.id=:bookId) ORDER BY p.chapter_id")
    suspend fun positions(spaceId: String,bookId: String? = null): List<EditorPositionEntity>
}
