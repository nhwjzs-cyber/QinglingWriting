package com.qingling.writing.core.model

/** Portable manuscripts only. Restore allocates fresh IDs; account/sync/settings are not copied. */
data class LibraryBackup(
    val createdAt: String,
    val books: List<BackupBook>,
    val volumes: List<BackupVolume>,
    val chapters: List<BackupChapter>,
    val revisions: List<BackupRevision>,
    val positions: List<BackupPosition>,
    val planning: List<PlanningEntry> = emptyList(),
)
data class BackupBook(val id: String, val title: String, val author: String, val description: String,
    val createdAt: String, val updatedAt: String, val deletedAt: String?, val batch: String?, val lastChapter: String?,val targetWords: Long = 0,
    val pinned: Boolean = false,val archived: Boolean = false, val chapterNumberCounter: Long = 0)
data class BackupVolume(val id: String, val bookId: String, val title: String, val position: Long,
    val createdAt: String, val updatedAt: String, val deletedAt: String?, val batch: String?)
data class BackupChapter(val id: String, val bookId: String, val volumeId: String, val title: String, val body: String,
    val position: Long, val createdAt: String, val updatedAt: String, val deletedAt: String?, val batch: String?, val completed: Boolean = false, val automaticNumber: Long = 0, val titleLocked: Boolean = true)
data class BackupRevision(val id: String, val chapterId: String, val title: String, val body: String,
    val source: String, val createdAt: String)
data class BackupPosition(val chapterId: String, val start: Long, val end: Long, val scroll: Long)
data class BackupRestoreResult(val books: Int, val chapters: Int)

/** Scope a consistent snapshot without dropping deleted children, history or planning entries. */
fun LibraryBackup.forBook(bookId: String): LibraryBackup {
    val book = books.singleOrNull { it.id == bookId } ?: error("作品不存在或备份范围已变化")
    val selectedChapters = chapters.filter { it.bookId == bookId }
    val chapterIds = selectedChapters.map { it.id }.toSet()
    return copy(books = listOf(book),volumes = volumes.filter { it.bookId == bookId },chapters = selectedChapters,
        revisions = revisions.filter { it.chapterId in chapterIds },positions = positions.filter { it.chapterId in chapterIds },
        planning = planning.filter { it.bookId == bookId })
}
