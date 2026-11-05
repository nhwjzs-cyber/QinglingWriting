package com.qingling.writing.domain.repository

import com.qingling.writing.core.model.RecycleEntry
import kotlinx.coroutines.flow.Flow

/**
 * 回收站(P06 的回收部分;M1 无自动永久清理、无彻底删除入口)。
 * 列出空间内所有删除批次:整书、存活书下的卷、存活卷所在书存活时的章;
 * 被整书级联删除的卷/章由书的条目代表,不单列。
 */
interface RecycleBinRepository {

    fun observeRecycleBin(spaceId: String): Flow<List<RecycleEntry>>
    suspend fun preparePurge(spaceId: String, entries: List<RecycleEntry>): com.qingling.writing.core.model.PurgePlan
    suspend fun purge(plan: com.qingling.writing.core.model.PurgePlan)
}
