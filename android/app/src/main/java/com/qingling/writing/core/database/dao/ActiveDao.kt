package com.qingling.writing.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.qingling.writing.core.database.entities.BookActiveEntity
import com.qingling.writing.core.database.entities.SpaceActiveEntity

@Dao
interface ActiveDao {

    // ---- 空间级 ----

    @Upsert
    suspend fun upsertSpaceActive(entity: SpaceActiveEntity)

    @Query("SELECT * FROM space_active WHERE space_id = :spaceId")
    suspend fun spaceActive(spaceId: String): SpaceActiveEntity?

    // ---- 书级 ----

    @Upsert
    suspend fun upsertBookActive(entity: BookActiveEntity)

    @Query("SELECT * FROM book_active WHERE book_id = :bookId")
    suspend fun bookActive(bookId: String): BookActiveEntity?

    /** 冷启动回退:该空间的书,按最近编辑倒序(与书架一致)。 */
    @Query(
        "SELECT b.id FROM books b WHERE b.space_id = :spaceId AND b.deleted_at IS NULL " +
            "ORDER BY b.updated_at DESC LIMIT 1",
    )
    suspend fun firstAliveBookId(spaceId: String): String?

    /** 回退:该书的第一个存活章(目录序)。 */
    @Query(
        "SELECT c.id FROM chapters c JOIN volumes v ON v.id = c.volume_id " +
            "WHERE c.book_id = :bookId AND c.deleted_at IS NULL AND v.deleted_at IS NULL " +
            "ORDER BY v.position ASC, v.id ASC, c.position ASC, c.id ASC LIMIT 1",
    )
    suspend fun firstAliveChapterId(bookId: String): String?
}
