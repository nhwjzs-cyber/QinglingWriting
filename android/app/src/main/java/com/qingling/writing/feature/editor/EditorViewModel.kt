package com.qingling.writing.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qingling.writing.app.AppContainer
import com.qingling.writing.core.model.LocalSaveState
import com.qingling.writing.core.model.SaveResult
import com.qingling.writing.data.SaveCoordinatorImpl
import com.qingling.writing.domain.NotFoundException
import com.qingling.writing.domain.repository.OutlineRepository
import com.qingling.writing.domain.repository.WritingRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException

internal data class FormattingSource(val chapterId: String, val body: String)
internal enum class FormatApplyResult { APPLIED, STALE, SAVE_FAILED }

/** 编辑器页面状态:M2 按章打开;Loading(读取中)/ Ready / Missing(章不存在或已删除)。 */
sealed interface EditorUiState {
    data object Loading : EditorUiState
    data class Ready(val bookTitle: String, val chapterTitle: String) : EditorUiState
    data class EmptyBook(val bookId:String,val title:String,val showOutline:Boolean=false):EditorUiState
    data class Failure(val message:String):EditorUiState
    data object Missing : EditorUiState
}

/**
 * 编辑器 ViewModel(M2)。
 *
 * 会话归属(docs/03 §4):[session] 持有 TextFieldState 与编辑序列,旋转/切后台随
 * ViewModel 复用;进程重建后重读数据库(含光标恢复)。
 * 保存:SaveCoordinatorImpl(每章一个实例,绑定 viewModelScope)串行落库,
 * UI 只观察 LocalSaveState 显示真实状态——只有真实提交才显示「已保存到本机」。
 * 离开页面经 [requestClose] 冲刷:失败返回 false,调用方留在本页(重试/载入最新)。
 */
