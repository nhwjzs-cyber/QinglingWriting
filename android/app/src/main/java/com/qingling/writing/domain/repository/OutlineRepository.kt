package com.qingling.writing.domain.repository

import com.qingling.writing.core.model.ChapterDocument
import com.qingling.writing.core.model.OutlineVolume
import kotlinx.coroutines.flow.Flow

/**
 * 卷章结构与目录(P02;contracts「GLM 可在保持语义下拆文件」,本接口承接卷章操作)。
 * 排序规则(docs/01 §4):移动在事务内重排同级存活项的 position;第一版无拖拽,仅 上移/下移/跨卷移动;
 * 不存在独立于卷的章;同名显示合法,内部 UUID 区分。
 */
interface OutlineRepository {
    suspend fun placeVolume(bookId: String,volumeId: String,index: Int) { error("排序暂不可用") }

    /** 目录树快照流:卷按 position,卷内章按 position;只含存活项。 */
    fun observeOutline(bookId: String): Flow<List<OutlineVolume>>

    /** 书名流(编辑器/目录标题用);书不存在或已删除时发 null。 */
    fun observeBookTitle(bookId: String): Flow<String?>

    suspend fun createVolume(bookId: String, title: String)

    /** 新章默认标题由 UI 传「未命名章节」(docs/01 §3.4);返回可直接打开的章。 */
    suspend fun createChapter(bookId: String, volumeId: String, title: String): ChapterDocument

    /** 事务内复制正文为独立草稿，插入原章之后；不复制历史、完成标记或写作统计。 */
    suspend fun duplicateChapter(chapterId: String, title: String): ChapterDocument

    suspend fun renameVolume(volumeId: String, newTitle: String)

    suspend fun renameChapter(chapterId: String, newTitle: String)

    /** 卷上移/下移:[offset] 为 -1(上移)或 +1(下移);越界为无操作。 */
    suspend fun moveVolume(bookId: String, volumeId: String, offset: Int)

    /** 跨卷移动(contracts moveChapter):[targetIndex] 越界时夹到末尾;目标卷必须同书。 */
    suspend fun moveChapter(chapterId: String, targetVolumeId: String, targetIndex: Int)

    /** 章在卷内上移/下移;越界为无操作。 */
    suspend fun moveChapterWithinVolume(chapterId: String, offset: Int)

    /** 软删除卷:同一批次级联标记其所有存活章(先提示范围由 UI 完成)。 */
    suspend fun softDeleteVolume(volumeId: String)

    /** 软删除单章(仅该章一个批次)。 */
    suspend fun softDeleteChapter(chapterId: String)
}
