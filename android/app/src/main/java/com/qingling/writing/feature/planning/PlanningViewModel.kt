package com.qingling.writing.feature.planning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qingling.writing.core.model.*
import com.qingling.writing.data.PlanningRepository
import com.qingling.writing.domain.repository.OutlineRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PlanningDraft(val entry: PlanningEntry,val dirty: Boolean)

class PlanningViewModel(private val repository: PlanningRepository, outline: OutlineRepository,
    private val bookId: String,private val recovery:com.qingling.writing.data.RecoveryDraftStore?=null,
    private val readBack:suspend(String)->PlanningEntry?={repository.load(it)}) : ViewModel() {
    private val filter = MutableStateFlow("" to "")
    @OptIn(FlowPreview::class,ExperimentalCoroutinesApi::class)
    val entries = filter.debounce(150).flatMapLatest { (query,kind) -> repository.search(bookId,query,kind) }
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    fun filter(query: String,kind: String) { filter.value = query to kind }
    val volumes = outline.observeOutline(bookId).stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    private val _draft = MutableStateFlow<PlanningDraft?>(null)
    val draft = _draft.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val saving = Mutex()
    @OptIn(FlowPreview::class)
    private val autosave = viewModelScope.launch {
        _draft.debounce(1000).collect { if (it?.dirty == true) save() }
    }
    fun update(title: String? = null,body: String? = null) {
        val d = _draft.value ?: return
        _draft.value = d.copy(entry = d.entry.copy(title = title ?: d.entry.title,body = body ?: d.entry.body),dirty = true)
        recovery?.putPlanning(_draft.value!!.entry)
        _error.value = null
    }
    suspend fun save(): Boolean = saving.withLock {
        val before = _draft.value ?: return@withLock true
        if (!before.dirty) return@withLock true
        _busy.value = true
        var committed:PlanningEntry?=null
        try {
            val stored = repository.save(before.entry)
            committed=stored
            val verified=readBack(stored.id)
            check(verified!=null && verified.title==before.entry.title && verified.body==before.entry.body && verified.version==stored.version){"保存后回读不一致，当前文字和保护副本仍保留，请重试或复制"}
            val pending = _draft.value
            if (pending?.entry?.id == stored.id) {
                val stillDirty = pending.entry.title != stored.title || pending.entry.body != stored.body
                _draft.value = PlanningDraft(pending.entry.copy(version = stored.version,updatedAt = stored.updatedAt),stillDirty)
                if(stillDirty)recovery?.putPlanning(_draft.value!!.entry)else recovery?.planningCommitted(stored)
            }
            _error.value = null
            true
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            committed?.let{stored->_draft.value?.takeIf{it.entry.id==stored.id}?.let{pending->
                _draft.value=PlanningDraft(pending.entry.copy(version=stored.version,updatedAt=stored.updatedAt),true)
                recovery?.putPlanning(_draft.value!!.entry)
            }}
            _error.value = if (e is com.qingling.writing.data.PlanningConflict || e is IllegalArgumentException || e.message?.startsWith("保存后回读")==true)
                e.message ?: "内容或归属不正确，请检查后重试"
                else "保存未成功，当前文字仍保留，请检查本机空间后重试"
            false
        }
        finally { _busy.value = false }
    }
    suspend fun edit(entry: PlanningEntry): Boolean {
        if (!finishEdit()) return false
        val full = if (entry.version == 0L) entry else repository.load(entry.id)
        if (full == null) { _error.value = "条目已不存在，请刷新后重试";return false }
        val protected=recovery?.read("planning:"+full.id)
        val valid=protected!=null && protected.optLong("version")==full.version && protected.optString("bookId")==full.bookId && protected.optString("entryKind")==full.kind
        _draft.value = if(valid)PlanningDraft(full.copy(title=protected!!.getString("title"),body=protected.getString("text")),true)else PlanningDraft(full,false)
        return true
    }
    suspend fun openScope(kind: PlanningKind,chapterId: String? = null,volumeId: String? = null) {
        try { if (finishEdit()) edit(repository.scoped(bookId,kind,chapterId,volumeId)) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { _error.value = "无法打开条目，作品或章节可能已被删除" }
    }
    suspend fun saveAndVerify():Boolean {
        if(!save())return false
        val before=_draft.value ?: return true
        if(before.entry.version==0L && !before.dirty)return true
        return try{
            val stored=readBack(before.entry.id)
            val current=_draft.value
            if(stored==null || stored.title!=before.entry.title || stored.body!=before.entry.body || stored.version!=before.entry.version || current!=before || before.dirty){
                _error.value="保存结果尚未确认，当前文字仍保留，请重试或复制"
                _draft.value?.let{_draft.value=it.copy(dirty=true);recovery?.putPlanning(it.entry)}
                false
            }else true
        }catch(e:CancellationException){throw e}catch(_:Exception){_error.value="暂时无法回读保存结果，当前文字仍保留";_draft.value?.let{_draft.value=it.copy(dirty=true);recovery?.putPlanning(it.entry)};false}
    }
    suspend fun finishEdit(): Boolean {
        // Edits can arrive during a save; flush the latest buffer before allowing navigation.
        if (!saveAndVerify() || _draft.value?.dirty == true) return false
        _draft.value = null
        return true
    }
    suspend fun convert(volumeId: String): String? {
        if (!save() || _draft.value?.dirty == true) return null
        val d = _draft.value ?: return null
        _busy.value = true
        return try { repository.toChapter(d.entry,volumeId).id }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { _error.value = e.message ?: "生成章节未成功，便签保留"; null }
        finally { _busy.value = false }
    }
    private val _deleted = MutableStateFlow<PlanningEntry?>(null)
    val deleted = _deleted.asStateFlow()
    suspend fun deleteCurrent():Boolean = saving.withLock {
        val before=_draft.value ?: return@withLock false
        _busy.value=true
        try { _deleted.value=repository.deleteUserEntry(before.entry);recovery?.remove("planning:"+before.entry.id);_draft.value=null;_error.value=null;true }
        catch(e:CancellationException){throw e}
        catch(e:Exception){_error.value=e.message ?: "删除未完成，条目保留";false}
        finally{_busy.value=false}
    }
    suspend fun undoDelete():Boolean = saving.withLock {
        val removed=_deleted.value ?: return@withLock false
        _busy.value=true
        try { repository.undoDeleteUserEntry(removed);_deleted.value=null;_error.value=null;true }
        catch(e:CancellationException){throw e}
        catch(e:Exception){_error.value=e.message ?: "恢复未完成，请重试";false}
        finally{_busy.value=false}
    }
    fun discard() { _draft.value?.let{recovery?.remove("planning:"+it.entry.id)};_draft.value = null; _error.value = null }
    fun flushOnBackground() { viewModelScope.launch { save() } }
}
