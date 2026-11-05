package com.qingling.writing.feature.outline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qingling.writing.core.model.ChapterDocument
import com.qingling.writing.core.model.OutlineVolume
import com.qingling.writing.domain.NotFoundException
import com.qingling.writing.domain.TitleValidationException
import com.qingling.writing.domain.repository.OutlineRepository
import com.qingling.writing.domain.validation.TitleError
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 目录 ViewModel(P02):数据驱动的卷章树 + 增/改/移/删操作。
 * 所有操作是挂起函数,由 UI 作用域调用;标题校验错误以 [OperationResult.Invalid] 回给对话框。
 */
class OutlineViewModel(
    private val outline: OutlineRepository,
    bookId: String,
) : ViewModel() {

    val volumes: StateFlow<List<OutlineVolume>> = outline.observeOutline(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val bookTitle: StateFlow<String?> = outline.observeBookTitle(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    sealed interface OperationResult {
        data object Ok : OperationResult
        data class Invalid(val error: TitleError) : OperationResult
        data class Created(val chapter: ChapterDocument) : OperationResult
    }

    suspend fun createVolume(bookId: String, title: String): OperationResult =
        try {
            outline.createVolume(bookId, title)
            OperationResult.Ok
        } catch (e: TitleValidationException) {
            OperationResult.Invalid(e.error)
        }

    suspend fun createChapter(bookId: String, volumeId: String, title: String): OperationResult =
        try {
            OperationResult.Created(outline.createChapter(bookId, volumeId, title))
        } catch (e: TitleValidationException) {
            OperationResult.Invalid(e.error)
        } catch (e: NotFoundException) {
            OperationResult.Ok // 卷已被删除等竞态:树流会自行更新
        }

    suspend fun duplicateChapter(chapterId: String, title: String): OperationResult =
        try { OperationResult.Created(outline.duplicateChapter(chapterId, title)) }
        catch (e: TitleValidationException) { OperationResult.Invalid(e.error) }

    suspend fun renameVolume(volumeId: String, newTitle: String): OperationResult =
        try {
            outline.renameVolume(volumeId, newTitle)
            OperationResult.Ok
        } catch (e: TitleValidationException) {
            OperationResult.Invalid(e.error)
        } catch (e: NotFoundException) {
            OperationResult.Ok
        }

    suspend fun renameChapter(chapterId: String, newTitle: String): OperationResult =
        try {
            outline.renameChapter(chapterId, newTitle)
            OperationResult.Ok
        } catch (e: TitleValidationException) {
            OperationResult.Invalid(e.error)
        } catch (e: NotFoundException) {
            OperationResult.Ok
        }

    suspend fun moveVolume(bookId: String, volumeId: String, offset: Int) {
        try {
            outline.moveVolume(bookId, volumeId, offset)
        } catch (e: NotFoundException) {
            // 竞态:忽略
        }
    }

    suspend fun moveChapterWithinVolume(chapterId: String, offset: Int) {
        try {
            outline.moveChapterWithinVolume(chapterId, offset)
        } catch (e: NotFoundException) {
            // 竞态:忽略
        }
    }

    suspend fun moveChapterToVolume(chapterId: String, targetVolumeId: String) {
        try {
            outline.moveChapter(chapterId, targetVolumeId, Int.MAX_VALUE) // 追加到目标卷末尾
        } catch (e: NotFoundException) {
            // 竞态:忽略
        }
    }
    suspend fun placeChapter(chapterId: String, volumeId: String, index: Int) {
        try { outline.moveChapter(chapterId, volumeId, index) }
        catch (_: NotFoundException) { }
    }
    suspend fun placeVolume(bookId: String,volumeId: String,index: Int) = outline.placeVolume(bookId,volumeId,index)

    suspend fun deleteVolume(volumeId: String) {
        try {
            outline.softDeleteVolume(volumeId)
        } catch (e: NotFoundException) {
            // 竞态:忽略
        }
    }

    suspend fun deleteChapter(chapterId: String) {
        try {
            outline.softDeleteChapter(chapterId)
        } catch (e: NotFoundException) {
            // 竞态:忽略
        }
    }
}
