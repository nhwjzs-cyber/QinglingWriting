package com.qingling.writing.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.qingling.writing.core.database.entities.BookEntity
import kotlinx.coroutines.flow.Flow

/** 书架汇总行:子查询聚合字数/章数/继续写作章(SQL 层保证「最近编辑倒序」,docs/01 §4)。 */
data class BookSummaryRow(
    val id: String,
    val title: String,
    val updatedAt: String,
    val wordCount: Long,
    val chapterCount: Int,
    val continueChapterId: String?,
    val completedCount: Int,
    val author: String,
    val createdAt: String,
    val pinned: Boolean,
    val archived: Boolean,
)

@Dao
interface BookDao {
    @Query("UPDATE books SET chapter_number_counter = chapter_number_counter + 1 WHERE id=:bookId AND chapter_number_counter < 9223372036854775807")
    suspend fun incrementChapterNumber(bookId: String): Int
    @Query("SELECT chapter_number_counter FROM books WHERE id=:bookId")
    suspend fun chapterNumber(bookId: String): Long?


    @Insert
    suspend fun insert(book: BookEntity)

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun byId(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeById(id: String): Flow<BookEntity?>

    @Query(
        """
        SELECT b.id AS id, b.title AS title, b.updated_at AS updatedAt, b.author AS author,b.created_at AS createdAt,
          IFNULL(f.pinned,0) AS pinned,IFNULL(f.archived,0) AS archived,
          IFNULL((SELECT SUM(c.word_count) FROM chapters c
                  WHERE c.book_id = b.id AND c.deleted_at IS NULL), 0) AS wordCount,
          (SELECT COUNT(*) FROM chapters c
                  WHERE c.book_id = b.id AND c.deleted_at IS NULL) AS chapterCount,
          (SELECT c2.id FROM chapters c2
                  WHERE c2.book_id = b.id AND c2.deleted_at IS NULL
                  ORDER BY c2.updated_at DESC, c2.position ASC LIMIT 1) AS continueChapterId,
          (SELECT COUNT(*) FROM chapters c JOIN chapter_details d ON c.id=d.chapter_id
              WHERE c.book_id=b.id AND c.deleted_at IS NULL AND d.completed=1) AS completedCount
        FROM books b LEFT JOIN shelf_flags f ON b.id=f.book_id
        WHERE b.space_id = :spaceId AND b.deleted_at IS NULL
        ORDER BY b.updated_at DESC,b.id ASC
        """,
    )
    fun observeBookSummaries(spaceId: String): Flow<List<BookSummaryRow>>

    @Query("SELECT COUNT(*) FROM books WHERE space_id = :spaceId")
    suspend fun countInSpace(spaceId: String): Int

    @Query("UPDATE books SET title = :title, updated_at = :updatedAt WHERE id = :id AND deleted_at IS NULL")
    suspend fun renameActive(id: String, title: String, updatedAt: String): Int

    @Query("UPDATE books SET updated_at = :updatedAt WHERE id = :id")
    suspend fun touchUpdated(id: String, updatedAt: String)

    @Query(
        "UPDATE books SET deleted_at = :deletedAt, deletion_batch_id = :batchId " +
            "WHERE id = :id AND deleted_at IS NULL",
    )
    suspend fun softDelete(id: String, deletedAt: String, batchId: String): Int

    @Query(
        "UPDATE books SET deleted_at = NULL, deletion_batch_id = NULL, updated_at = :updatedAt " +
            "WHERE id = :id AND deletion_batch_id = :batchId",
    )
    suspend fun restore(id: String, batchId: String, updatedAt: String): Int

    /**
     * 祖先链补救(M1 审核修复):恢复子孙批次前,若书自身已删(且删除批次不同),
     * 仅清书自身的墓碑标记——不触碰书删除批次下的其他成员(它们保持各自墓碑)。
     */
    @Query(
        "UPDATE books SET deleted_at = NULL, deletion_batch_id = NULL " +
            "WHERE id = :id AND deleted_at IS NOT NULL",
    )
    suspend fun reviveSelf(id: String): Int

    /** 正文提交(M2)后推进书级 local_seq 并置脏标记(M5 同步使用);返回受影响行数。 */
    @Query(
        "UPDATE books SET local_seq = local_seq + 1, local_dirty = 1, updated_at = :updatedAt " +
            "WHERE id = :id",
    )
    suspend fun bumpLocalSeq(id: String, updatedAt: String): Int

    @Query("SELECT local_seq FROM books WHERE id = :id")
    suspend fun localSeq(id: String): Long?
}
