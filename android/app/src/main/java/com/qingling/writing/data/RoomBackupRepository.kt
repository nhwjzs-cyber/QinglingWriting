package com.qingling.writing.data

import androidx.room.withTransaction
import com.qingling.writing.core.database.WritingDatabase
import com.qingling.writing.core.database.entities.*
import com.qingling.writing.core.files.BackupCodec
import com.qingling.writing.core.model.*
import com.qingling.writing.domain.repository.BackupRepository
import com.qingling.writing.domain.validation.BackupValidator
import com.qingling.writing.domain.validation.WordCounter

internal class RoomBackupRepository(
    private val db: WritingDatabase,
    private val newId: () -> String,
    private val now: () -> String,
    private val captureLimitBytes: Long = BackupCodec.MAX_PAYLOAD.toLong(),
) : BackupRepository {
    override suspend fun capture(): LibraryBackup = captureScoped(null)
    override suspend fun captureBook(bookId: String): LibraryBackup = captureScoped(bookId)
    private suspend fun captureScoped(bookId: String?): LibraryBackup = db.withTransaction {
        val dao = db.backupDao()
        val space = GuestSpace.ID
        bookId?.let { require(db.bookDao().byId(it)?.spaceId == space) { "作品不存在" } }
        require(dao.conservativeSize(space,bookId) <= captureLimitBytes) { "BACKUP_LIMIT" }
        val completed = db.progressDao().details().filter { it.completed }.map { it.chapterId }.toSet()
        val goals = db.bookGoalDao().capture(space,bookId).associate { it.bookId to it.targetWords }
        val shelf=db.shelfFlagsDao().capture(space,bookId).associateBy {it.bookId}
        LibraryBackup(now(),
            dao.books(space,bookId).map { BackupBook(it.id, it.title, it.author, it.description, it.createdAt, it.updatedAt,
                it.deletedAt, it.deletionBatchId, it.lastActiveChapterId,goals[it.id] ?: 0,shelf[it.id]?.pinned ?: false,shelf[it.id]?.archived ?: false, it.chapterNumberCounter) },
            dao.volumes(space,bookId).map { BackupVolume(it.id, it.bookId, it.title, it.position, it.createdAt,
                it.updatedAt, it.deletedAt, it.deletionBatchId) },
            dao.chapters(space,bookId).map { BackupChapter(it.id, it.bookId, it.volumeId, it.title, it.body, it.position,
                it.createdAt, it.updatedAt, it.deletedAt, it.deletionBatchId, it.id in completed, it.automaticNumber, it.titleLocked) },
            dao.revisions(space,bookId).map { BackupRevision(it.id, it.chapterId, it.title, it.body, it.source, it.createdAt) },
            dao.positions(space,bookId).map { BackupPosition(it.chapterId, it.selectionStart, it.selectionEnd, it.scrollOffset) },
            db.planningDao().capture(space,bookId).map { it.toEntry() })
    }

    override suspend fun restoreCopies(backup: LibraryBackup): BackupRestoreResult {
        BackupValidator.validate(backup)
        val bookIds = backup.books.associate { it.id to newId() }
        val volumeIds = backup.volumes.associate { it.id to newId() }
        val chapterIds = backup.chapters.associate { it.id to newId() }
        val batches = (backup.books.mapNotNull { it.batch } + backup.volumes.mapNotNull { it.batch } +
            backup.chapters.mapNotNull { it.batch }).distinct().associateWith { newId() }
        val timestamp = now()
        return db.withTransaction {
            db.spaceDao().insert(SpaceEntity(GuestSpace.ID, GuestSpace.KIND_GUEST, null))
            backup.books.forEach { b ->
                val suffix = "（恢复副本）"
                val kept = b.title.codePoints().limit((80 - suffix.codePointCount(0, suffix.length)).toLong()).toArray()
                val title = String(kept, 0, kept.size) + suffix
                db.bookDao().insert(BookEntity(bookIds.getValue(b.id), GuestSpace.ID, title, b.author, b.description,
                    b.createdAt, timestamp, b.deletedAt, b.batch?.let(batches::getValue),
                    localSeq = 1, syncedLocalSeq = 0, remoteVersion = null, localDirty = true,
                    lastActiveChapterId = b.lastChapter?.let(chapterIds::getValue),
                    chapterNumberCounter = maxOf(b.chapterNumberCounter, backup.chapters.count { it.bookId == b.id }.toLong(), backup.chapters.filter { it.bookId == b.id }.maxOfOrNull { it.automaticNumber } ?: 0L)))
                if (b.targetWords > 0) db.bookGoalDao().setGoal(BookGoalEntity(bookIds.getValue(b.id),b.targetWords))
                if(b.pinned || b.archived) db.shelfFlagsDao().put(ShelfFlagsEntity(bookIds.getValue(b.id),b.pinned,b.archived))
            }
            backup.volumes.forEach { v ->
                db.outlineDao().insertVolume(VolumeEntity(volumeIds.getValue(v.id), bookIds.getValue(v.bookId),
                    v.title, v.position, v.createdAt, v.updatedAt, v.deletedAt, v.batch?.let(batches::getValue)))
            }
            backup.chapters.forEach { c ->
                db.outlineDao().insertChapter(ChapterEntity(chapterIds.getValue(c.id), bookIds.getValue(c.bookId),
                    volumeIds.getValue(c.volumeId), c.title, c.body, c.position, editSeq = 0,
                    wordCount = WordCounter.count(c.body), c.createdAt, c.updatedAt, c.deletedAt, c.batch?.let(batches::getValue), c.automaticNumber, c.titleLocked))
                if (c.completed) db.progressDao().setDetails(ChapterDetailsEntity(chapterIds.getValue(c.id), true))
            }
            backup.revisions.forEach { r ->
                db.revisionDao().insert(RevisionEntity(newId(), chapterIds.getValue(r.chapterId), r.title, r.body,
                    BackupCodec.sha256(r.body.toByteArray(Charsets.UTF_8)), r.source, r.createdAt))
            }
            val lengths = backup.chapters.associate { it.id to it.body.length.toLong() }
            backup.positions.forEach { p ->
                val size = lengths.getValue(p.chapterId)
                db.editorPositionDao().upsert(EditorPositionEntity(chapterIds.getValue(p.chapterId),
                    p.start.coerceAtMost(size), p.end.coerceAtMost(size), p.scroll.coerceAtMost(Int.MAX_VALUE.toLong())))
            }
            backup.planning.forEach { p ->
                val note=com.qingling.writing.core.model.QuickNote.decode(p.body)
                val restoredBody=if(p.kind=="INSPIRATION" && p.body.startsWith("〔轻灵速记·1〕")) note.copy(chapterId=chapterIds[note.chapterId].orEmpty(),resolvedChapterId=chapterIds[note.resolvedChapterId].orEmpty()).encode() else p.body
                db.planningDao().insert(p.copy(id = newId(),bookId = if(p.bookId.isEmpty()) "" else bookIds.getValue(p.bookId),
                    chapterId = p.chapterId?.let(chapterIds::getValue),volumeId = p.volumeId?.let(volumeIds::getValue),
                    version = 1, body=restoredBody).toEntity())
            }
            BackupRestoreResult(backup.books.size, backup.chapters.size)
        }
    }
}
