package com.qingling.writing.feature.bookshelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qingling.writing.core.model.GuestSpace
import com.qingling.writing.core.model.RecycleEntry
import com.qingling.writing.domain.NotFoundException
import com.qingling.writing.domain.repository.RecycleBinRepository
import com.qingling.writing.domain.repository.WritingRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * 回收站 ViewModel(P06 回收部分):批次列表 + 按批次恢复。
 * 无自动永久清理、无彻底删除入口(docs/01 §4:满 30 天仍可恢复,只提示整理空间)。
 */
class RecycleBinViewModel(
    private val writing: WritingRepository,
    private val recycleBin: RecycleBinRepository,
) : ViewModel() {

    val entries: StateFlow<List<RecycleEntry>> = recycleBin.observeRecycleBin(GuestSpace.ID)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 按批次整体恢复;批次已被并发操作处理(竞态)时忽略,列表流会自行更新。 */
    suspend fun restore(entry: RecycleEntry) {
        try {
            writing.restoreDeletionBatch(entry.bookId, entry.batchId)
        } catch (e: NotFoundException) {
            // 墓碑已不存在:忽略,等列表流刷新
        }
    }
    suspend fun preparePurge(selected: List<RecycleEntry>) = recycleBin.preparePurge(GuestSpace.ID, selected)
    suspend fun purge(plan: com.qingling.writing.core.model.PurgePlan) = recycleBin.purge(plan)
}
