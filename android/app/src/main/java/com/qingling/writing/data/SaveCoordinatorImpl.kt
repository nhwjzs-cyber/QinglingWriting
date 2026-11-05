package com.qingling.writing.data

import com.qingling.writing.core.model.ChapterDraft
import com.qingling.writing.core.model.LocalSaveState
import com.qingling.writing.core.model.SaveResult
import com.qingling.writing.domain.repository.HistoryRepository
import com.qingling.writing.domain.repository.SaveCoordinator
import com.qingling.writing.domain.repository.WritingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * SaveCoordinator 的实现(contracts 语义;每章一个实例,由 EditorViewModel 创建并绑定其作用域)。
 *
 * 顺序与防旧写:offer 进入 CONFLATED 通道(中间态自动合并),单一写者循环去抖后取**最新**草稿
 * 顺序落库;仓储层 `edit_seq < :newSeq` 守卫保证任何旧写/旧确认不覆盖新版本。
 * 失败不吞:state 进入 Failed(原因明确),缓冲文本保留在编辑器,重试 = 再次输入或显式重试;
 * flush 与后台写共用 [mutex] 串行,切章/返回前调用,失败时调用方留在当前页(docs/01 §3.6)。
 * 自动快照:提交成功后按节流策略交给 HistoryRepository(失败不影响保存结果)。
 */
internal class SaveCoordinatorImpl(
    private val chapterId: String,
    initialEditSeq: Long,
    private val writing: WritingRepository,
    private val history: HistoryRepository,
    private val scope: CoroutineScope,
    private val now: () -> String,
    /** 输入去抖窗口;测试可传 0 走确定路径。 */
    private val debounceMs: Long = SAVE_DEBOUNCE_MS,
    private val recovery:RecoveryDraftStore? = null,
    initialTitle:String?=null,
) : SaveCoordinator {

    private val channel = Channel<ChapterDraft>(Channel.CONFLATED)
    private val mutex = Mutex()
    private val _state = MutableStateFlow<LocalSaveState>(LocalSaveState.Idle)
    override val state: StateFlow<LocalSaveState> = _state.asStateFlow()

    private var lastDraft: ChapterDraft? = null
    private var committedSeq: Long = initialEditSeq
    private var baseTitle:String?=initialTitle
    private var lastBookLocalSeq: Long = -1L
    private var lastCommittedResult: SaveResult.Committed? = null
    private var writerJob: Job? = null

    override fun offer(draft: ChapterDraft) {
        recovery?.putEditor(draft,committedSeq,baseTitle)
        lastDraft = draft
        _state.value = LocalSaveState.Saving(draft.chapterId, draft.editSeq)
        ensureWriter()
        channel.trySend(draft)
    }

    override fun protectInput(draft: ChapterDraft) {
        // The snapshot observer will enqueue the accepted edit and its final IME metrics.
        // Do not advance its sequence or counters before TextFieldState commits the edit.
        recovery?.putEditor(draft,committedSeq,baseTitle)
        _state.value = LocalSaveState.Saving(draft.chapterId,draft.editSeq)
    }

    override fun cancelInputProtection() {
        val pending=lastDraft
        if(pending!=null && pending.editSeq>committedSeq){
            if(recovery?.putEditor(pending,committedSeq,baseTitle)==false){
                _state.value=LocalSaveState.Failed(chapterId,pending.editSeq,com.qingling.writing.core.model.SaveFailure.STORAGE_UNAVAILABLE)
                return
            }
            _state.value=LocalSaveState.Saving(pending.chapterId,pending.editSeq)
        }else{
            if(recovery?.remove("editor:"+chapterId)==false){
                _state.value=LocalSaveState.Failed(chapterId,committedSeq,com.qingling.writing.core.model.SaveFailure.STORAGE_UNAVAILABLE)
                return
            }
            _state.value=lastCommittedResult?.let{LocalSaveState.Saved(chapterId,it.editSeq,now())} ?: LocalSaveState.Idle
        }
    }

    override suspend fun flush(chapterId: String, expectedEditSeq: Long): SaveResult =
        mutex.withLock {
            if (expectedEditSeq <= committedSeq) {
                // 本次会话内该版本已提交(或无更新的待写内容):直接复用上次提交结果。
                lastCommittedResult
                    ?: SaveResult.Committed(chapterId, committedSeq, lastBookLocalSeq)
            } else {
                val draft = lastDraft
                if (draft == null || draft.editSeq < expectedEditSeq) {
                    // 没有可写的草稿(打开后未编辑即返回):库内即为最新,视为已提交。
                    // bookLocalSeq 用 -1 哨兵表示「本次无新提交,书序列未变」。
                    committedSeq = maxOf(committedSeq, expectedEditSeq)
                    SaveResult.Committed(chapterId, committedSeq, -1L).also { lastCommittedResult = it }
                } else {
                    performSaveLocked(draft)
                }
            }
        }

    private fun ensureWriter() {
        if (writerJob?.isActive == true) return
        writerJob = scope.launch {
            for (incoming in channel) {
                var latest = incoming
                // 合并去抖窗口内的后续输入,只写最新一份。
                if (debounceMs > 0) delay(debounceMs)
                while (true) {
                    val next = channel.tryReceive().getOrNull() ?: break
                    latest = next
                }
                mutex.withLock {
                    if (latest.editSeq > committedSeq) {
                        performSaveLocked(latest)
                    }
                }
            }
        }
    }

    /** 调用方需持有 [mutex];返回落库结果并把状态推给 UI。 */
    private suspend fun performSaveLocked(draft: ChapterDraft): SaveResult {
        _state.value = LocalSaveState.Saving(draft.chapterId, draft.editSeq)
        val result = writing.saveDraft(draft)
        when (result) {
            is SaveResult.Committed -> {
                recovery?.editorCommitted(draft.chapterId,result.editSeq,if(draft.titleEdited)draft.title else null)
                if(draft.titleEdited)baseTitle=draft.title
                committedSeq = result.editSeq
                lastBookLocalSeq = result.bookLocalSeq
                lastCommittedResult = result
                val pending = lastDraft
                _state.value = if (pending != null && pending.editSeq > result.editSeq) {
                    LocalSaveState.Saving(pending.chapterId, pending.editSeq)
                } else LocalSaveState.Saved(draft.chapterId, result.editSeq, now())
                // 自动快照节流;失败不影响保存结果(P06:历史与保存是两条链路)。
                runCatching {
                    history.createAutoSnapshotIfDue(draft.chapterId, draft.title, draft.body)
                }
            }
            is SaveResult.Failed -> {
                _state.value = LocalSaveState.Failed(draft.chapterId, result.editSeq, result.reason)
            }
        }
        return result
    }

    companion object {
        /** 输入去抖:停顿该时长后提交一次,避免逐键落库(docs/01 P05「无需手动保存」)。 */
        const val SAVE_DEBOUNCE_MS = 600L
    }
}