class EditorViewModel(
    private val container: AppContainer,
    private val outline: OutlineRepository,
    initialChapterId: String,
) : ViewModel() {

    /** 可变的当前章 id(switchToChapter 会改变它);初始值来自导航参数。 */
    private var chapterId: String = initialChapterId

    private val writing: WritingRepository = container.writingRepository

    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Loading)
    val uiState: StateFlow<EditorUiState> = _uiState

    private val _bookId = MutableStateFlow<String?>(null)

    /** 章所属书 id;加载完成后非空,供左右面板(目录/工具)与导出入口使用。 */
    val bookId: StateFlow<String?> = _bookId.asStateFlow()
    @OptIn(ExperimentalCoroutinesApi::class)
    val chapterOrder = _bookId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else writing.observeChapterOrder(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val sessionFlow = MutableStateFlow<EditorSession?>(null)
    internal val editorSession: StateFlow<EditorSession?> = sessionFlow.asStateFlow()

    // 会话对 UI 模块可见性:类型是 internal,属性也保持 internal;Screen 经 wordCount/saveState 观察。
    internal var session: EditorSession?
        get() = sessionFlow.value
        private set(value) {
            sessionFlow.value = value
        }

    /** 真实保存状态(来自当前会话的协调器);无会话时为 Idle。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val saveState: StateFlow<LocalSaveState> = sessionFlow
        .flatMapLatest { it?.saveState ?: flowOf(LocalSaveState.Idle) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalSaveState.Idle)

    /** 码点口径字数(docs/01 §7);随会话切换,后台计算。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val wordCount: StateFlow<Long> = sessionFlow
        .flatMapLatest { it?.wordCount ?: flowOf(0L) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    private val transition = Mutex()
    private val _switching = MutableStateFlow(false)
    internal val switching: StateFlow<Boolean> = _switching.asStateFlow()
    private var chapterScope: CoroutineScope? = null

    init {
        viewModelScope.launch { transition.withLock { load() } }
        viewModelScope.launch {
            sessionFlow.collectLatest { current ->
                if (current != null) writing.observeChapter(current.chapterId).collect { document ->
                    val ready = _uiState.value as? EditorUiState.Ready
                    if(document!=null && ready!=null && session===current) {
                        current.updateChapterTitle(document.title,document.editSeq)
                        if(ready.chapterTitle!=current.chapterTitle) _uiState.value=ready.copy(chapterTitle=current.chapterTitle)
                    }
                }
            }
        }
    }

    private suspend fun load(showLoading:Boolean=true) {
        if(showLoading)_uiState.value=EditorUiState.Loading
        try { kotlinx.coroutines.withTimeout(10_000) {
            val requestedBook=if(chapterId.startsWith("book:")) chapterId.removePrefix("book:") else writing.owningBookOfChapter(chapterId)
            if(requestedBook!=null) {
                _bookId.value=requestedBook
                val title=outline.observeBookTitle(requestedBook).first() ?: throw NotFoundException("book",requestedBook)
                val resolved=writing.resolveBookActiveChapter(requestedBook)
                if(resolved==null) {chapterScope?.cancel();session=null;_uiState.value=EditorUiState.EmptyBook(requestedBook,title);return@withTimeout}
            }
            val resolved=if(chapterId.startsWith("book:")) writing.resolveBookActiveChapter(chapterId.removePrefix("book:")) ?: throw NotFoundException("book",chapterId) else chapterId
            var document=writing.readChapter(resolved)
            val recovery=container.recoveryDrafts
            val pending=recovery.editor(resolved)
            if(pending!=null) {
                val record=recovery.read("editor:"+resolved)!!
                if(document.body==pending.body && (!pending.titleEdited || document.title==pending.title) && document.editSeq>=pending.editSeq) recovery.editorCommitted(resolved,document.editSeq)
                else if(document.editSeq==record.optLong("base") && pending.editSeq>document.editSeq && (!pending.titleEdited || !record.has("baseTitle") || record.optString("baseTitle")==document.title)) {
                    container.historyRepository.createBeforeRecoverySnapshot(resolved)
                    check(writing.saveDraft(pending) is SaveResult.Committed){"保护草稿尚未保存成功，请重试"}
                    recovery.editorCommitted(resolved,pending.editSeq);document=writing.readChapter(resolved)
                } else check(recovery.preserveEditorConflict(resolved,record)){"版本冲突草稿暂未保护成功，请检查存储空间后重试"}
            }
            val title=outline.observeBookTitle(document.bookId).first() ?: throw NotFoundException("book",document.bookId)
            val position=writing.readEditorPosition(document.id)
            writing.recordActive(document.id)
            val sessionScope=CoroutineScope(viewModelScope.coroutineContext+SupervisorJob(viewModelScope.coroutineContext[Job]))
            val coordinator=SaveCoordinatorImpl(chapterId=document.id,initialEditSeq=document.editSeq,writing=writing,
                history=container.historyRepository,scope=sessionScope,now={java.time.Instant.now().toString()},recovery=container.recoveryDrafts,initialTitle=document.title)
            val next=EditorSession(document,position,coordinator,sessionScope,{writing.saveEditorPosition(it)})
            chapterScope?.cancel();chapterScope=sessionScope;session=next;chapterId=document.id;_bookId.value=document.bookId
            _uiState.value=EditorUiState.Ready(title,document.title)
        }}catch(e:kotlinx.coroutines.TimeoutCancellationException){chapterScope?.cancel();session=null;_uiState.value=EditorUiState.Failure("打开稿件超时，请重试或回到书架")}
        catch(e:CancellationException){throw e}
        catch(e:NotFoundException){chapterScope?.cancel();session=null;_uiState.value=EditorUiState.Missing}
        catch(_:Exception){chapterScope?.cancel();session=null;_uiState.value=EditorUiState.Failure("暂时无法读取本机稿件，请重试")}
    }
    fun seedFirstChapter(){viewModelScope.launch{transition.withLock{
        val empty=_uiState.value as? EditorUiState.EmptyBook ?: return@withLock
        _uiState.value=EditorUiState.Loading
        try{writing.ensureFirstChapter(empty.bookId);_uiState.value=empty.copy(showOutline=true)}
        catch(e:CancellationException){throw e}catch(_:Exception){_uiState.value=empty}
    }}}

    fun undo() = session?.undo()

    fun redo() = session?.redo()

    internal fun formattingSource(): FormattingSource? = session?.let {
        FormattingSource(it.chapterId, it.editorState.text.toString())
    }

    internal suspend fun insertPlanningText(text: String): Boolean = transition.withLock {
        val current = session ?: return@withLock false
        val state = current.editorState
        if (text.isEmpty() || state.composition != null) return@withLock false
        val before = state.text.toString()
        val at = state.selection.min
        if (current.flush() !is SaveResult.Committed) return@withLock false
        try { container.historyRepository.createManualSnapshot(current.chapterId) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { return@withLock false }
        if (!current.applyFormatting(before,before.substring(0,at)+text+before.substring(at))) return@withLock false
        state.edit { selection = androidx.compose.ui.text.TextRange(at+text.length) }
        current.flush() // After insertion, retain the buffer and show existing save failure UI if needed.
        true
    }

    internal suspend fun applyFormatting(source: FormattingSource, replacement: String): FormatApplyResult = transition.withLock {
        val current = session ?: return@withLock FormatApplyResult.STALE
        if (current.chapterId != source.chapterId || current.editorState.text.toString() != source.body ||
            current.editorState.composition != null) return@withLock FormatApplyResult.STALE
        if (current.flush() !is SaveResult.Committed) return@withLock FormatApplyResult.SAVE_FAILED
        try {
            container.historyRepository.createManualSnapshot(current.chapterId)
        } catch (failure: CancellationException) { throw failure
        } catch (_: Exception) { return@withLock FormatApplyResult.SAVE_FAILED }
        if (!current.applyFormatting(source.body, replacement)) return@withLock FormatApplyResult.STALE
        // Keep failure visible in the existing save-status footer; the buffer and undo step survive.
        current.flush()
        FormatApplyResult.APPLIED
    }

    /**
     * 离开本页的冲刷门(docs/01 §3.6):先保存光标,再 flush。
     * 返回 true 才允许导航;false 时留在本页,底栏展示失败原因与操作。
     */
    suspend fun requestClose(): Boolean {
        val current = session ?: return true
        return current.flush() is SaveResult.Committed
    }

    internal suspend fun previewSplit(): com.qingling.writing.core.model.StructurePlan = transition.withLock {
        val current = checkNotNull(session) { "请等待章节载入完成" }
        val state = current.editorState
        check(state.composition == null && state.selection.collapsed) { "请先结束输入，并将光标放到拆分位置，不要选中文字" }
        val body = state.text.toString();val offset = state.selection.start
        check(current.flush() is SaveResult.Committed) { "当前章未保存成功，请先重试保存" }
        check(state.text.toString() == body && state.selection.start == offset) { "光标或正文已变化，请重新打开拆分" }
        container.structure.previewSplit(checkNotNull(_bookId.value),current.chapterId,body,offset)
    }

    internal suspend fun applyStructure(plan: com.qingling.writing.core.model.StructurePlan,title: String): String = transition.withLock {
        check(requestClose()) { "当前章未保存成功，请先重试保存" }
        _switching.value = true
        try {
            val target = container.structure.apply(plan,title)
            chapterScope?.cancel();session = null;chapterId = target
            load(showLoading = false)
            target
        } finally { _switching.value = false }
    }

    /** 保存失败后的显式重试(与再次输入等价,由协调器按序处理)。 */
    fun retrySave() {
        val current = session ?: return
        current.retry()
    }

    /** UX v0.2 A05:同页切章——替换 EditorSession 而非导航到新路由。 */
    suspend fun switchToChapter(newChapterId: String): Boolean = transition.withLock {
        if (newChapterId == chapterId) return@withLock true
        _switching.value = true
        try {
            if (!requestClose()) return@withLock false
            chapterId = newChapterId
            load(showLoading = false)
            _uiState.value is EditorUiState.Ready
        } finally { _switching.value = false }
    }

    /**
     * STALE_SEQUENCE / DELETED / 历史恢复后的「载入最新」:重建会话。
     * 这是一次显式用户动作(非重组副作用),允许重建 TextFieldState;
     * 当前缓冲如未提交,已被用户放弃(界面有明确文案,不静默)。
     */
    fun reloadLatest() {
        viewModelScope.launch {
            transition.withLock {
                chapterScope?.cancel()
                session = null
                load()
            }
        }
    }
}
