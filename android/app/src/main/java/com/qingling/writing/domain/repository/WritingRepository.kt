package com.qingling.writing.domain.repository

import com.qingling.writing.core.model.BookSummary
import com.qingling.writing.core.model.ChapterDocument
import com.qingling.writing.core.model.ChapterDraft
import com.qingling.writing.core.model.EditorPosition
import kotlinx.coroutines.flow.Flow

/**
 * 书与章的核心读写(contracts/WritingContracts.kt 的 WritingRepository)。
 * 事务边界:createBook 必须在一个事务内生成 书+第一卷+第一章(contracts 头注;docs/06 M1)。
 */
interface WritingRepository {
    suspend fun pinBook(bookId: String,value: Boolean) { error("置顶暂不可用") }
    suspend fun archiveBook(bookId: String,value: Boolean) { error("归档暂不可用") }
    fun observeChapterOrder(bookId: String): Flow<List<String>> = kotlinx.coroutines.flow.flowOf(emptyList())

    fun observeBooks(spaceId: String): Flow<List<BookSummary>>

    /** 一个事务:书 + 第一卷「第一卷」+ 第一章「未命名章节」;返回直接进入编辑器的章。 */
    suspend fun createBook(
        spaceId: String,
        title: String,
        author: String,
        description: String,
    ): ChapterDocument

    suspend fun owningBookOfChapter(chapterId:String):String? = null
    suspend fun ensureFirstChapter(bookId:String):ChapterDocument {error("无法创建章节")}

    suspend fun createEmptyBook(spaceId:String,title:String,author:String,description:String):String { error("空书创建不可用") }

    suspend fun readChapter(chapterId: String): ChapterDocument
    fun observeChapter(chapterId:String): Flow<ChapterDocument?> = kotlinx.coroutines.flow.flowOf(null)

    /**
     * 正文落库(contracts saveDraft;M2 SaveCoordinator 的唯一写路径)。
     * editSeq 防旧写:仅当库内 edit_seq < draft.editSeq 才提交,否则 STALE_SEQUENCE;
     * 章已删除返回 DELETED;磁盘类错误映射 DISK_FULL/STORAGE_UNAVAILABLE(docs/05 §2 明确错误类型)。
     */
    suspend fun saveDraft(draft: ChapterDraft): com.qingling.writing.core.model.SaveResult

    /** 重命名书(书架 P01);触碰书 updated_at,驱动书架最近编辑倒序。 */
    suspend fun renameBook(bookId: String, newTitle: String)

    /**
     * 软删除整书:同一事务内,把书与其所有存活的卷/章打上同一个 deletionBatchId
     * (docs/01 §4:级联只标记此前存活的项;删除保留墓碑与正文,不永久删除)。
     */
    suspend fun softDeleteBook(bookId: String)

    /**
     * 按删除批次恢复(docs/01 §4:恢复父项不复活此前已单独删除的子项——
     * 批次内的行恢复,更早单独删除的行保持墓碑)。批次必须属于 [bookId]。
     */
    suspend fun restoreDeletionBatch(bookId: String, deletionBatchId: String)

    /** 光标/滚动位置读写(M2;恢复时按文本长度 clamp 由编辑器负责)。 */
    suspend fun readEditorPosition(chapterId: String): EditorPosition?

    suspend fun saveEditorPosition(position: EditorPosition)

    // ---- UX v0.2:最近激活记录(冷启动/点书续写) ----

    /** 章被打开且确认存活后调用;写 space_active + book_active + books.last_active_chapter_id。 */
    suspend fun recordActive(chapterId: String)

    /** 冷启动解析:上次激活章 → 同书首存活章 → null(书架空态或数据不可用)。 */
    suspend fun resolveLastActiveChapter(): String?

    /** 点书续写解析:该书 lastActiveChapterId → 首存活章 → null(空态)。 */
    suspend fun resolveBookActiveChapter(bookId: String): String?
}
