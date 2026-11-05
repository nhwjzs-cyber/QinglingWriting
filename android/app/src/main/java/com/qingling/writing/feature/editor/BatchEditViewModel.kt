package com.qingling.writing.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qingling.writing.core.model.*
import com.qingling.writing.data.RoomBatchEditRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class BatchUi(val busy: Boolean = false, val plan: BatchPlan? = null, val error: String? = null, val applied: Int = 0)
class BatchEditViewModel(private val repository: RoomBatchEditRepository) : ViewModel() {
    private val _ui = MutableStateFlow(BatchUi())
    val ui = _ui.asStateFlow()
    fun reset() { if (!_ui.value.busy) _ui.value = BatchUi() }
    fun preview(scope: BatchScope, operation: BatchOperation) {
        if (_ui.value.busy) return
        _ui.value = BatchUi(busy = true)
        viewModelScope.launch {
            try { _ui.value = BatchUi(plan = withContext(Dispatchers.IO) { repository.preview(scope, operation) })
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                val message = if (e is IllegalArgumentException) e.message else null
                _ui.value = BatchUi(error = message ?: "无法读取范围或生成预览，请缩小到单卷后重试。作品可能已被删除。")
            }
        }
    }
    fun apply(plan: BatchPlan) {
        if (_ui.value.busy || _ui.value.plan != plan) return
        _ui.value = _ui.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try { _ui.value = BatchUi(applied = withContext(Dispatchers.IO) { repository.apply(plan) })
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { _ui.value = BatchUi(error = "未应用修改，本次写入已回滚。正文或目录可能已变化，请重新生成预览。") }
        }
    }
}
