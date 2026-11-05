package com.qingling.writing

import com.qingling.writing.core.model.BookSummary
import com.qingling.writing.core.model.ChapterDocument
import com.qingling.writing.core.model.ChapterDraft
import com.qingling.writing.core.model.EditorPosition
import com.qingling.writing.core.model.SaveFailure
import com.qingling.writing.core.model.SaveResult
import com.qingling.writing.data.SaveCoordinatorImpl
import com.qingling.writing.domain.NotFoundException
import com.qingling.writing.domain.repository.HistoryRepository
import com.qingling.writing.domain.repository.WritingRepository
import com.qingling.writing.core.model.Revision
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SaveCoordinator 时序 JVM 单测(docs/05 §6:保存序列状态机):
 * 串行顺序、去抖合并、editSeq 防旧写、flush 语义、失败注入(含 DISK_FULL)、自动快照触发。
 * 全部走虚拟时间(StandardTestDispatcher),不依赖真实延时。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SaveCoordinatorTest {

    private class FakeHistory : HistoryRepository {
        val autoSnapshots = mutableListOf<String>()
        override fun observeRevisions(chapterId: String): Flow<List<Revision>> = emptyFlow()
        override suspend fun createManualSnapshot(chapterId: String) = Unit
        override suspend fun createAutoSnapshotIfDue(chapterId: String, title: String, body: String) {
            autoSnapshots += body
        }
        override suspend fun restoreRevision(chapterId: String, revisionId: String): ChapterDocument =
            throw NotFoundException("chapter", chapterId)
    }

    private class FakeWriting : WritingRepository {
        val saves = mutableListOf<ChapterDraft>()
        /** 非 null 时前 failTimes 次保存以此原因失败。 */
        var failWith: SaveFailure? = null
        var failCount = 0
        var dbEditSeq: Long = 0L

        override suspend fun saveDraft(draft: ChapterDraft): SaveResult {
            saves += draft
            val failure = failWith
            if (failure != null && saves.size <= failCount) {
                return SaveResult.Failed(draft.chapterId, draft.editSeq, failure)
            }
            dbEditSeq = draft.editSeq
            return SaveResult.Committed(draft.chapterId, draft.editSeq, bookLocalSeq = 1L)
        }

        override fun observeBooks(spaceId: String): Flow<List<BookSummary>> = emptyFlow()
        override suspend fun createBook(
            spaceId: String, title: String, author: String, description: String,
        ): ChapterDocument = throw NotImplementedError()
        override suspend fun readChapter(chapterId: String): ChapterDocument =
            throw NotFoundException("chapter", chapterId)
        override suspend fun renameBook(bookId: String, newTitle: String) = Unit
        override suspend fun softDeleteBook(bookId: String) = Unit
        override suspend fun restoreDeletionBatch(bookId: String, deletionBatchId: String) = Unit
        override suspend fun readEditorPosition(chapterId: String): EditorPosition? = null
        override suspend fun saveEditorPosition(position: EditorPosition) = Unit
        override suspend fun recordActive(chapterId: String) = Unit
        override suspend fun resolveLastActiveChapter(): String? = null
        override suspend fun resolveBookActiveChapter(bookId: String): String? = null
    }

    private fun TestScope.newCoordinator(
        writing: FakeWriting,
        history: FakeHistory,
        initialSeq: Long = 0L,
    ): SaveCoordinatorImpl =
        SaveCoordinatorImpl(
            chapterId = "ch-1",
            initialEditSeq = initialSeq,
            writing = writing,
            history = history,
            scope = backgroundScope,
            now = { "2026-10-31T00:00:00Z" },
            debounceMs = 0L, // 确定性:测试不走去抖延时,断言前 runCurrent 泵一次
        )

    private fun draft(seq: Long, body: String = "body-$seq") =
        ChapterDraft("ch-1", "第一章", body, seq)

    @Test
    fun `offer commits latest after debounce with ordered seq`() = runTest(StandardTestDispatcher()) {
        val writing = FakeWriting()
        val history = FakeHistory()
        val coordinator = newCoordinator(writing, history)

        coordinator.offer(draft(1))
        coordinator.offer(draft(2))
        coordinator.offer(draft(3)) // CONFLATED 通道合并到最新
        runCurrent()

        assertEquals(listOf(3L), writing.saves.map { it.editSeq })
        assertEquals("body-3", writing.saves.single().body)
        assertEquals("body-3", history.autoSnapshots.single())
        val state = coordinator.state.value
        assertTrue(state is com.qingling.writing.core.model.LocalSaveState.Saved)
    }

    @Test
    fun `failed save keeps state and retry offer commits`() = runTest(StandardTestDispatcher()) {
        val writing = FakeWriting().apply {
            failWith = SaveFailure.DISK_FULL
            failCount = 1
        }
        val coordinator = newCoordinator(writing, FakeHistory())

        coordinator.offer(draft(1))
        runCurrent()
        assertTrue(coordinator.state.value is com.qingling.writing.core.model.LocalSaveState.Failed)
        assertEquals(SaveFailure.DISK_FULL, (coordinator.state.value as com.qingling.writing.core.model.LocalSaveState.Failed).reason)

        writing.failWith = null // 「排除故障后重试」
        coordinator.offer(draft(1)) // 同一 editSeq 重试(内容未变)
        runCurrent()

        assertEquals(2, writing.saves.size)
        assertTrue(coordinator.state.value is com.qingling.writing.core.model.LocalSaveState.Saved)
    }

    @Test
    fun `stale save is rejected by repository contract and surfaced`() = runTest(StandardTestDispatcher()) {
        val writing = FakeWriting().apply { dbEditSeq = 9L }
        // 仓储契约:库内 seq 更新时返回 STALE(实现层守卫,JVM 侧用返回值模拟)
        writing.failWith = SaveFailure.STALE_SEQUENCE
        writing.failCount = Int.MAX_VALUE
        val coordinator = newCoordinator(writing, FakeHistory())

        coordinator.offer(draft(1))
        runCurrent()

        val failed = coordinator.state.value as com.qingling.writing.core.model.LocalSaveState.Failed
        assertEquals(SaveFailure.STALE_SEQUENCE, failed.reason)
    }

    @Test
    fun `flush with pending draft saves immediately`() = runTest(StandardTestDispatcher()) {
        val writing = FakeWriting()
        val coordinator = newCoordinator(writing, FakeHistory())

        coordinator.offer(draft(7, "flushed"))
        val result = coordinator.flush("ch-1", expectedEditSeq = 7)

        assertTrue(result is SaveResult.Committed)
        assertEquals("flushed", writing.saves.single().body)
    }

    @Test
    fun `flush without pending draft is a no-op commit`() = runTest(StandardTestDispatcher()) {
        val writing = FakeWriting()
        val coordinator = newCoordinator(writing, FakeHistory(), initialSeq = 4L)

        val result = coordinator.flush("ch-1", expectedEditSeq = 4L)

        assertTrue(result is SaveResult.Committed)
        assertEquals(0, writing.saves.size)
    }

    @Test
    fun `auto snapshot throttling is delegated to history`() = runTest(StandardTestDispatcher()) {
        val history = FakeHistory()
        val coordinator = newCoordinator(FakeWriting(), history)

        coordinator.offer(draft(1))
        runCurrent()
        coordinator.offer(draft(2))
        runCurrent()

        // FakeHistory 不实现节流,每次提交都收到——节流策略本身在 RoomHistoryRepository 仪器测试覆盖。
        assertEquals(listOf("body-1", "body-2"), history.autoSnapshots)
    }
    @Test
    fun `new input immediately clears a previous saved indication`() = runTest(StandardTestDispatcher()) {
        val coordinator = newCoordinator(FakeWriting(), FakeHistory())
        coordinator.offer(draft(1)); runCurrent()
        assertTrue(coordinator.state.value is com.qingling.writing.core.model.LocalSaveState.Saved)
        coordinator.offer(draft(2))
        val state = coordinator.state.value as com.qingling.writing.core.model.LocalSaveState.Saving
        assertEquals(2L, state.latestEditSeq)
    }

}
