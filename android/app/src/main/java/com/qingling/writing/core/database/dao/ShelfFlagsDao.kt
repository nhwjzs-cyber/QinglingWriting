package com.qingling.writing.core.database.dao
import androidx.room.*
import com.qingling.writing.core.database.entities.ShelfFlagsEntity
import kotlinx.coroutines.flow.Flow
@Dao interface ShelfFlagsDao {
    @Query("SELECT * FROM shelf_flags WHERE book_id=:id") suspend fun byId(id: String): ShelfFlagsEntity?
    @Upsert suspend fun put(value: ShelfFlagsEntity)
    @Query("SELECT f.* FROM shelf_flags f JOIN books b ON b.id=f.book_id WHERE b.space_id=:space AND (:book IS NULL OR b.id=:book)")
    suspend fun capture(space: String,book: String?=null): List<ShelfFlagsEntity>
    @Query("SELECT book_id || ':' || pinned || ':' || archived FROM shelf_flags ORDER BY book_id") fun versions(): Flow<List<String>>
    @Query("UPDATE books SET local_seq=local_seq+1,local_dirty=1 WHERE id=:id AND deleted_at IS NULL") suspend fun changed(id: String): Int
}
