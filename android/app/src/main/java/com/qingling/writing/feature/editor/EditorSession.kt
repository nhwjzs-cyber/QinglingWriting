package com.qingling.writing.feature.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import com.qingling.writing.core.model.ChapterDocument
import com.qingling.writing.core.model.ChapterDraft
import com.qingling.writing.core.model.EditorPosition
import com.qingling.writing.core.model.SaveResult
import com.qingling.writing.domain.repository.SaveCoordinator
import com.qingling.writing.domain.validation.WordCounter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

/**
 * 单章编辑会话(docs/03 §4):持有当前章的 state-based TextFieldState 与编辑序列,
 * 旋转/切后台随 ViewModel 复用;进程重建后由 EditorViewModel 重新读库重建。
 *
 * 组合输入期间不做任何 trim/替换/重建(docs/03 §4):所有修改只通过 [offerDraft] 读取
 * 快照文本投递给 SaveCoordinator,从不回写 TextFieldState;撤销/重做的程序化修改
 * 走 [applySnap](显式用户动作),并同步 lastOfferedText 使快照流不产生额外保存。
 * 光标以 UTF-16 偏移保存,恢复时按文本长度 clamp;字数为码点口径(docs/01 §7)。
 */
internal class EditorSession(
    initial: ChapterDocument,
    initialPosition: EditorPosition?,
    private val coordinator: SaveCoordinator,
    private val scope: CoroutineScope,
    private val onPositionSave: suspend (EditorPosition) -> Unit,
) {
    val chapterId: String = initial.id
    val bookId: String = initial.bookId
    var chapterTitle: String = initial.title
        private set
    val titleState = TextFieldState(com.qingling.writing.core.model.ChapterNaming.editorText(initial.title))
    private var lastOfferedTitle = titleState.text.toString()
    private var pendingTitle:String? = null
    private var titleEditedAtSeq = initial.editSeq
    fun updateChapterTitle(title:String,storedSeq:Long) {
        // An old database emission must not overwrite newer typing or IME composition.
        val raw=titleState.text.toString()
        if(raw!=lastOfferedTitle || titleState.composition!=null) return
        if(pendingTitle!=null && storedSeq<titleEditedAtSeq) return
        chapterTitle=title
        pendingTitle=null
        if(com.qingling.writing.core.model.ChapterNaming.fromEditor(raw)!=title) {
            val replacement=com.qingling.writing.core.model.ChapterNaming.editorText(title)
            lastOfferedTitle=replacement
            if(raw!=replacement) titleState.edit { replace(0,length,replacement) }
        }
    }
    fun sessionMetrics() = inputMeter.snapshot()

    var editorState: TextFieldState = TextFieldState(initial.body.ifEmpty { ParagraphIndentation.PREFIX },
        initialSelection=TextRange(if(initial.body.isEmpty()) ParagraphIndentation.PREFIX.length else initial.body.length))
        private set

    private var editSeq: Long = initial.editSeq
    private var lastOfferedText: String = initial.body
    private var lastOfferedCountsAsWriting=false
    private val inputMeter=com.qingling.writing.core.model.InputMeter(initial.editSeq,editorState.text.toString())
    private var lastOfferedMetrics=inputMeter.snapshot()
    private var protectingUnobservedInput=false
    val bodyInputProtection=InputTransformation { protectInput(body=asCharSequence().toString()) }
    val titleInputProtection=InputTransformation { protectInput(title=asCharSequence().toString()) }
    private fun protectInput(body:String=editorState.text.toString(),title:String=titleState.text.toString()) {
        if(body==lastOfferedText && title==lastOfferedTitle){
            if(protectingUnobservedInput)coordinator.cancelInputProtection()
            protectingUnobservedInput=false
            return
        }
        protectingUnobservedInput=true
        val normalized=com.qingling.writing.core.model.ChapterNaming.fromEditor(title)
        coordinator.protectInput(ChapterDraft(chapterId,normalized,body,editSeq+1,
            countAsWriting=body.isNotBlank() || lastOfferedText.isNotBlank(),
            titleEdited=normalized!=chapterTitle || pendingTitle!=null))
    }
    fun setWritingActive(active: Boolean) { inputMeter.setActive(active) }

    /** Queue the latest text synchronously before launching the immediate background write. */
    fun checkpoint() {
        offerCurrentDraft(includeComposingTitle=true)
        scope.launch { flush() }
    }

    // ---- 撤销/重做(限当前会话,docs/06 M2)----
    private data class Snap(val text: String, val selStart: Int, val selEnd: Int)

    private val undoStack = ArrayDeque<Snap>()
    private val redoStack = ArrayDeque<Snap>()
    private var lastStable: Snap = Snap(editorState.text.toString(),editorState.selection.start,editorState.selection.end)
    private var quiesceJob: Job? = null
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    val saveState: kotlinx.coroutines.flow.Flow<com.qingling.writing.core.model.LocalSaveState> =
        coordinator.state

    /** 码点口径字数(docs/01 §7),后台计算,停顿 300ms 后刷新。 */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val wordCount: StateFlow<Long> = snapshotFlow { editorState.text.toString() }
        .distinctUntilChanged()
        .debounce(WORD_COUNT_DEBOUNCE_MS)
        .map { WordCounter.count(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), 0L)

    init {
        // 恢复光标:按当前文本长度 clamp(docs/03 §4);滚动偏移 M2 记 0。
        initialPosition?.let { pos ->
            val len = editorState.text.length
            val start = if(initial.body.isEmpty()) len else pos.selectionStart.coerceIn(0, len)
            val end = if(initial.body.isEmpty()) len else pos.selectionEnd.coerceIn(0, len)
            editorState.edit {
                selection = TextRange(start, end)
            }
        }
        scope.launch {
            snapshotFlow { Triple(editorState.text.toString() to editorState.composition,titleState.text.toString(),titleState.composition) }
                .distinctUntilChanged()
                .collect {
                    offerCurrentDraft()
                    scheduleStableCapture()
                }
        }
    }

    private fun scheduleStableCapture() {
        quiesceJob?.cancel()
        quiesceJob = scope.launch {
            kotlinx.coroutines.delay(STABLE_CAPTURE_MS)
            captureStable()
        }
    }

    /** 停顿后的稳定态进撤销栈;撤销/恢复的程序化修改经 lastStable 同步,不会重复入栈。 */
    private fun captureStable() {
        val snap = currentSnap()
        if (snap.text == lastStable.text) return
        undoStack.addLast(lastStable)
        if (undoStack.size > UNDO_LIMIT) undoStack.removeFirst()
        redoStack.clear()
        lastStable = snap
        updateFlags()
    }

    fun undo() {
        offerCurrentDraft()
        quiesceJob?.cancel()
        captureStable()
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(currentSnap())
        applySnap(previous)
        updateFlags()
    }

    fun redo() {
        quiesceJob?.cancel()
        captureStable()
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(currentSnap())
        applySnap(next)
        updateFlags()
    }

    private fun currentSnap(): Snap =
        Snap(
            editorState.text.toString(),
            editorState.selection.start,
            editorState.selection.end,
        )

    private fun applySnap(snap: Snap) {
        inputMeter.ignore(snap.text)
        editorState.edit {
            replace(0, length, snap.text)
            val len = length
            selection = TextRange(snap.selStart.coerceIn(0, len), snap.selEnd.coerceIn(0, len))
        }
        offerCurrentDraft()
        lastStable = snap
    }

    private fun updateFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    /** Preview uses a frozen input. Refuse stale previews and active IME composition. */
    fun applyFormatting(expectedSource: String, replacement: String): Boolean {
        if (editorState.composition != null || editorState.text.toString() != expectedSource) return false
        if (replacement == expectedSource) return true
        offerCurrentDraft()
        quiesceJob?.cancel()
        captureStable() // Preserve unobserved typing before making one separate undo step.
        val before = currentSnap()
        undoStack.addLast(before)
        if (undoStack.size > UNDO_LIMIT) undoStack.removeFirst()
        redoStack.clear()
        applySnap(Snap(replacement, before.selStart.coerceAtMost(replacement.length), before.selEnd.coerceAtMost(replacement.length)))
        updateFlags()
        return true
    }

    /** 保存失败后的显式重试:以当前缓冲内容、同一 editSeq 重新进入写者队列。 */
    fun retry() {
        offerCurrentDraft(includeComposingTitle=true)
        coordinator.offer(ChapterDraft(chapterId, pendingTitle ?: chapterTitle, editorState.text.toString(), editSeq, countAsWriting = lastOfferedCountsAsWriting,inputMetrics=lastOfferedMetrics,titleEdited=pendingTitle!=null))
    }

    /** 返回/切章/导出前的冲刷:保存光标位置,再协调器 flush(失败时调用方留在本页)。 */
    suspend fun flush(): SaveResult {
        // Include edits made before snapshotFlow has had a scheduling turn.
        offerCurrentDraft(includeComposingTitle=true)
        runCatching {
            onPositionSave(currentPosition())
        }
        return coordinator.flush(chapterId, editSeq)
    }

    private fun offerCurrentDraft(includeComposingTitle:Boolean=false) {
        val text=editorState.text.toString()
        val metrics=inputMeter.observe(text,editorState.composition!=null)
        val rawTitle=if(includeComposingTitle || titleState.composition==null) titleState.text.toString() else lastOfferedTitle
        val titleChanged=rawTitle!=lastOfferedTitle
        if(text!=lastOfferedText || metrics!=lastOfferedMetrics || titleChanged) {
            protectingUnobservedInput=false
            editSeq+=1
            if(titleChanged) {
                lastOfferedTitle=rawTitle
                pendingTitle=com.qingling.writing.core.model.ChapterNaming.fromEditor(rawTitle)
                titleEditedAtSeq=editSeq
            }
            lastOfferedCountsAsWriting=text.isNotBlank() || lastOfferedText.isNotBlank()
            lastOfferedText=text
            lastOfferedMetrics=metrics
            coordinator.offer(ChapterDraft(chapterId,pendingTitle ?: chapterTitle,text,editSeq,
                countAsWriting=lastOfferedCountsAsWriting,inputMetrics=metrics,titleEdited=pendingTitle!=null))
        }
    }

    fun currentPosition(): EditorPosition = EditorPosition(
        chapterId = chapterId,
        selectionStart = editorState.selection.start,
        selectionEnd = editorState.selection.end,
        scrollOffset = 0,
    )

    companion object {
        private const val STABLE_CAPTURE_MS = 800L
        private const val UNDO_LIMIT = 50
        private const val WORD_COUNT_DEBOUNCE_MS = 300L
    }
}
