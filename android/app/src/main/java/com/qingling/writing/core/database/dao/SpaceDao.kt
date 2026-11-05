package com.qingling.writing.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.qingling.writing.core.database.entities.SpaceEntity

@Dao
interface SpaceDao {

    /** 幂等建游客空间:固定常量 id + INSERT OR IGNORE(docs/05 §3:UUID 为身份)。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(space: SpaceEntity)

    @Query("SELECT * FROM spaces WHERE id = :id")
    suspend fun byId(id: String): SpaceEntity?
}
