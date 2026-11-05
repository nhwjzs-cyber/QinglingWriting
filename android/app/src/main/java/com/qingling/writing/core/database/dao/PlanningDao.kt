package com.qingling.writing.core.database.dao

import androidx.room.*
import com.qingling.writing.core.database.entities.PlanningEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanningDao {
    @Query("""SELECT p.id,p.book_id,p.chapter_id,p.volume_id,p.kind,p.title,
        substr(p.body,1,120) AS body,p.updated_at,p.version FROM planning_entries p
        WHERE p.book_id=:bookId AND p.kind IN ('CHAPTER_OUTLINE','VOLUME_SUMMARY') ORDER BY p.id""")
    fun outlinePreviews(bookId: String): Flow<List<PlanningEntity>>
    // List/search returns only short excerpts; opening an entry reads its full body separately.
    @Query("""SELECT p.id,p.book_id,p.chapter_id,p.volume_id,p.kind,p.title,
        substr(p.body,1,180) AS body,p.updated_at,p.version FROM planning_entries p JOIN books b ON b.id=p.book_id
        WHERE p.book_id=:bookId AND b.deleted_at IS NULL AND (:kind='' OR p.kind=:kind)
        AND (p.title LIKE :pattern ESCAPE '\' OR p.body LIKE :pattern ESCAPE '\')
        AND (p.chapter_id IS NULL OR EXISTS(SELECT 1 FROM chapters c WHERE c.id=p.chapter_id AND c.deleted_at IS NULL))
        AND (p.volume_id IS NULL OR EXISTS(SELECT 1 FROM volumes v WHERE v.id=p.volume_id AND v.deleted_at IS NULL))
        ORDER BY p.updated_at DESC,p.id""")
    fun search(bookId: String,pattern: String,kind: String): Flow<List<PlanningEntity>>
    @Query("""SELECT p.* FROM planning_entries p JOIN books b ON b.id=p.book_id
        WHERE p.book_id=:bookId AND b.deleted_at IS NULL
        AND (p.chapter_id IS NULL OR EXISTS(SELECT 1 FROM chapters c WHERE c.id=p.chapter_id AND c.deleted_at IS NULL))
        AND (p.volume_id IS NULL OR EXISTS(SELECT 1 FROM volumes v WHERE v.id=p.volume_id AND v.deleted_at IS NULL))
        ORDER BY p.updated_at DESC,p.id""")
    fun observe(bookId: String): Flow<List<PlanningEntity>>
    @Query("""SELECT p.* FROM planning_entries p LEFT JOIN books b ON b.id=p.book_id
        WHERE p.kind='INSPIRATION' AND (p.book_id IS NULL OR (b.deleted_at IS NULL AND b.space_id=:spaceId))
        ORDER BY p.updated_at DESC,p.id""")
    fun inbox(spaceId: String): Flow<List<PlanningEntity>>
    @Query("SELECT * FROM planning_entries WHERE id=:id") suspend fun byId(id: String): PlanningEntity?
    @Query("SELECT * FROM planning_entries WHERE chapter_id=:id") suspend fun forChapter(id: String): PlanningEntity?
    @Query("SELECT * FROM planning_entries WHERE volume_id=:id") suspend fun forVolume(id: String): PlanningEntity?
    @Query("SELECT * FROM planning_entries WHERE book_id=:bookId AND kind='INSPIRATION'")
    suspend fun bookNotes(bookId:String):List<PlanningEntity>
    @Query("UPDATE planning_entries SET book_id=:bookId,body=:body,updated_at=:stamp,version=version+1 WHERE id=:id AND version=:expected AND kind='INSPIRATION'")
    suspend fun moveNote(id:String,bookId:String?,body:String,stamp:String,expected:Long):Int
    @Query("DELETE FROM planning_entries WHERE id=:id AND version=:expected AND chapter_id IS NULL AND volume_id IS NULL")
    suspend fun deleteUserEntry(id:String,expected:Long):Int
    @Insert suspend fun insert(entry: PlanningEntity)
    @Query("UPDATE planning_entries SET title=:title,body=:body,updated_at=:stamp,version=version+1 WHERE id=:id AND version=:expected")
    suspend fun update(id: String,title: String,body: String,stamp: String,expected: Long): Int
    @Query("SELECT 'planning:' || id || ':' || version FROM planning_entries ORDER BY id") fun versions(): Flow<List<String>>
    @Query("SELECT p.* FROM planning_entries p LEFT JOIN books b ON b.id=p.book_id WHERE (p.book_id IS NULL AND :bookId IS NULL) OR (b.space_id=:spaceId AND (:bookId IS NULL OR b.id=:bookId)) ORDER BY p.id")
    suspend fun capture(spaceId: String,bookId: String? = null): List<PlanningEntity>
    @Query("SELECT id FROM planning_entries WHERE book_id IN (:ids)") suspend fun idsOfBooks(ids: List<String>): List<String>
    @Query("SELECT id FROM planning_entries WHERE chapter_id IN (:ids)") suspend fun idsOfChapters(ids: List<String>): List<String>
    @Query("SELECT id FROM planning_entries WHERE volume_id IN (:ids)") suspend fun idsOfVolumes(ids: List<String>): List<String>
}
