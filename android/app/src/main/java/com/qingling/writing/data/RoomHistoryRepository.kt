package com.qingling.writing.data

import androidx.room.withTransaction
import com.qingling.writing.core.database.WritingDatabase
import com.qingling.writing.core.database.entities.RevisionEntity
import com.qingling.writing.core.model.ChapterDocument
import com.qingling.writing.core.model.Revision
import com.qingling.writing.domain.NotFoundException
import com.qingling.writing.domain.repository.HistoryRepository
import com.qingling.writing.domain.validation.WordCounter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant

/**
 * HistoryRepository 的 Room 实现(P06)。
 * 自动快照节流:同一章两次 AUTO 至少间隔 [AUTO_MIN_INTERVAL_MS];恢复前当前内容先存
 * BEFORE_RESTORE(docs/01 P06「恢复历史前保留当前内容」);AUTO 上限滚动保留 20 份。
 */
internal class RoomHistoryRepository(
    private val db: WritingDatabase,
    private val newId: () -> String,
    private val now: () -> String,
    private val autoKeep: suspend () -> Int = { AUTO_KEEP },
) : HistoryRepository {

    private val revisionDao get() = db.revisionDao()
    private val outlineDao get() = db.outlineDao()

    override fun observeRevisions(chapterId: String): Flow<List<Revision>> =
        revisionDao.observeByChapter(chapterId).map { list -> list.map(RevisionEntity::toModel) }

    override suspend fun createManualSnapshot(chapterId: String) {
        val chapter = requireAliveChapter(chapterId)
        revisionDao.insert(
            RevisionEntity(
                id = newId(),
                chapterId = chapter.id,
                title = chapter.title,
                body = chapter.body,
                contentSha256 = ContentHash.of(chapter.body),
                source = Revision.Source.MANUAL.name,
                createdAt = now(),
            ),
        )
    }

    override suspend fun createBeforeRecoverySnapshot(chapterId:String) {
        db.withTransaction {
            val chapter=requireAliveChapter(chapterId)
            revisionDao.insert(RevisionEntity(id=newId(),chapterId=chapter.id,title=chapter.title,body=chapter.body,
                contentSha256=ContentHash.of(chapter.body),source=Revision.Source.BEFORE_RESTORE.name,createdAt=now()))
        }
    }

    override suspend fun createAutoSnapshotIfDue(
        chapterId: String,
        title: String,
        body: String,
    ) {
        val keep = autoKeep().coerceIn(20, 100)
        db.withTransaction {
        val currentChapter = requireAliveChapter(chapterId)
        val snapshotTitle = currentChapter.title
        val latestAuto = revisionDao.latestAutoCreatedAt(chapterId)
        if (latestAuto != null) {
            val elapsed = Duration.between(Instant.parse(latestAuto), Instant.parse(now()))
            if (elapsed.toMillis() < AUTO_MIN_INTERVAL_MS) return@withTransaction
        }
        revisionDao.insert(
            RevisionEntity(
                id = newId(),
                chapterId = chapterId,
                title = snapshotTitle,
                body = body,
                contentSha256 = ContentHash.of(body),
                source = Revision.Source.AUTO.name,
                createdAt = now(),
            ),
        )
        revisionDao.trimAutoRevisions(chapterId, keep)
        }
    }

    override suspend fun restoreRevision(chapterId: String, revisionId: String): ChapterDocument {
        val timestamp = now()
        return db.withTransaction {
            val chapter = outlineDao.chapterById(chapterId)
                ?.takeIf { it.deletedAt == null }
                ?: throw NotFoundException("chapter", chapterId)
            val revision = revisionDao.byId(revisionId)
                ?.takeIf { it.chapterId == chapterId }
                ?: throw NotFoundException("revision", revisionId)

            // 恢复历史前,先把当前内容保全为 BEFORE_RESTORE(docs/01 P06)。
            revisionDao.insert(
                RevisionEntity(
                    id = newId(),
                    chapterId = chapterId,
                    title = chapter.title,
                    body = chapter.body,
                    contentSha256 = ContentHash.of(chapter.body),
                    source = Revision.Source.BEFORE_RESTORE.name,
                    createdAt = timestamp,
                ),
            )
            val newSeq = chapter.editSeq + 1
            val updated = outlineDao.restoreChapterContent(
                id = chapterId,
                title = revision.title,
                body = revision.body,
                wordCount = WordCounter.count(revision.body),
                editSeq = newSeq,
                updatedAt = timestamp,
            )
            if (updated == 0) throw NotFoundException("chapter", chapterId)
            db.bookDao().bumpLocalSeq(chapter.bookId, timestamp)
            ChapterDocument(
                id = chapterId,
                bookId = chapter.bookId,
                volumeId = chapter.volumeId,
                title = revision.title,
                body = revision.body,
                editSeq = newSeq,
            )
        }
    }

    private suspend fun requireAliveChapter(chapterId: String) =
        outlineDao.chapterById(chapterId)
            ?.takeIf { it.deletedAt == null }
            ?: throw NotFoundException("chapter", chapterId)

    companion object {
        const val AUTO_MIN_INTERVAL_MS = 30_000L
        const val AUTO_KEEP = 20
    }
}
