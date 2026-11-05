package com.qingling.writing.feature.bookshelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qingling.writing.core.datastore.SettingsStore
import com.qingling.writing.core.model.GuestSpace
import com.qingling.writing.data.WritingProgressRepository
import com.qingling.writing.domain.repository.WritingRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class DashboardViewModel(writing: WritingRepository,private val progress: WritingProgressRepository,
    private val settings: SettingsStore) : ViewModel() {
    val books = writing.observeBooks(GuestSpace.ID).stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val days = progress.days().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val inputDays = progress.inputDays().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val bookDays = progress.bookDays().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val goals = progress.goals().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val dailyGoal = settings.dailyGoal.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),2000)
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val writes = kotlinx.coroutines.channels.Channel<suspend () -> Unit>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    init {
        viewModelScope.launch {
            for (block in writes) {
                _busy.value = true; _message.value = null
                try { block(); _message.value = "目标已保存" }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { _message.value = if(e is IllegalArgumentException || e is IllegalStateException) e.message else "保存未成功，请检查本机空间后重试" }
                finally { _busy.value = false }
            }
        }
    }
    fun saveGoal(bookId: String,value: String) = save {
        val words = value.toLongOrNull() ?: error("请输入有效整数")
        progress.setGoal(bookId,words)
    }
    fun saveDaily(value: String) = save {
        val words = value.toIntOrNull() ?: error("请输入有效整数")
        require(words in 0..1_000_000) { "每日目标为 0 到 1000000，0 为关闭" }
        settings.setDailyGoal(words)
    }
    private fun save(block: suspend () -> Unit) {
        writes.trySend(block)
    }
}
