package com.qingling.writing.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** 整书墓碑行:范围统计(卷/章总数,级联删除时它们共用该批次)随行返回。 */
data class DeletedBookRow(
    val id: String,
    val title: String,
    val deletedAt: String,
    val deletionBatchId: String,
    val volumeCount: Int,
    val chapterCount: Int,
)

/** 存活书下的卷墓碑。 */
data class DeletedVolumeRow(
    val id: String,
    val title: String,
    val bookId: String,
    val bookTitle: String,
    val deletedAt: String,
    val deletionBatchId: String,
    val chapterCount: Int,
)

/** 存活书下的章墓碑。 */
data class DeletedChapterRow(
    val id: String,
    val title: String,
    val bookId: String,
    val bookTitle: String,
    val deletedAt: String,
    val deletionBatchId: String,
)

/**
 * 回收站查询(M1 + 审核修复):按「删除批次展示根条目,过滤随父项级联删除的子条目」。
 * 判定依据是批次相同(随父级联),而非父项是否删除(父项可能被独立删除,此时子条目是
 * 独立删除,必须保留以便单独恢复;恢复时祖先链由仓储补救)。书墓碑无父项,全部展示。
 */
@Dao
interface RecycleDao {

    @Query(
        """
        SELECT b.id AS id, b.title AS title, b.deleted_at AS deletedAt,
               b.deletion_batch_id AS deletionBatchId,
               (SELECT COUNT(*) FROM volumes v WHERE v.book_id = b.id) AS volumeCount,
               (SELECT COUNT(*) FROM chapters c WHERE c.book_id = b.id) AS chapterCount
        FROM books b
        WHERE b.space_id = :spaceId AND b.deleted_at IS NOT NULL
        ORDER BY b.deleted_at DESC
        """,
    )
    fun observeDeletedBooks(spaceId: String): Flow<List<DeletedBookRow>>

    @Query(
        """
        SELECT v.id AS id, v.title AS title, v.book_id AS bookId, b.title AS bookTitle,
               v.deleted_at AS deletedAt, v.deletion_batch_id AS deletionBatchId,
               (SELECT COUNT(*) FROM chapters c WHERE c.volume_id = v.id) AS chapterCount
        FROM volumes v
        JOIN books b ON b.id = v.book_id
        WHERE b.space_id = :spaceId AND v.deleted_at IS NOT NULL
          AND (b.deleted_at IS NULL OR b.deletion_batch_id != v.deletion_batch_id)
        ORDER BY v.deleted_at DESC
        """,
    )
    fun observeDeletedVolumesInLiveBooks(spaceId: String): Flow<List<DeletedVolumeRow>>

    @Query(
        """
        SELECT c.id AS id, c.title AS title, c.book_id AS bookId, b.title AS bookTitle,
               c.deleted_at AS deletedAt, c.deletion_batch_id AS deletionBatchId
        FROM chapters c
        JOIN books b ON b.id = c.book_id
        JOIN volumes v ON v.id = c.volume_id
        WHERE b.space_id = :spaceId AND c.deleted_at IS NOT NULL
          AND (b.deleted_at IS NULL OR b.deletion_batch_id != c.deletion_batch_id)
          AND (v.deleted_at IS NULL OR v.deletion_batch_id != c.deletion_batch_id)
        ORDER BY c.deleted_at DESC
        """,
    )
    fun observeDeletedChaptersInLiveBooks(spaceId: String): Flow<List<DeletedChapterRow>>
}
