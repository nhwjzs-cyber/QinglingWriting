package com.qingling.writing.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.qingling.writing.core.database.entities.ChapterEntity
import com.qingling.writing.core.database.entities.VolumeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OutlineDao {

    // instr treats %, _, punctuation and emoji literally. Never materialize all chapter bodies in UI.
    @Query("""
        SELECT ch.id AS chapterId, v.title AS volumeTitle, ch.title AS chapterTitle,
            substr(ch.body, max(1, instr(ch.body, :literal) - 35), 120) AS snippet
        FROM chapters ch JOIN volumes v ON v.id = ch.volume_id
        JOIN books b ON b.id = ch.book_id
        WHERE ch.book_id = :bookId AND b.deleted_at IS NULL AND v.deleted_at IS NULL
            AND ch.deleted_at IS NULL AND (instr(ch.title, :literal) > 0 OR instr(ch.body, :literal) > 0)
        ORDER BY v.position, v.id, ch.position, ch.id LIMIT 201
    """)
    suspend fun searchBook(bookId: String, literal: String): List<com.qingling.writing.core.model.BookSearchHit>

    // ---- 卷 ----

    @Insert
    suspend fun insertVolume(volume: VolumeEntity)

    @Query("SELECT * FROM volumes WHERE id = :id")
    suspend fun volumeById(id: String): VolumeEntity?

    @Query("SELECT * FROM volumes WHERE book_id = :bookId AND deleted_at IS NULL ORDER BY position ASC, id ASC")
    suspend fun activeVolumes(bookId: String): List<VolumeEntity>

    @Query("SELECT * FROM volumes WHERE book_id = :bookId AND deleted_at IS NULL ORDER BY position ASC, id ASC")
    fun observeActiveVolumes(bookId: String): Flow<List<VolumeEntity>>

    @Query("SELECT IFNULL(MAX(position), -1) + 1 FROM volumes WHERE book_id = :bookId AND deleted_at IS NULL")
    suspend fun nextVolumePosition(bookId: String): Long

    @Query("UPDATE volumes SET title = :title, updated_at = :updatedAt WHERE id = :id AND deleted_at IS NULL")
    suspend fun renameVolume(id: String, title: String, updatedAt: String): Int

    @Query("UPDATE volumes SET position = :position WHERE id = :id")
    suspend fun setVolumePosition(id: String, position: Long)

    @Query(
        "UPDATE volumes SET deleted_at = :deletedAt, deletion_batch_id = :batchId " +
            "WHERE id = :id AND deleted_at IS NULL",
    )
    suspend fun softDeleteVolume(id: String, deletedAt: String, batchId: String): Int

    @Query(
        "UPDATE volumes SET deleted_at = :deletedAt, deletion_batch_id = :batchId " +
            "WHERE book_id = :bookId AND deleted_at IS NULL",
    )
    suspend fun cascadeVolumesOfBook(bookId: String, deletedAt: String, batchId: String): Int

    @Query("UPDATE volumes SET deleted_at = NULL, deletion_batch_id = NULL WHERE deletion_batch_id = :batchId")
    suspend fun restoreVolumesByBatch(batchId: String): Int

    @Query("SELECT COUNT(*) FROM volumes WHERE deletion_batch_id = :batchId AND book_id = :bookId")
    suspend fun countVolumesInBatchForBook(batchId: String, bookId: String): Int

    // ---- 章 ----

    @Insert
    suspend fun insertChapter(chapter: ChapterEntity)

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun chapterById(id: String): ChapterEntity?

    @Query("SELECT * FROM chapters WHERE id=:id AND deleted_at IS NULL")
    fun observeChapterById(id:String): Flow<ChapterEntity?>

    @Query(
        "SELECT ch.* FROM chapters ch\n" +
            "JOIN volumes v ON v.id = ch.volume_id\n" +
            "WHERE ch.book_id = :bookId AND ch.deleted_at IS NULL AND v.deleted_at IS NULL\n" +
            "ORDER BY v.position ASC, v.id ASC, ch.position ASC, ch.id ASC",
    )
    fun observeActiveChapters(bookId: String): Flow<List<ChapterEntity>>

    /** 导出:按目录序收集选中存活章(排除软删除,M3)。 */
    @Query(
        "SELECT ch.* FROM chapters ch\n" +
            "JOIN volumes v ON v.id = ch.volume_id\n" +
            "WHERE ch.book_id = :bookId AND ch.deleted_at IS NULL AND v.deleted_at IS NULL\n" +
            "AND ch.id IN (:chapterIds)\n" +
            "ORDER BY v.position ASC, v.id ASC, ch.position ASC, ch.id ASC",
    )
    suspend fun exportChapters(bookId: String, chapterIds: List<String>): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE volume_id = :volumeId AND deleted_at IS NULL ORDER BY position ASC, id ASC")
    suspend fun activeChaptersOfVolume(volumeId: String): List<ChapterEntity>

    @Query("SELECT IFNULL(MAX(position), -1) + 1 FROM chapters WHERE volume_id = :volumeId AND deleted_at IS NULL")
    suspend fun nextChapterPosition(volumeId: String): Long

    @Query("UPDATE chapters SET title = :title, title_locked = 1, updated_at = :updatedAt WHERE id = :id AND deleted_at IS NULL")
    suspend fun renameChapter(id: String, title: String, updatedAt: String): Int

    @Query(
        "UPDATE chapters SET volume_id = :volumeId, position = :position " +
            "WHERE id = :id AND deleted_at IS NULL",
    )
    suspend fun moveChapter(id: String, volumeId: String, position: Long): Int

    @Query("UPDATE chapters SET position = :position WHERE id = :id")
    suspend fun setChapterPosition(id: String, position: Long)

    /**
     * 正文落库(editSeq 防旧写,M2):仅当库内 edit_seq < 新 seq 且章存活才提交;
     * 返回 0 时由仓储区分 STALE_SEQUENCE / DELETED。
     */
    @Query(
        "UPDATE chapters SET body = :body, word_count = :wordCount, edit_seq = :editSeq, " +
            "updated_at = :updatedAt WHERE id = :id AND deleted_at IS NULL AND edit_seq < :editSeq",
    )
    suspend fun writeChapterBodyGuarded(
        id: String,
        body: String,
        wordCount: Long,
        editSeq: Long,
        updatedAt: String,
    ): Int

    @Transaction
    suspend fun saveChapterBodyGuarded(id: String, body: String, wordCount: Long, editSeq: Long, updatedAt: String): Int {
        val changed = writeChapterBodyGuarded(id, body, wordCount, editSeq, updatedAt)
        return changed
    }

    /** 历史恢复(M2):覆盖标题/正文/字数并推进 edit_seq(同样带防旧写守卫)。 */
    @Query(
        "UPDATE chapters SET title = :title, body = :body, word_count = :wordCount, " +
            "edit_seq = :editSeq, updated_at = :updatedAt " +
            "WHERE id = :id AND deleted_at IS NULL AND edit_seq < :editSeq",
    )
    suspend fun writeRestoredChapterContent(
        id: String,
        title: String,
        body: String,
        wordCount: Long,
        editSeq: Long,
        updatedAt: String,
    ): Int

    @Transaction
    suspend fun restoreChapterContent(id: String, title: String, body: String, wordCount: Long, editSeq: Long, updatedAt: String): Int {
        val changed = writeRestoredChapterContent(id, title, body, wordCount, editSeq, updatedAt)
        return changed
    }

    @Query(
        "UPDATE chapters SET deleted_at = :deletedAt, deletion_batch_id = :batchId " +
            "WHERE id = :id AND deleted_at IS NULL",
    )
    suspend fun softDeleteChapter(id: String, deletedAt: String, batchId: String): Int

    /** 级联只标记此前存活的章(docs/01 §4:不得复活/重复标记已删除项)。 */
    @Query(
        "UPDATE chapters SET deleted_at = :deletedAt, deletion_batch_id = :batchId " +
            "WHERE volume_id = :volumeId AND deleted_at IS NULL",
    )
    suspend fun cascadeChaptersOfVolume(volumeId: String, deletedAt: String, batchId: String): Int

    @Query(
        "UPDATE chapters SET deleted_at = :deletedAt, deletion_batch_id = :batchId " +
            "WHERE book_id = :bookId AND deleted_at IS NULL",
    )
    suspend fun cascadeChaptersOfBook(bookId: String, deletedAt: String, batchId: String): Int

    @Query("UPDATE chapters SET deleted_at = NULL, deletion_batch_id = NULL WHERE deletion_batch_id = :batchId")
    suspend fun restoreChaptersByBatch(batchId: String): Int

    /**
     * 祖先链补救(M1 审核修复):恢复章批次前,若其父卷已被删除(独立于该批次的删除),
     * 仅清父卷自身的墓碑标记——父卷删除批次下的其他成员不受影响。
     */
    @Query(
        "UPDATE volumes SET deleted_at = NULL, deletion_batch_id = NULL " +
            "WHERE deleted_at IS NOT NULL AND id IN " +
            "(SELECT volume_id FROM chapters WHERE deletion_batch_id = :batchId)",
    )
    suspend fun reviveParentVolumesOfBatch(batchId: String): Int

    @Query("SELECT COUNT(*) FROM chapters WHERE deletion_batch_id = :batchId AND book_id = :bookId")
    suspend fun countChaptersInBatchForBook(batchId: String, bookId: String): Int

    /**
     * 仅 debug 演示种子写入正文(docs/05 §5:演示数据不进 release;DemoSeeder 专用,
     * 不参与任何用户保存链路——正文写入的正式路径是 M2 的 SaveCoordinator)。
     */
    @Query("UPDATE chapters SET body = :body, word_count = :wordCount WHERE id = :id")
    suspend fun seedChapterBodyForDemo(id: String, body: String, wordCount: Long)
}
