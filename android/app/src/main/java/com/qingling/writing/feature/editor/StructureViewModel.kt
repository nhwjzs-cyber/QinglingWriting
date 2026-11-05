package com.qingling.writing.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qingling.writing.core.model.*
import com.qingling.writing.data.ChapterStructureRepository
import com.qingling.writing.domain.repository.OutlineRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class StructureUi(val plan: StructurePlan? = null,val busy: Boolean = false,val error: String? = null,val applied: Boolean = false)
class StructureViewModel(private val repository: ChapterStructureRepository,outline: OutlineRepository,
    private val bookId: String,private val commit: suspend (StructurePlan,String) -> String) : ViewModel() {
    val volumes = outline.observeOutline(bookId).stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    private val _ui = MutableStateFlow(StructureUi())
    val ui = _ui.asStateFlow()
    fun begin(split: StructurePlan?) { if (!_ui.value.busy) _ui.value = StructureUi(plan = split) }
    fun clear() { if (!_ui.value.busy) _ui.value = StructureUi() }
    fun preview(ids: List<String>,titles: Boolean) {
        if (_ui.value.busy) return
        _ui.value = StructureUi(busy = true)
        viewModelScope.launch {
            try { _ui.value = StructureUi(plan = repository.previewMerge(bookId,ids,titles)) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _ui.value = StructureUi(error = e.message ?: "无法生成预览，请重新选择") }
        }
    }
    fun apply(title: String) {
        val before = _ui.value
        val plan = before.plan ?: return
        if (before.busy) return
        _ui.value = before.copy(busy = true,error = null)
        viewModelScope.launch {
            try { commit(plan,title); _ui.value = StructureUi(applied = true) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _ui.value = before.copy(error = if (e is IllegalStateException || e is IllegalArgumentException)
                    e.message ?: "预览已失效，请重新生成"
                    else "操作或刷新未完成，请重新打开目录核对内容，再决定是否重试")
            }
        }
    }
}
