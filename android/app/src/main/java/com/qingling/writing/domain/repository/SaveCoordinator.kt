package com.qingling.writing.domain.repository

import com.qingling.writing.core.model.ChapterDraft
import com.qingling.writing.core.model.LocalSaveState
import com.qingling.writing.core.model.SaveResult
import kotlinx.coroutines.flow.Flow

/**
 * 串行保存协调器(contracts/WritingContracts.kt 的 SaveCoordinator)。
 * 单一有序写者:offer 打进来的草稿经去抖合并后按 editSeq 顺序落库;
 * 已落库 editSeq 之后的任何旧写都被仓储层拒绝(STALE_SEQUENCE)。
 * state 供 UI 显示 保存中/已保存到本机/保存失败——只有真实提交才显示"已保存"(docs/01 P05/§6)。
 */
interface SaveCoordinator {

    val state: Flow<LocalSaveState>

    /** 编辑缓冲变化时投递最新草稿;中间态会被合并,只保证最新内容按序提交。 */
    fun offer(draft: ChapterDraft)

    /** Durable input snapshot before the framework returns from accepting an input edit. */
    fun protectInput(draft: ChapterDraft) {}
    fun cancelInputProtection() {}

    /**
     * 冲刷:切章/返回/导出前调用。若 expectedEditSeq 已提交则立即返回成功;
     * 否则立即写当前草稿。失败时调用方保留正文、不离开页面(docs/01 §3.6)。
     */
    suspend fun flush(chapterId: String, expectedEditSeq: Long): SaveResult
}
