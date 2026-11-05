package com.qingling.writing.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qingling.writing.core.model.Revision
import com.qingling.writing.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 历史版本 ViewModel(P06):列表 + 手动快照 + 恢复。
 * 恢复前当前内容自动存为 BEFORE_RESTORE(docs/01 P06);恢复后 edit_seq 前进,
 * 若该章编辑器会话仍在,其下一次保存会得到 STALE_SEQUENCE 并提供「载入最新」。
 */
class HistoryViewModel(
    private val history: HistoryRepository,
    chapterId: String,
) : ViewModel() {

    val revisions: StateFlow<List<Revision>> = history.observeRevisions(chapterId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _restored = MutableStateFlow(false)
    val restored: StateFlow<Boolean> = _restored.asStateFlow()

    fun createManualSnapshot(chapterId: String) {
        viewModelScope.launch {
            try {
                history.createManualSnapshot(chapterId)
            } catch (e: com.qingling.writing.domain.NotFoundException) {
                // 章已删除:列表随流刷新,无需操作
            }
        }
    }

    fun restore(chapterId: String, revisionId: String) {
        viewModelScope.launch {
            try {
                history.restoreRevision(chapterId, revisionId)
                _restored.value = true
            } catch (e: com.qingling.writing.domain.NotFoundException) {
                // 章/版本已不存在:列表随流刷新
            }
        }
    }
}
