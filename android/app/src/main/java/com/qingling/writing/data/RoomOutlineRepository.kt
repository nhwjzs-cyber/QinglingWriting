package com.qingling.writing.data

import androidx.room.withTransaction
import com.qingling.writing.core.database.WritingDatabase
import com.qingling.writing.core.database.entities.ChapterEntity
import com.qingling.writing.core.database.entities.VolumeEntity
import com.qingling.writing.core.model.ChapterDocument
import com.qingling.writing.core.model.OutlineChapter
import com.qingling.writing.core.model.OutlineVolume
import com.qingling.writing.domain.NotFoundException
import com.qingling.writing.domain.TitleValidationException
import com.qingling.writing.domain.repository.OutlineRepository
import com.qingling.writing.domain.validation.TitleValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * OutlineRepository 的 Room 实现(P02:卷章增删改移)。
 * 移动在事务内重排同级全部存活项的 position(docs/01 §4);同位次临界合法(无 UNIQUE(position)),
 * 重排后不留空洞、顺序稳定(以 id 决胜)。
 */
internal class RoomOutlineRepository(
    private val db: WritingDatabase,
    private val newId: () -> String,
    private val now: () -> String,
) : OutlineRepository {

    private val bookDao get() = db.bookDao()
    private val outlineDao get() = db.outlineDao()

    override fun observeOutline(bookId: String): Flow<List<OutlineVolume>> =
        combine(
            outlineDao.observeActiveVolumes(bookId),
            outlineDao.observeActiveChapters(bookId),
            db.progressDao().observeDetails(),
            db.planningDao().outlinePreviews(bookId),
        ) { volumes, chapters, details, planning ->
            val completed = details.filter { it.completed }.map { it.chapterId }.toSet()
            val chapterNotes = planning.filter { it.chapterId != null }.associate { it.chapterId to it.body }
            val volumeNotes = planning.filter { it.volumeId != null }.associate { it.volumeId to it.body }
            volumes.map { volume ->
                OutlineVolume(
                    id = volume.id,
                    bookId = volume.bookId,
                    title = volume.title,
                    position = volume.position,
                    chapters = chapters
                        .filter { it.volumeId == volume.id }
                        .map { it.toOutlineChapter().copy(completed = it.id in completed,outline = chapterNotes[it.id].orEmpty(), unnamed = com.qingling.writing.core.model.ChapterNaming.isPlaceholder(it.title, it.automaticNumber, it.titleLocked)) },
                    summary = volumeNotes[volume.id].orEmpty(),
                )
            }
        }

    override fun observeBookTitle(bookId: String): Flow<String?> =
        bookDao.observeById(bookId).map { book ->
            book?.takeIf { it.deletedAt == null }?.title
        }

    override suspend fun createVolume(bookId: String, title: String) {
        val checked = TitleValidator.checkSectionTitle(title)
        if (checked.error != null) throw TitleValidationException(checked.error)
        requireAliveBook(bookId)
        val timestamp = now()
        db.withTransaction {
            outlineDao.insertVolume(
                VolumeEntity(
                    id = newId(),
                    bookId = bookId,
                    title = checked.value,
                    position = outlineDao.nextVolumePosition(bookId),
                    createdAt = timestamp,
                    updatedAt = timestamp,
                    deletedAt = null,
                    deletionBatchId = null,
                ),
            )
            touchBook(bookId)
        }
    }

    override suspend fun createChapter(bookId: String, volumeId: String, title: String): ChapterDocument {
        val automatic = title.isBlank()
        val checked = TitleValidator.checkSectionTitle(if (automatic) "未命名章节" else title)
        if (checked.error != null) throw TitleValidationException(checked.error)
        requireAliveBook(bookId)
        val chapterId = newId()
        var resolvedTitle = checked.value
        val timestamp = now()
        db.withTransaction {
            val volume = outlineDao.volumeById(volumeId)
                ?.takeIf { it.deletedAt == null }
                ?: throw NotFoundException("volume", volumeId)
            // 跨书卷章引用在数据库层也会被复合外键拒绝;这里提前给出明确的领域错误。
            if (volume.bookId != bookId) throw NotFoundException("volume", volumeId)
            check(bookDao.incrementChapterNumber(bookId) == 1) { "章节编号已达上限" }
            val number = requireNotNull(bookDao.chapterNumber(bookId))
            resolvedTitle = if (automatic) com.qingling.writing.core.model.ChapterNaming.placeholder(number) else checked.value
            outlineDao.insertChapter(
                ChapterEntity(
                    id = chapterId,
                    bookId = bookId,
                    volumeId = volumeId,
                    title = resolvedTitle,
                    body = "",
                    position = outlineDao.nextChapterPosition(volumeId),
                    editSeq = 0L,
                    wordCount = 0L,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                    deletedAt = null,
                    deletionBatchId = null,
                    automaticNumber = if (automatic) number else 0, titleLocked = !automatic,
                ),
            )
            touchBook(bookId)
        }
        return ChapterDocument(
            id = chapterId,
            bookId = bookId,
            volumeId = volumeId,
            title = resolvedTitle,
            body = "",
            editSeq = 0L,
        )
    }

    override suspend fun duplicateChapter(chapterId: String, title: String): ChapterDocument {
        val checked = TitleValidator.checkSectionTitle(title)
        if (checked.error != null) throw TitleValidationException(checked.error)
        return db.withTransaction {
            val source = requireAliveChapter(chapterId)
            requireAliveBook(source.bookId)
            val volume = outlineDao.volumeById(source.volumeId)
                ?.takeIf { it.deletedAt == null } ?: throw NotFoundException("volume", source.volumeId)
            val siblings = outlineDao.activeChaptersOfVolume(volume.id)
            val index = siblings.indexOfFirst { it.id == chapterId }
            val stamp = now()
            check(bookDao.incrementChapterNumber(source.bookId) == 1) { "章节编号已达上限" }
            val copy = source.copy(id = newId(), title = checked.value, automaticNumber = 0, titleLocked = true,
                position = siblings.size.toLong(), editSeq = 0,
                createdAt = stamp, updatedAt = stamp, deletedAt = null, deletionBatchId = null)
            outlineDao.insertChapter(copy)
            renumberAfterMove(copy, volume.id, index + 1)
            touchBook(source.bookId)
            ChapterDocument(copy.id, copy.bookId, copy.volumeId, copy.title, copy.body, copy.editSeq)
        }
    }

    override suspend fun renameVolume(volumeId: String, newTitle: String) {
        val checked = TitleValidator.checkSectionTitle(newTitle)
        if (checked.error != null) throw TitleValidationException(checked.error)
        db.withTransaction {
            val volume = outlineDao.volumeById(volumeId)
                ?.takeIf { it.deletedAt == null }
                ?: throw NotFoundException("volume", volumeId)
            if (outlineDao.renameVolume(volumeId, checked.value, now()) == 0) {
                throw NotFoundException("volume", volumeId)
            }
            touchBook(volume.bookId)
        }
    }

    override suspend fun renameChapter(chapterId: String, newTitle: String) {
        val checked = TitleValidator.checkSectionTitle(newTitle)
        if (checked.error != null) throw TitleValidationException(checked.error)
        db.withTransaction {
            val chapter = outlineDao.chapterById(chapterId)
                ?.takeIf { it.deletedAt == null }
                ?: throw NotFoundException("chapter", chapterId)
            if (outlineDao.renameChapter(chapterId, checked.value, now()) == 0) {
                throw NotFoundException("chapter", chapterId)
            }
            touchBook(chapter.bookId)
        }
    }

    override suspend fun moveVolume(bookId: String, volumeId: String, offset: Int) {
        require(offset == -1 || offset == 1) { "offset must be -1 or +1, got $offset" }
        db.withTransaction {
            val volumes = outlineDao.activeVolumes(bookId)
            val index = volumes.indexOfFirst { it.id == volumeId }
            if (index < 0) throw NotFoundException("volume", volumeId)
            val target = index + offset
            if (target < 0 || target >= volumes.size) return@withTransaction // 越界 = 无操作
            val current = volumes[index]
            val neighbor = volumes[target]
            outlineDao.setVolumePosition(current.id, neighbor.position)
            outlineDao.setVolumePosition(neighbor.id, current.position)
            touchBook(bookId)
        }
    }

    override suspend fun placeVolume(bookId: String,volumeId: String,index: Int) = db.withTransaction {
        require(index >= 0)
        requireAliveBook(bookId)
        val ordered=outlineDao.activeVolumes(bookId).toMutableList()
        val source=ordered.firstOrNull { it.id==volumeId } ?: throw NotFoundException("volume",volumeId)
        ordered.remove(source);ordered.add(index.coerceAtMost(ordered.size),source)
        ordered.forEachIndexed { i,v -> outlineDao.setVolumePosition(v.id,i.toLong()) }
        touchBook(bookId)
    }

    override suspend fun moveChapter(chapterId: String, targetVolumeId: String, targetIndex: Int) {
        db.withTransaction {
            val chapter = requireAliveChapter(chapterId)
            val target = outlineDao.volumeById(targetVolumeId)
                ?.takeIf { it.deletedAt == null }
                ?: throw NotFoundException("volume", targetVolumeId)
            if (target.bookId != chapter.bookId) throw NotFoundException("volume", targetVolumeId)
            val originalVolumeId = chapter.volumeId
            renumberAfterMove(chapter, targetVolumeId, targetIndex)
            // 跨卷移动(M1 审核修复):原卷剩余存活章按目录序重排 0..n,不留 position 空洞。
            if (originalVolumeId != targetVolumeId) {
                renumberVolume(originalVolumeId)
            }
            touchBook(chapter.bookId)
        }
    }

    override suspend fun moveChapterWithinVolume(chapterId: String, offset: Int) {
        require(offset == -1 || offset == 1) { "offset must be -1 or +1, got $offset" }
        db.withTransaction {
            val chapter = requireAliveChapter(chapterId)
            val siblings = outlineDao.activeChaptersOfVolume(chapter.volumeId)
            val index = siblings.indexOfFirst { it.id == chapterId }
            if (index < 0) throw NotFoundException("chapter", chapterId)
            val target = index + offset
            if (target < 0 || target >= siblings.size) return@withTransaction // 越界 = 无操作
            renumberAfterMove(chapter, chapter.volumeId, target)
            touchBook(chapter.bookId)
        }
    }

    override suspend fun softDeleteVolume(volumeId: String) {
        val timestamp = now()
        val batchId = newId()
        db.withTransaction {
            val volume = outlineDao.volumeById(volumeId)
                ?.takeIf { it.deletedAt == null }
                ?: throw NotFoundException("volume", volumeId)
            if (outlineDao.softDeleteVolume(volumeId, timestamp, batchId) == 0) {
                throw NotFoundException("volume", volumeId)
            }
            outlineDao.cascadeChaptersOfVolume(volumeId, timestamp, batchId)
            touchBook(volume.bookId)
        }
    }

    override suspend fun softDeleteChapter(chapterId: String) {
        val timestamp = now()
        val batchId = newId()
        db.withTransaction {
            val chapter = outlineDao.chapterById(chapterId)
                ?.takeIf { it.deletedAt == null }
                ?: throw NotFoundException("chapter", chapterId)
            if (outlineDao.softDeleteChapter(chapterId, timestamp, batchId) == 0) {
                throw NotFoundException("chapter", chapterId)
            }
            touchBook(chapter.bookId)
        }
    }

    /** 结构性写操作后触碰书 updated_at,保证书架「最近编辑」倒序准确(M1 审核修复)。 */
    private suspend fun touchBook(bookId: String) {
        bookDao.touchUpdated(bookId, now())
    }

    /** 卷内存活章按目录序重排 0..n(跨卷移出后消除原卷空洞)。 */
    private suspend fun renumberVolume(volumeId: String) {
        outlineDao.activeChaptersOfVolume(volumeId).forEachIndexed { index, item ->
            if (item.position != index.toLong()) {
                outlineDao.setChapterPosition(item.id, index.toLong())
            }
        }
    }

    /** 共用重排:把 [chapter] 插到目标卷的 [targetIndex],同级存活项全部重编 0..n(事务内调用)。 */
    private suspend fun renumberAfterMove(chapter: ChapterEntity, targetVolumeId: String, targetIndex: Int) {
        val others = outlineDao.activeChaptersOfVolume(targetVolumeId)
            .filterNot { it.id == chapter.id }
        val insertAt = targetIndex.coerceIn(0, others.size)
        val newOrder = others.toMutableList().apply { add(insertAt, chapter) }
        newOrder.forEachIndexed { index, item ->
            if (item.id == chapter.id) {
                outlineDao.moveChapter(chapter.id, targetVolumeId, index.toLong())
            } else if (item.position != index.toLong()) {
                outlineDao.setChapterPosition(item.id, index.toLong())
            }
        }
    }

    private suspend fun requireAliveChapter(chapterId: String): ChapterEntity =
        outlineDao.chapterById(chapterId)
            ?.takeIf { it.deletedAt == null }
            ?: throw NotFoundException("chapter", chapterId)

    private suspend fun requireAliveBook(bookId: String) {
        val book = bookDao.byId(bookId) ?: throw NotFoundException("book", bookId)
        if (book.deletedAt != null) throw NotFoundException("book", bookId)
    }
}
