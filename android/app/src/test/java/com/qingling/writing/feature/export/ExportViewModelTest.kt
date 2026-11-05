package com.qingling.writing.feature.export

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.qingling.writing.core.files.ExportFileStore
import com.qingling.writing.core.model.*
import com.qingling.writing.domain.repository.ExportRepository
import com.qingling.writing.domain.repository.OutlineRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ExportViewModelTest {
    @get:Rule val temp = TemporaryFolder()
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    private lateinit var files: ExportFileStore
    private val outline = FakeOutline()
    private var body = "原始正文😀"
    private var omitLast = false
    private var captures = 0
    private val exports = object : ExportRepository {
        override suspend fun capture(selection: ExportSelection): ExportSnapshot {
            captures++
            return ExportSnapshot("书", outline.volumes.value.map { vol -> ExportVolume(vol.id, vol.title,
                vol.chapters.filter { it.id in selection.chapterIds && !(omitLast && it.id == "c3") }
                    .map { ExportChapter(it.id, vol.id, it.title, body) }) }, 1)
        }
    }
    @Before fun before() { Dispatchers.setMain(dispatcher); files = ExportFileStore(temp.newFolder()) }
    @After fun after() { models.clear(); Dispatchers.resetMain() }
    private fun model(handle: SavedStateHandle = SavedStateHandle()): ExportViewModel =
        ExportViewModel(outline, exports, files, "book", handle).also { models.put("${models.keys().size}", it) }

    @Test fun defaultAllVolumeSelectionAndUserClearedSelectionSurviveRecreation() = runTest {
        val handle = SavedStateHandle(); val vm = model(handle); runCurrent()
        assertEquals(setOf("c1", "c2", "c3"), vm.ui.value.selected)
        vm.toggleVolume("v1")
        assertEquals(setOf("c3"), vm.ui.value.selected)
        vm.selectNone()
        val restored = model(handle); runCurrent()
        assertTrue(restored.ui.value.selected.isEmpty())
        assertNull(restored.prepare())
        assertEquals(0, captures)
    }
    @Test fun selectionClickOrderDoesNotChangePreviewOrder() = runTest {
        val vm = model(); runCurrent(); vm.selectNone()
        vm.toggleChapter("c3"); vm.toggleChapter("c1")
        vm.setOptions(false, false, true)
        val result = vm.prepare()!!
        assertEquals(listOf("卷一 · Z章", "卷二 · A章"), result.chapterTitles)
        val text = result.staged.file.readText(Charsets.UTF_8)
        assertTrue(text.startsWith("Z章\n")); assertFalse(text.contains("卷一"))
    }
    @Test fun cancelNeverReportsSavedAndUnblocksControls() = runTest {
        val vm = model(); runCurrent(); vm.beginSave()
        assertTrue(vm.ui.value.busy)
        vm.cancelSave()
        assertEquals(ExportNotice.CANCELLED, vm.ui.value.notice)
        assertFalse(vm.ui.value.busy)
    }
    @Test fun providerWritesFrozenSnapshotEvenAfterSourceChangesAndStateRestores() = runTest {
        val handle = SavedStateHandle(); val vm = model(handle); runCurrent()
        val pending = vm.beginSave()!!
        val expected = pending.staged.file.readBytes()
        body = "之后修改的正文"
        val restored = model(handle); runCurrent()
        assertTrue(restored.ui.value.busy)
        val output = ByteArrayOutputStream()
        restored.completeSave { output }
        restored.ui.first { !it.busy && it.notice == ExportNotice.SAVED }
        assertArrayEquals(expected, output.toByteArray())
        assertEquals(1, captures)
    }
    @Test fun providerFailureIsSurfacedAndControlsUnlock() = runTest {
        val vm = model(); runCurrent(); vm.beginSave()
        vm.completeSave { throw IOException("disconnected") }
        vm.ui.first { !it.busy && it.notice == ExportNotice.FAILED }
        assertTrue(vm.ui.value.preview!!.staged.file.isFile)
    }
    @Test fun changedSelectionCannotSilentlyExportFewerChapters() = runTest {
        val vm = model(); runCurrent(); omitLast = true
        assertNull(vm.prepare())
        assertEquals(ExportNotice.CHANGED, vm.ui.value.notice)
        assertFalse(vm.ui.value.busy)
    }

    @Test fun docxFormatAndPendingCopySurviveRecreation() = runTest {
        val handle = SavedStateHandle();val vm = model(handle);runCurrent();vm.setFormat(ExportFormat.DOCX)
        val pending = vm.beginSave()!!
        assertEquals(ExportFormat.DOCX,pending.format);assertTrue(pending.staged.fileName.endsWith(".docx"))
        assertTrue(pending.staged.fileName.contains("全书"));assertTrue(pending.text.contains("原始正文"))
        val restored = model(handle);runCurrent();assertEquals(ExportFormat.DOCX,restored.ui.value.format)
        val output = ByteArrayOutputStream();restored.completeSave { output }
        restored.ui.first { !it.busy && it.notice == ExportNotice.SAVED }
        assertArrayEquals(pending.staged.file.readBytes(),output.toByteArray())
    }
    @Test fun explicitTextPreparationRemainsTextWhileDocxIsSelected() = runTest {
        val vm = model();runCurrent();vm.setFormat(ExportFormat.DOCX)
        val copy = vm.prepare(ExportFormat.TXT)!!
        assertTrue(copy.staged.fileName.endsWith(".txt"));assertTrue(copy.staged.file.readText().contains("原始正文"))
        assertEquals(ExportFormat.DOCX,vm.ui.value.format)
    }
    private class FakeOutline : OutlineRepository {
        override suspend fun duplicateChapter(chapterId: String, title: String): com.qingling.writing.core.model.ChapterDocument = error("unused")
        val volumes = MutableStateFlow(listOf(
            OutlineVolume("v1", "book", "卷一", 0, listOf(
                OutlineChapter("c1", "book", "v1", "Z章", 0, 0),
                OutlineChapter("c2", "book", "v1", "空章", 1, 0))),
            OutlineVolume("v2", "book", "卷二", 1, listOf(OutlineChapter("c3", "book", "v2", "A章", 0, 0)))))
        override fun observeOutline(bookId: String) = volumes
        override fun observeBookTitle(bookId: String) = MutableStateFlow<String?>("书")
        override suspend fun createVolume(bookId: String, title: String) = error("unused")
        override suspend fun createChapter(bookId: String, volumeId: String, title: String): ChapterDocument = error("unused")
        override suspend fun renameVolume(volumeId: String, newTitle: String) = error("unused")
        override suspend fun renameChapter(chapterId: String, newTitle: String) = error("unused")
        override suspend fun moveVolume(bookId: String, volumeId: String, offset: Int) = error("unused")
        override suspend fun moveChapter(chapterId: String, targetVolumeId: String, targetIndex: Int) = error("unused")
        override suspend fun moveChapterWithinVolume(chapterId: String, offset: Int) = error("unused")
        override suspend fun softDeleteVolume(volumeId: String) = error("unused")
        override suspend fun softDeleteChapter(chapterId: String) = error("unused")
    }
}
