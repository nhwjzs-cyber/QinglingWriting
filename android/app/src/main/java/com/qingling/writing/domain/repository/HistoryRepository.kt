package com.qingling.writing.domain.repository

import com.qingling.writing.core.model.ChapterDocument
import com.qingling.writing.core.model.Revision
import kotlinx.coroutines.flow.Flow

/**
 * 历史版本(P06;contracts restoreRevision 在 WritingRepository,列表/快照拆到本接口)。
 * 自动快照节流策略与 BEFORE_RESTORE 保全见实现与 docs/01 P06。
 */
interface HistoryRepository {

    /** 章的历史版本,新→旧;正文全量存储(简单可靠,M2 不做增量)。 */
    fun observeRevisions(chapterId: String): Flow<List<Revision>>

    /** 手动快照(MANUAL):对当前章内容立即建一份。 */
    suspend fun createManualSnapshot(chapterId: String)
    suspend fun createBeforeRecoverySnapshot(chapterId:String)=createManualSnapshot(chapterId)

    /**
     * 自动快照(AUTO,节流):由 SaveCoordinator 在提交成功后调用;
     * 同章两次 AUTO 间隔不足阈值时跳过,超过保留上限时滚动淘汰。
     * 实现内部读库判定,失败由调用方 runCatching 吸收(不影响保存链路)。
     */
    suspend fun createAutoSnapshotIfDue(chapterId: String, title: String, body: String)

    /**
     * 恢复历史(contracts restoreRevision):一个事务内先把**当前内容**存为 BEFORE_RESTORE,
     * 再用历史版本覆盖正文与标题、edit_seq 前进、更新字数;返回恢复后的文档。
     * 「恢复历史前保留当前内容」由此保证。
     */
    suspend fun restoreRevision(chapterId: String, revisionId: String): ChapterDocument
}
