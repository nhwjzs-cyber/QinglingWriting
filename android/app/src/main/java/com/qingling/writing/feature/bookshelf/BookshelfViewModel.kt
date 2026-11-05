package com.qingling.writing.feature.bookshelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qingling.writing.core.model.BookSummary
import com.qingling.writing.core.model.ChapterDocument
import com.qingling.writing.core.model.GuestSpace
import com.qingling.writing.domain.NotFoundException
import com.qingling.writing.domain.TitleValidationException
import com.qingling.writing.domain.repository.WritingRepository
import com.qingling.writing.domain.validation.TitleError
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * 书架 ViewModel(P01):订阅 Room 的书架汇总流(最近编辑倒序,SQL 保证);
 * 创建/重命名/删除是挂起操作,由 UI 作用域调用,校验错误以明确类型返回给对话框展示。
 */
class BookshelfViewModel(
    private val writing: WritingRepository,
    private val progress: com.qingling.writing.data.WritingProgressRepository? = null,
) : ViewModel() {

    val books: StateFlow<List<BookSummary>> = writing.observeBooks(GuestSpace.ID)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val writingDays = (progress?.days() ?: kotlinx.coroutines.flow.flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val bookGoals = (progress?.goals() ?: kotlinx.coroutines.flow.flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun chapterName(bookId: String): String = try {
        writing.resolveBookActiveChapter(bookId)?.let { writing.readChapter(it).title }.orEmpty()
    }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: NotFoundException) { "" }

    sealed interface CreateResult {
        data class Ok(val chapter: ChapterDocument) : CreateResult
        data class Invalid(val error: TitleError) : CreateResult
    }

    /** 一个事务内生成 书+第一卷+第一章(docs/01 §3.3);Ok 直接进入第一章编辑器。 */
    suspend fun createBook(title: String, author: String, description: String): CreateResult =
        try {
            CreateResult.Ok(writing.createBook(GuestSpace.ID, title, author, description))
        } catch (e: TitleValidationException) {
            CreateResult.Invalid(e.error)
        }

    /** 返回 null 表示成功;Invalid 为标题校验失败(对话框内展示)。 */
    suspend fun renameBook(bookId: String, newTitle: String): TitleError? =
        try {
            writing.renameBook(bookId, newTitle)
            null
        } catch (e: TitleValidationException) {
            e.error
        } catch (e: NotFoundException) {
            null // 删除竞态:书已不在,列表流会自行更新
        }

    /** 软删除整书(同一批次级联卷章);范围提示由 UI 对话框先行完成(docs/01 §4)。 */
    suspend fun deleteBook(bookId: String) {
        try {
            writing.softDeleteBook(bookId)
        } catch (e: NotFoundException) {
            // 删除竞态:忽略
        }
    }
    suspend fun pinBook(id: String,value: Boolean) = writing.pinBook(id,value)
    suspend fun archiveBook(id: String,value: Boolean) = writing.archiveBook(id,value)
}
