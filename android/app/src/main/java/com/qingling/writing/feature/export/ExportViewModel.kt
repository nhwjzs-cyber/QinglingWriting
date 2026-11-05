package com.qingling.writing.feature.export

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qingling.writing.core.files.ExportFileStore
import com.qingling.writing.core.files.TxtWriter
import com.qingling.writing.core.model.ExportSelection
import com.qingling.writing.core.model.OutlineVolume
import com.qingling.writing.core.model.ExportFormat
import com.qingling.writing.core.files.DocxWriter
import com.qingling.writing.domain.repository.ExportRepository
import com.qingling.writing.domain.repository.OutlineRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import com.qingling.writing.core.model.ExportPreset
import com.qingling.writing.core.model.ExportPreferences
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream

enum class ExportNotice { SAVED, CANCELLED, SHARE_OPENED, FAILED, EMPTY, CHANGED, MISSING }
data class ExportPreview(val staged: ExportFileStore.Staged, val text: String,
    val truncated: Boolean, val chapterTitles: List<String>,val format: ExportFormat = ExportFormat.TXT)
data class ExportUiState(
    val loaded: Boolean = false, val bookTitle: String? = null,
    val volumes: List<OutlineVolume> = emptyList(), val selected: Set<String> = emptySet(),
    val includeBook: Boolean = true, val includeVolumes: Boolean = true, val includeChapters: Boolean = true,
    val busy: Boolean = false, val preview: ExportPreview? = null, val notice: ExportNotice? = null,
    val format: ExportFormat = ExportFormat.TXT,
    val errorDetail: String? = null, val scopeLabel:String="全书",
)

