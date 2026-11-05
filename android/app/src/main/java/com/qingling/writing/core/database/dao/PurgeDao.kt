package com.qingling.writing.core.database.dao

import androidx.room.Dao
import androidx.room.Query

data class PurgeChapter(val id: String, val bookId: String, val volumeId: String,
    val deletedAt: String?, val batch: String?)

@Dao
interface PurgeDao {
    @Query("SELECT id, book_id AS bookId, volume_id AS volumeId, deleted_at AS deletedAt, deletion_batch_id AS batch FROM chapters WHERE book_id=:book")
    suspend fun chaptersOfBook(book: String): List<PurgeChapter>
    @Query("SELECT id, book_id AS bookId, volume_id AS volumeId, deleted_at AS deletedAt, deletion_batch_id AS batch FROM chapters WHERE volume_id=:volume")
    suspend fun chaptersOfVolume(volume: String): List<PurgeChapter>
    @Query("SELECT id, book_id AS bookId, volume_id AS volumeId, deleted_at AS deletedAt, deletion_batch_id AS batch FROM chapters WHERE id=:id")
    suspend fun chapter(id: String): PurgeChapter?
    @Query("SELECT id FROM volumes WHERE book_id=:book")
    suspend fun volumesOfBook(book: String): List<String>
    @Query("SELECT COUNT(*) FROM revisions WHERE chapter_id IN (:ids)")
    suspend fun revisionCount(ids: List<String>): Int
    @Query("DELETE FROM revisions WHERE chapter_id IN (:ids)")
    suspend fun deleteRevisions(ids: List<String>)
    @Query("DELETE FROM editor_positions WHERE chapter_id IN (:ids)")
    suspend fun deletePositions(ids: List<String>)
    @Query("UPDATE space_active SET last_active_chapter_id=NULL WHERE last_active_chapter_id IN (:ids)")
    suspend fun clearSpaceChapters(ids: List<String>)
    @Query("DELETE FROM book_active WHERE last_active_chapter_id IN (:ids)")
    suspend fun clearBookChapters(ids: List<String>)
    @Query("UPDATE books SET last_active_chapter_id=NULL WHERE last_active_chapter_id IN (:ids)")
    suspend fun clearLastChapters(ids: List<String>)
    @Query("DELETE FROM chapters WHERE id IN (:ids) AND deleted_at IS NOT NULL")
    suspend fun deleteChapters(ids: List<String>): Int
    @Query("DELETE FROM volumes WHERE id IN (:ids) AND deleted_at IS NOT NULL")
    suspend fun deleteVolumes(ids: List<String>): Int
    @Query("DELETE FROM book_active WHERE book_id IN (:ids)")
    suspend fun deleteBookActive(ids: List<String>)
    @Query("UPDATE space_active SET last_active_book_id=NULL,last_active_chapter_id=NULL WHERE last_active_book_id IN (:ids)")
    suspend fun clearSpaceBooks(ids: List<String>)
    @Query("DELETE FROM books WHERE id IN (:ids) AND deleted_at IS NOT NULL")
    suspend fun deleteBooks(ids: List<String>): Int
}
