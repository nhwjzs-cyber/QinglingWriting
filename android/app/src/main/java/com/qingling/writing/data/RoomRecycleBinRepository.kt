package com.qingling.writing.data

import com.qingling.writing.core.database.WritingDatabase
import com.qingling.writing.core.model.RecycleEntry
import com.qingling.writing.domain.repository.RecycleBinRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import androidx.room.withTransaction
import com.qingling.writing.core.model.PurgePlan

/**
 * RecycleBinRepository 的 Room 实现(P06 回收部分)。
 * 三个墓碑源合并为一个按删除时间倒序的批次列表;恢复入口在 WritingRepository.restoreDeletionBatch。
 */
internal class RoomRecycleBinRepository(
    private val db: WritingDatabase,
) : RecycleBinRepository {

    override suspend fun preparePurge(spaceId: String, entries: List<RecycleEntry>): PurgePlan = db.withTransaction {
        require(entries.isNotEmpty())
        val books = mutableSetOf<String>()
        val volumes = mutableSetOf<String>()
        val chapters = mutableSetOf<String>()
        val dao = db.purgeDao()
        entries.forEach { entry ->
            val book = checkNotNull(db.bookDao().byId(entry.bookId))
            check(book.spaceId == spaceId) { "作品不属于当前空间" }
            when (entry.kind) {
                RecycleEntry.Kind.BOOK -> {
                    check(entry.itemId == book.id && book.deletedAt != null && book.deletionBatchId == entry.batchId)
                    books.add(book.id)
                    dao.volumesOfBook(book.id).forEach { id ->
                        check(db.outlineDao().volumeById(id)?.deletedAt != null)
                        volumes.add(id)
                    }
                    dao.chaptersOfBook(book.id).forEach { check(it.deletedAt != null); chapters.add(it.id) }
                }
                RecycleEntry.Kind.VOLUME -> {
                    val volume = checkNotNull(db.outlineDao().volumeById(entry.itemId))
                    check(volume.bookId == book.id && volume.deletedAt != null && volume.deletionBatchId == entry.batchId)
                    volumes.add(volume.id)
                    dao.chaptersOfVolume(volume.id).forEach { check(it.deletedAt != null); chapters.add(it.id) }
                }
                RecycleEntry.Kind.CHAPTER -> {
                    val chapter = checkNotNull(dao.chapter(entry.itemId))
                    check(chapter.bookId == book.id && chapter.deletedAt != null && chapter.batch == entry.batchId)
                    chapters.add(chapter.id)
                }
            }
        }
        val planningIds = (books.toList().chunked(200).flatMap { db.planningDao().idsOfBooks(it) } +
            chapters.toList().chunked(200).flatMap { db.planningDao().idsOfChapters(it) } +
            volumes.toList().chunked(200).flatMap { db.planningDao().idsOfVolumes(it) }).distinct().sorted()
        PurgePlan(spaceId, entries.toList(), books.sorted(), volumes.sorted(), chapters.sorted(),
            chapters.toList().chunked(200).sumOf { dao.revisionCount(it) },planningIds)
    }

    override suspend fun purge(plan: PurgePlan) = db.withTransaction {
        check(preparePurge(plan.spaceId, plan.entries) == plan) { "删除范围已变化，请重新确认" }
        val dao = db.purgeDao()
        plan.chapterIds.chunked(200).forEach { ids ->
            dao.deleteRevisions(ids); dao.deletePositions(ids)
            dao.clearSpaceChapters(ids); dao.clearBookChapters(ids); dao.clearLastChapters(ids)
            check(dao.deleteChapters(ids) == ids.size)
        }
        plan.volumeIds.chunked(200).forEach { check(dao.deleteVolumes(it) == it.size) }
        plan.bookIds.chunked(200).forEach { ids ->
            dao.deleteBookActive(ids); dao.clearSpaceBooks(ids)
            check(dao.deleteBooks(ids) == ids.size)
        }
    }

    override fun observeRecycleBin(spaceId: String): Flow<List<RecycleEntry>> =
        combine(
            db.recycleDao().observeDeletedBooks(spaceId),
            db.recycleDao().observeDeletedVolumesInLiveBooks(spaceId),
            db.recycleDao().observeDeletedChaptersInLiveBooks(spaceId),
        ) { books, volumes, chapters ->
            (
                books.map { row ->
                    RecycleEntry(
                        batchId = row.deletionBatchId,
                        kind = RecycleEntry.Kind.BOOK,
                        title = row.title,
                        bookId = row.id,
                        bookTitle = null,
                        volumeCount = row.volumeCount,
                        chapterCount = row.chapterCount,
                        deletedAt = row.deletedAt,
                    )
                } + volumes.map { row ->
                    RecycleEntry(
                        batchId = row.deletionBatchId,
                        kind = RecycleEntry.Kind.VOLUME,
                        itemId = row.id,
                        title = row.title,
                        bookId = row.bookId,
                        bookTitle = row.bookTitle,
                        volumeCount = null,
                        chapterCount = row.chapterCount,
                        deletedAt = row.deletedAt,
                    )
                } + chapters.map { row ->
                    RecycleEntry(
                        batchId = row.deletionBatchId,
                        kind = RecycleEntry.Kind.CHAPTER,
                        itemId = row.id,
                        title = row.title,
                        bookId = row.bookId,
                        bookTitle = row.bookTitle,
                        volumeCount = null,
                        chapterCount = null,
                        deletedAt = row.deletedAt,
                    )
                }
                ).sortedByDescending { it.deletedAt }
        }
}