class ExportViewModel(
    outline: OutlineRepository,
    private val exports: ExportRepository,
    private val files: ExportFileStore,
    private val bookId: String,
    private val savedState: SavedStateHandle,
    private val settings:com.qingling.writing.core.datastore.SettingsStore?=null,
    private val preset:ExportPreset=ExportPreset(),
) : ViewModel() {
    private val mutable = MutableStateFlow(ExportUiState(busy = savedState.get<String>(PENDING) != null,
        includeBook = savedState[BOOK] ?: true, includeVolumes = savedState[VOLUMES] ?: true,
        includeChapters = savedState[CHAPTERS] ?: true,
        format = ExportFormat.entries.firstOrNull { it.name == savedState.get<String>(FORMAT) } ?: ExportFormat.TXT))
    val ui = mutable.asStateFlow()
    private var initialized = savedState.contains(SELECTED)

    init {
        viewModelScope.launch {
            settings?.exportPreferences?.first()?.let{p->mutable.value=mutable.value.copy(
                format=if(savedState.contains(FORMAT))mutable.value.format else p.format,
                includeBook=if(savedState.contains(BOOK))mutable.value.includeBook else p.book,
                includeVolumes=if(savedState.contains(VOLUMES))mutable.value.includeVolumes else p.volumes,
                includeChapters=if(savedState.contains(CHAPTERS))mutable.value.includeChapters else p.chapters)}
            combine(outline.observeBookTitle(bookId), outline.observeOutline(bookId)) { title, volumes -> title to volumes }
                .collect { (title, volumes) ->
                    val ids = volumes.flatMap { it.chapters }.map { it.id }.toSet()
                    val selected = if (!initialized) preset.select(volumes) else
                        (savedState.get<ArrayList<String>>(SELECTED)?.toSet() ?: mutable.value.selected).intersect(ids)
                    // Empty initial data may precede seeding; initialize on the first real chapter set.
                    if (ids.isNotEmpty() || initialized) {
                        initialized = true
                        savedState[SELECTED] = ArrayList(selected)
                    }
                    val old = mutable.value
                    mutable.value = old.copy(loaded = true, bookTitle = title, volumes = volumes,
                        selected = selected, scopeLabel=savedState.get<String>(SCOPE_LABEL) ?: preset.label, preview = if (old.volumes == volumes && old.bookTitle == title) old.preview else null)
                }
        }
    }

    private fun select(ids: Set<String>, label:String="自定义") {
        if (mutable.value.busy) return
        initialized = true
        savedState[SELECTED] = ArrayList(ids)
        savedState[SCOPE_LABEL]=label
        mutable.value = mutable.value.copy(selected = ids, scopeLabel=label,preview = null, notice = null)
    }
    fun toggleChapter(id: String) = select(mutable.value.selected.let { if (id in it) it - id else it + id })
    fun selectAll() = select(mutable.value.volumes.flatMap { it.chapters }.map { it.id }.toSet(),"全书")
    fun selectNone() = select(emptySet())
    fun toggleVolume(id: String) {
        val ids = mutable.value.volumes.firstOrNull { it.id == id }?.chapters?.map { it.id }?.toSet() ?: return
        select(if (mutable.value.selected.containsAll(ids)) mutable.value.selected - ids else mutable.value.selected + ids)
    }
    fun setOptions(book: Boolean, volumes: Boolean, chapters: Boolean) {
        if (mutable.value.busy) return
        savedState[BOOK] = book; savedState[VOLUMES] = volumes; savedState[CHAPTERS] = chapters
        mutable.value = mutable.value.copy(includeBook = book, includeVolumes = volumes,
            includeChapters = chapters, preview = null, notice = null)
        persistOptions()
    }

    fun setFormat(format: ExportFormat) {
        if (mutable.value.busy) return
        savedState[FORMAT] = format.name
        mutable.value = mutable.value.copy(format = format,preview = null,notice = null)
        persistOptions()
    }
    private fun persistOptions(){
        val store=settings ?: return
        val value=mutable.value.let{ExportPreferences(it.format,it.includeBook,it.includeVolumes,it.includeChapters)}
        viewModelScope.launch{try{store.setExportPreferences(value)}catch(e:CancellationException){throw e}catch(_:Exception){mutable.value=mutable.value.copy(errorDetail="导出偏好未保存，本次选择仍可使用")}}
    }
    /** One transaction captures the source. The chooser later writes this exact staged file. */
    suspend fun prepare(format: ExportFormat = mutable.value.format): ExportPreview? {
        val current = mutable.value
        if (current.busy) return null
        if (current.selected.isEmpty()) { notice(ExportNotice.EMPTY); return null }
        mutable.value = current.copy(busy = true, notice = null,errorDetail = null)
        try {
            val selection = ExportSelection(bookId, current.selected, current.includeBook, current.includeVolumes, current.includeChapters)
            val snapshot = exports.capture(selection)
            val chapters = snapshot.volumes.flatMap { it.chapters }
            if (chapters.map { it.id }.toSet() != current.selected) { notice(ExportNotice.CHANGED); return null }
            val result = withContext(Dispatchers.IO) {
                files.pruneExpired()
                val text = TxtWriter.write(snapshot, current.includeBook, current.includeVolumes, current.includeChapters)
                val shortTitle = buildString { snapshot.bookTitle.codePoints().limit(40).forEach { appendCodePoint(it) } }
                val range = if(current.selected.size == current.volumes.sumOf { it.chapters.size }) "全书" else "选${current.selected.size}章"
                val name = "$shortTitle-$range-${java.time.LocalDate.now()}"
                val staged = files.stage(name,if(format == ExportFormat.DOCX)
                    DocxWriter.write(snapshot,current.includeBook,current.includeVolumes,current.includeChapters) else text,format.extension)
                val preview = if(format == ExportFormat.TXT) files.preview(staged.token) else {
                    val plain = text.toString(Charsets.UTF_8)
                    var excerpt = plain.take(8000)
                    if(excerpt.lastOrNull()?.isHighSurrogate() == true) excerpt = excerpt.dropLast(1)
                    ExportFileStore.Preview(excerpt,plain.length > 8000)
                }
                ExportPreview(staged, preview.text, preview.truncated,
                    snapshot.volumes.flatMap { vol -> vol.chapters.map { "${vol.title} · ${it.title}" } },format)
            }
            mutable.value = mutable.value.copy(preview = result)
            return result
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            notice(ExportNotice.FAILED)
            mutable.value = mutable.value.copy(errorDetail = e.message?.takeIf { it.startsWith("DOCX ") || it.startsWith("单次") })
            return null
        } finally { mutable.value = mutable.value.copy(busy = false) }
    }

    suspend fun beginSave(): ExportPreview? {
        val prepared = prepare() ?: return null
        savedState[PENDING] = prepared.staged.token
        mutable.value = mutable.value.copy(busy = true)
        return prepared
    }
    fun cancelSave() {
        savedState.remove<String>(PENDING)
        mutable.value = mutable.value.copy(busy = false, notice = ExportNotice.CANCELLED)
    }
    fun launchFailed() {
        savedState.remove<String>(PENDING)
        mutable.value = mutable.value.copy(busy = false, notice = ExportNotice.FAILED)
    }
    fun completeSave(openOutput: () -> OutputStream?) {
        val token = savedState.get<String>(PENDING)
        if (token == null) { notice(ExportNotice.MISSING); return }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { files.copyTo(token, openOutput) }
                notice(ExportNotice.SAVED)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                notice(ExportNotice.FAILED)
            } finally {
                savedState.remove<String>(PENDING)
                mutable.value = mutable.value.copy(busy = false)
            }
        }
    }
    fun sharedChooserOpened() = notice(ExportNotice.SHARE_OPENED)
    fun dismissPreview() { mutable.value = mutable.value.copy(preview = null) }
    private fun notice(value: ExportNotice) { mutable.value = mutable.value.copy(notice = value,errorDetail = null) }
    companion object {
        private const val SCOPE_LABEL="export.scopeLabel"
        private const val PENDING = "export.pending"
        private const val SELECTED = "export.selected"
        private const val BOOK = "export.book"
        private const val VOLUMES = "export.volumes"
        private const val CHAPTERS = "export.chapters"
        private const val FORMAT = "export.format"
    }
}
