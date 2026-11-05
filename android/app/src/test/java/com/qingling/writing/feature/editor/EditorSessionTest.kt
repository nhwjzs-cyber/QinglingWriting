package com.qingling.writing.feature.editor

import com.qingling.writing.core.model.*
import com.qingling.writing.domain.repository.SaveCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import androidx.compose.runtime.snapshots.Snapshot
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorSessionTest {
    @Test fun backgroundCheckpointQueuesImmediateTextAndFlushesPosition() = runTest {
        val recorder = Recorder()
        var savedPosition: EditorPosition? = null
        val session = EditorSession(ChapterDocument("ch", "book", "vol", "章", "原文", 3),
            null, recorder, backgroundScope, { savedPosition = it })
        session.editorState.edit { replace(0, length, "后台前输入😀"); selection = androidx.compose.ui.text.TextRange(3) }
        session.checkpoint()
        assertEquals("后台前输入😀", recorder.offered.single().body)
        runCurrent()
        assertEquals(1, recorder.flushes)
        assertEquals(3, savedPosition?.selectionStart)
        assertEquals("后台前输入😀", session.editorState.text.toString())
    }

    @Test fun failedBackgroundCheckpointKeepsBufferAndCanRetry() = runTest {
        val recorder = Recorder().apply { failure = true }
        val session = EditorSession(ChapterDocument("ch", "book", "vol", "章", "原文", 3), null, recorder, backgroundScope, {})
        session.editorState.edit { replace(0, length, "未保存稿件") }
        session.checkpoint(); runCurrent()
        assertEquals("未保存稿件", session.editorState.text.toString())
        recorder.failure = false
        assertTrue(session.flush() is SaveResult.Committed)
        assertEquals(1, recorder.offered.size)
    }
    @Test fun formattingUndoAndRetryKeepCumulativeInputWithoutCountingGeneratedText() = runTest {
        val recorder=Recorder()
        val session=EditorSession(ChapterDocument("ch","book","vol","章","",0),null,recorder,backgroundScope,{})
        session.editorState.edit {replace(0,length,"甲😀")}
        session.flush()
        assertEquals(2L,recorder.offered.last().inputMetrics?.inserted)
        assertTrue(session.applyFormatting("甲😀","　　甲😀"))
        session.flush();session.undo();session.flush();session.redo();session.flush();session.retry()
        assertEquals(2L,recorder.offered.last().inputMetrics?.inserted)
        assertEquals(0L,recorder.offered.last().inputMetrics?.baseSeq)
    }
    @Test fun replacementPreservesTypingAndIsOneUndoStep() = runTest {
        val recorder = Recorder()
        val session = EditorSession(ChapterDocument("ch", "book", "vol", "章", "原文", 3),
            null, recorder, backgroundScope, {})
        session.editorState.edit { replace(0, length, "甲😀甲") }
        val source = session.editorState.text.toString()
        assertTrue(session.applyFormatting(source, TextSearch.replace(source, "甲", "乙")))
        session.undo()
        assertEquals(source, session.editorState.text.toString())
        session.redo()
        assertEquals("乙😀乙", session.editorState.text.toString())
        session.flush()
        assertEquals("乙😀乙", recorder.offered.last().body)
    }

    private class Recorder : SaveCoordinator {
        override val state = MutableStateFlow<LocalSaveState>(LocalSaveState.Idle)
        val offered = mutableListOf<ChapterDraft>()
        var failure = false
        var flushes = 0
        override fun offer(draft: ChapterDraft) { offered += draft }
        override suspend fun flush(chapterId: String, expectedEditSeq: Long): SaveResult {
            flushes++
            return if (failure) SaveResult.Failed(chapterId, expectedEditSeq, SaveFailure.DISK_FULL)
            else SaveResult.Committed(chapterId, expectedEditSeq, 1L)
        }
    }

    @Test fun immediateFlushIncludesEditBeforeObserverRuns() = runTest {
        val recorder = Recorder()
        val session = EditorSession(ChapterDocument("ch", "book", "vol", "章", "原文", 3),
            null, recorder, backgroundScope, {})
        session.editorState.edit { replace(0, length, "刚输入的新正文") }
        val result = session.flush() as SaveResult.Committed
        assertEquals("刚输入的新正文", recorder.offered.single().body)
        assertEquals(4L, result.editSeq)
        session.flush()
        assertEquals(1, recorder.offered.size)
    }

    @Test fun failedFlushPreservesEditableBuffer() = runTest {
        val recorder = Recorder().apply { failure = true }
        val session = EditorSession(ChapterDocument("ch", "book", "vol", "章", "原文", 3),
            null, recorder, backgroundScope, {})
        session.editorState.edit { replace(0, length, "不能丢的正文") }
        assertTrue(session.flush() is SaveResult.Failed)
        assertEquals("不能丢的正文", session.editorState.text.toString())
    }
    @Test fun undoAndRedoAreOfferedToPersistence() = runTest {
        val recorder = Recorder()
        val session = EditorSession(ChapterDocument("ch", "book", "vol", "章", "原文", 3),
            null, recorder, backgroundScope, {})
        runCurrent()
        session.editorState.edit { replace(0, length, "修改后") }
        Snapshot.sendApplyNotifications()
        runCurrent(); advanceTimeBy(801); runCurrent()
        assertTrue(session.canUndo.value)
        session.undo()
        session.flush()
        assertEquals("原文", recorder.offered.last().body)
        session.redo()
        session.flush()
        assertEquals("修改后", recorder.offered.last().body)
        assertEquals(listOf(4L, 5L, 6L), recorder.offered.map { it.editSeq })
    }

    @Test fun formattingIsOneUndoStepEvenBeforeTypingObserverRuns() = runTest {
        val recorder = Recorder()
        val session = EditorSession(ChapterDocument("ch", "book", "vol", "章", "原文", 3),
            null, recorder, backgroundScope, {})
        session.editorState.edit { replace(0, length, "新输入\n第二段") }
        assertTrue(session.applyFormatting("新输入\n第二段", "　　新输入\n　　第二段"))
        session.flush()
        assertEquals("　　新输入\n　　第二段", recorder.offered.last().body)
        session.undo()
        assertEquals("新输入\n第二段", session.editorState.text.toString())
        session.redo()
        assertEquals("　　新输入\n　　第二段", session.editorState.text.toString())
        session.undo(); session.undo()
        assertEquals("原文", session.editorState.text.toString())
    }

    @Test fun staleFormattingDoesNotChangeOrSaveText() = runTest {
        val recorder = Recorder()
        val session = EditorSession(ChapterDocument("ch", "book", "vol", "章", "原文", 3),
            null, recorder, backgroundScope, {})
        session.editorState.edit { replace(0, length, "预览后继续输入") }
        assertFalse(session.applyFormatting("原文", "　　原文"))
        assertEquals("预览后继续输入", session.editorState.text.toString())
        assertTrue(recorder.offered.isEmpty())
    }
}
