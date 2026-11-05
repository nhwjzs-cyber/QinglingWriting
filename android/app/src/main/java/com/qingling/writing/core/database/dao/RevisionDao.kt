package com.qingling.writing.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.qingling.writing.core.database.entities.RevisionEntity
import kotlinx.coroutines.flow.Flow

data class AutoRevisionInfo(val id: String,val chapterId: String,val createdAt: String,val contentBytes: Long)
data class ManuscriptStorageInfo(val manuscriptBytes: Long,val historyBytes: Long,val historyCount: Long)

@Dao
interface RevisionDao {

    @Query("SELECT r.id AS id,r.chapter_id AS chapterId,r.created_at AS createdAt,length(CAST(r.body AS BLOB))+length(CAST(r.title AS BLOB)) AS contentBytes FROM revisions r JOIN chapters c ON c.id=r.chapter_id JOIN books b ON b.id=c.book_id WHERE b.space_id=:space AND r.source='AUTO' ORDER BY r.chapter_id,r.created_at DESC,r.id DESC LIMIT 100001")
    suspend fun autoForCleanup(space: String): List<AutoRevisionInfo>

    @Query("SELECT IFNULL((SELECT SUM(length(CAST(c.body AS BLOB))) FROM chapters c JOIN books b ON c.book_id=b.id WHERE b.space_id=:space),0) AS manuscriptBytes, IFNULL((SELECT SUM(length(CAST(r.body AS BLOB))+length(CAST(r.title AS BLOB))) FROM revisions r JOIN chapters c ON c.id=r.chapter_id JOIN books b ON b.id=c.book_id WHERE b.space_id=:space),0) AS historyBytes, (SELECT COUNT(*) FROM revisions r JOIN chapters c ON c.id=r.chapter_id JOIN books b ON b.id=c.book_id WHERE b.space_id=:space) AS historyCount")
    suspend fun storageInfo(space: String): ManuscriptStorageInfo

    @Query("DELETE FROM revisions WHERE source='AUTO' AND id IN (:ids)")
    suspend fun deleteAutoByIds(ids: List<String>): Int

    @Insert
    suspend fun insert(revision: RevisionEntity)

    @Query("SELECT * FROM revisions WHERE chapter_id = :chapterId ORDER BY created_at DESC, id DESC")
    fun observeByChapter(chapterId: String): Flow<List<RevisionEntity>>

    @Query("SELECT * FROM revisions WHERE id = :id")
    suspend fun byId(id: String): RevisionEntity?

    /** 最近一次 AUTO 快照时间(节流判定用);无则 null。 */
    @Query(
        "SELECT created_at FROM revisions WHERE chapter_id = :chapterId AND source = 'AUTO' " +
            "ORDER BY created_at DESC LIMIT 1",
    )
    suspend fun latestAutoCreatedAt(chapterId: String): String?

    @Query("DELETE FROM revisions WHERE chapter_id = :chapterId AND source = 'AUTO' AND id NOT IN " +
        "(SELECT id FROM revisions WHERE chapter_id = :chapterId AND source = 'AUTO' " +
        "ORDER BY created_at DESC, id DESC LIMIT :keep)")
    suspend fun trimAutoRevisions(chapterId: String, keep: Int)
}
