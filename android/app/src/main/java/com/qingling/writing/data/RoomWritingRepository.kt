package com.qingling.writing.data

import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import androidx.room.withTransaction
import com.qingling.writing.core.database.WritingDatabase
import com.qingling.writing.core.database.entities.BookEntity
import com.qingling.writing.core.database.entities.ChapterEntity
import com.qingling.writing.core.database.entities.EditorPositionEntity
import com.qingling.writing.core.database.entities.SpaceActiveEntity
import com.qingling.writing.core.database.entities.VolumeEntity
import com.qingling.writing.core.database.entities.BookActiveEntity
import com.qingling.writing.core.model.BookSummary
import com.qingling.writing.core.model.ChapterDocument
import com.qingling.writing.core.model.ChapterDraft
import com.qingling.writing.core.model.EditorPosition
import com.qingling.writing.core.model.SaveFailure
import com.qingling.writing.core.model.SaveResult
import com.qingling.writing.core.model.GuestSpace
import com.qingling.writing.domain.NotFoundException
import com.qingling.writing.domain.TitleValidationException
import com.qingling.writing.domain.repository.WritingRepository
import com.qingling.writing.domain.validation.TitleError
import com.qingling.writing.domain.validation.TitleValidator
import com.qingling.writing.domain.validation.WordCounter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * WritingRepository 的 Room 实现(docs/03 §3:data/ 层)。
 * 事务边界:创建书与级联删除/恢复都在 withTransaction 内,保证原子性(docs/06 M1);
 * 时间一律 UTC RFC3339(contracts 头注);错误为明确类型(docs/05 §2)。
 * M2:saveDraft 为正文唯一写路径(editSeq 防旧写 + 磁盘错误分类)。
 */
internal class RoomWritingRepository(
    private val db: WritingDatabase,
    private val newId: () -> String,
    private val now: () -> String,
) : WritingRepository {

    private val bookDao get() = db.bookDao()
    private val outlineDao get() = db.outlineDao()

    override fun observeBooks(spaceId: String): Flow<List<BookSummary>> =
        bookDao.observeBookSummaries(spaceId).map { rows ->
            rows.map { row ->
                BookSummary(
                    id = row.id,
                    title = row.title,
                    wordCount = row.wordCount,
                    updatedAt = row.updatedAt,
                    chapterCount = row.chapterCount,
                    continueChapterId = row.continueChapterId,
                    completedCount = row.completedCount,
                    author=row.author,createdAt=row.createdAt,pinned=row.pinned,archived=row.archived,
                )
            }
        }

    override suspend fun pinBook(bookId: String,value: Boolean) = shelfChange(bookId,pinned=value)
    override suspend fun archiveBook(bookId: String,value: Boolean) = shelfChange(bookId,archived=value)
    private suspend fun shelfChange(bookId: String,pinned: Boolean?=null,archived: Boolean?=null) = db.withTransaction {
        val book=bookDao.byId(bookId)?.takeIf {it.deletedAt==null && it.spaceId==GuestSpace.ID} ?: throw NotFoundException("book",bookId)
        val old=db.shelfFlagsDao().byId(book.id) ?: com.qingling.writing.core.database.entities.ShelfFlagsEntity(book.id,false,false)
        db.shelfFlagsDao().put(old.copy(pinned=pinned ?: old.pinned,archived=archived ?: old.archived))
        check(db.shelfFlagsDao().changed(book.id)==1)
    }

    override suspend fun owningBookOfChapter(chapterId:String):String? {
        val chapter=outlineDao.chapterById(chapterId) ?: return null
        return chapter.bookId.takeIf{bookDao.byId(it)?.deletedAt==null && bookDao.byId(it)!=null}
    }
    override suspend fun ensureFirstChapter(bookId:String):ChapterDocument = db.withTransaction {
        val book=bookDao.byId(bookId)?.takeIf{it.deletedAt==null} ?: throw NotFoundException("book",bookId)
        db.activeDao().firstAliveChapterId(book.id)?.let{return@withTransaction readChapter(it)}
        val volume=outlineDao.activeVolumes(book.id).firstOrNull()?.id ?: newId().also {vid ->
            val stamp=now();outlineDao.insertVolume(VolumeEntity(vid,book.id,FIRST_VOLUME_TITLE,0,stamp,stamp,null,null))
        }
        RoomOutlineRepository(db,newId,now).createChapter(book.id,volume,"")
    }
    override suspend fun createEmptyBook(spaceId:String,title:String,author:String,description:String):String = db.withTransaction {
        val checked=TitleValidator.checkBookTitle(title)
        if(checked.error!=null) throw TitleValidationException(checked.error)
        val space=db.spaceDao().byId(spaceId) ?: throw NotFoundException("space",spaceId)
        val id=newId();val stamp=now()
        bookDao.insert(BookEntity(id,space.id,checked.value,author.trim(),description.trim(),stamp,stamp,null,null,0L,0L,null,true))
        id
    }

    override suspend fun createBook(
        spaceId: String,
        title: String,
        author: String,
        description: String,
    ): ChapterDocument {
        val checked = TitleValidator.checkBookTitle(title)
        if (checked.error != null) throw TitleValidationException(checked.error)
        val space = db.spaceDao().byId(spaceId) ?: throw NotFoundException("space", spaceId)
        val bookId = newId()
        val volumeId = newId()
        val chapterId = newId()
        val timestamp = now()
        db.withTransaction {
            bookDao.insert(
                BookEntity(
                    id = bookId,
                    spaceId = space.id,
                    title = checked.value,
                    author = author.trim(),
                    description = description.trim(),
                    createdAt = timestamp,
                    updatedAt = timestamp,
                    deletedAt = null,
                    deletionBatchId = null,
                    localSeq = 0L,
                    syncedLocalSeq = 0L,
                    remoteVersion = null,
                    localDirty = true,
                    lastActiveChapterId = chapterId,
                    chapterNumberCounter = 1,
                ),
            )
            outlineDao.insertVolume(
                VolumeEntity(
                    id = volumeId,
                    bookId = bookId,
                    title = FIRST_VOLUME_TITLE,
                    position = outlineDao.nextVolumePosition(bookId),
                    createdAt = timestamp,
                    updatedAt = timestamp,
                    deletedAt = null,
                    deletionBatchId = null,
                ),
            )
            outlineDao.insertChapter(
                ChapterEntity(
                    id = chapterId,
                    bookId = bookId,
                    volumeId = volumeId,
                    title = FIRST_CHAPTER_TITLE,
                    body = "",
                    position = outlineDao.nextChapterPosition(volumeId),
                    editSeq = 0L,
                    wordCount = 0L,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                    deletedAt = null,
                    deletionBatchId = null,
                ),
            )
        }
        return ChapterDocument(
            id = chapterId,
            bookId = bookId,
            volumeId = volumeId,
            title = FIRST_CHAPTER_TITLE,
            body = "",
            editSeq = 0L,
        )
    }

    override suspend fun readChapter(chapterId: String): ChapterDocument {
        val chapter = outlineDao.chapterById(chapterId)
        if (chapter == null || chapter.deletedAt != null) throw NotFoundException("chapter", chapterId)
        return chapter.toDocument()
    }
    override fun observeChapter(chapterId:String):Flow<ChapterDocument?> =
        outlineDao.observeChapterById(chapterId).map { it?.toDocument() }
    override fun observeChapterOrder(bookId: String) = db.batchEditDao().chapterOrder(bookId)

    override suspend fun saveDraft(draft: ChapterDraft): SaveResult {
        val timestamp = now()
        return try {
            db.withTransaction {
                val chapter = outlineDao.chapterById(draft.chapterId)
                    ?: return@withTransaction SaveResult.Failed(draft.chapterId, draft.editSeq, SaveFailure.DELETED)
                if (chapter.deletedAt != null) {
                    return@withTransaction SaveResult.Failed(draft.chapterId, draft.editSeq, SaveFailure.DELETED)
                }
                val editedTitle = if(draft.titleEdited) {
                    val checked=TitleValidator.checkSectionTitle(com.qingling.writing.core.model.ChapterNaming.fromEditor(draft.title))
                    if(checked.error!=null) return@withTransaction SaveResult.Failed(draft.chapterId,draft.editSeq,SaveFailure.UNKNOWN)
                    checked.value
                } else null
                val updated = outlineDao.saveChapterBodyGuarded(
                    id = draft.chapterId,
                    body = draft.body,
                    wordCount = WordCounter.count(draft.body),
                    editSeq = draft.editSeq,
                    updatedAt = timestamp,
                )
                if (updated == 0) {
                    // 守卫未命中:库内 seq 已 >= 新 seq(旧写/旧确认)或章刚被删除——区分二者。
                    val current = outlineDao.chapterById(draft.chapterId)
                    val reason = if (current == null || current.deletedAt != null) {
                        SaveFailure.DELETED
                    } else {
                        SaveFailure.STALE_SEQUENCE
                    }
                    return@withTransaction SaveResult.Failed(draft.chapterId, draft.editSeq, reason)
                }
                if(editedTitle!=null) check(outlineDao.renameChapter(draft.chapterId,editedTitle,timestamp)==1)
                bookDao.bumpLocalSeq(chapter.bookId, timestamp)
                if(draft.countAsWriting && draft.inputMetrics!=null) {
                    check(draft.inputMetrics.baseSeq <= chapter.editSeq)
                    val day=java.time.Instant.parse(timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
                    db.inputMetricDao().record(chapter.id,day,draft.inputMetrics)
                }
                if (draft.countAsWriting && draft.body != chapter.body) {
                    val day = java.time.Instant.parse(timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
                    db.progressDao().record(day, WordCounter.count(draft.body) - chapter.wordCount)
                    db.bookGoalDao().record(chapter.bookId,day,WordCounter.count(draft.body) - chapter.wordCount)
                }
                val bookLocalSeq = bookDao.localSeq(chapter.bookId) ?: 0L
                SaveResult.Committed(draft.chapterId, draft.editSeq, bookLocalSeq)
            }
        } catch (e: SQLiteFullException) {
            SaveResult.Failed(draft.chapterId, draft.editSeq, SaveFailure.DISK_FULL)
        } catch (e: SQLiteDiskIOException) {
            SaveResult.Failed(draft.chapterId, draft.editSeq, SaveFailure.STORAGE_UNAVAILABLE)
        } catch (e: SQLiteCantOpenDatabaseException) {
            SaveResult.Failed(draft.chapterId, draft.editSeq, SaveFailure.STORAGE_UNAVAILABLE)
        } catch (e: SQLiteException) {
            SaveResult.Failed(draft.chapterId, draft.editSeq, SaveFailure.UNKNOWN)
        }
    }

    override suspend fun readEditorPosition(chapterId: String): EditorPosition? =
        db.editorPositionDao().byChapter(chapterId)?.let {
            EditorPosition(it.chapterId, it.selectionStart.toInt(), it.selectionEnd.toInt(), it.scrollOffset.toInt())
        }

    override suspend fun saveEditorPosition(position: EditorPosition) {
        db.editorPositionDao().upsert(
            EditorPositionEntity(
                chapterId = position.chapterId,
                selectionStart = position.selectionStart.toLong(),
                selectionEnd = position.selectionEnd.toLong(),
                scrollOffset = position.scrollOffset.toLong(),
            ),
        )
    }

    // ---- UX v0.2:最近激活记录 ----

    override suspend fun recordActive(chapterId: String) {
        val chapter = outlineDao.chapterById(chapterId) ?: return
        val bookId = chapter.bookId
        val timestamp = now()
        db.activeDao().upsertBookActive(
            com.qingling.writing.core.database.entities.BookActiveEntity(
                bookId = bookId, lastActiveChapterId = chapterId, updatedAt = timestamp,
            ),
        )
        db.activeDao().upsertSpaceActive(
            SpaceActiveEntity(
                spaceId = GuestSpace.ID,
                lastActiveBookId = bookId,
                lastActiveChapterId = chapterId,
                updatedAt = timestamp,
            ),
        )
    }

    /** 冷启动解析(UX v0.2 §1.2):激活章 → 同书首存活章 → 书架。返回 chapterId 或 null。 */
    override suspend fun resolveLastActiveChapter(): String? {
        val active = db.activeDao().spaceActive(GuestSpace.ID) ?: return null
        val chapterId = active.lastActiveChapterId ?: return null
        val chapter = outlineDao.chapterById(chapterId) ?: return null
        val book=bookDao.byId(chapter.bookId) ?: return null
        if(book.deletedAt!=null || db.shelfFlagsDao().byId(book.id)?.archived==true) return null
        if (chapter.deletedAt == null) return chapterId
        // 回退:章已删,同书首存活章
        val fallback = db.activeDao().firstAliveChapterId(chapter.bookId) ?: return null
        return fallback
    }

    /** 点书续写解析(UX v0.2 §1.2):书内 lastActiveChapterId → 首存活章 → null(空态)。 */
    override suspend fun resolveBookActiveChapter(bookId: String): String? {
        val active = db.activeDao().bookActive(bookId)
        if (active != null) {
            val chapter = outlineDao.chapterById(active.lastActiveChapterId)
            if (chapter != null && chapter.deletedAt == null) return active.lastActiveChapterId
        }
        return db.activeDao().firstAliveChapterId(bookId)
    }

    override suspend fun renameBook(bookId: String, newTitle: String) {
        val checked = TitleValidator.checkBookTitle(newTitle)
        if (checked.error != null) throw TitleValidationException(checked.error)
        if (bookDao.renameActive(bookId, checked.value, now()) == 0) {
            throw NotFoundException("book", bookId)
        }
    }

    override suspend fun softDeleteBook(bookId: String) {
        val timestamp = now()
        val batchId = newId()
        db.withTransaction {
            check(db.planningDao().bookNotes(bookId).none{!com.qingling.writing.core.model.QuickNote.decode(it.body).deleted}) {"请先为本书的灵感选择迁移去向"}
            if (bookDao.softDelete(bookId, timestamp, batchId) == 0) {
                throw NotFoundException("book", bookId)
            }
            // 级联只标记此前存活的项(WHERE deleted_at IS NULL),同一批次(docs/01 §4)。
            outlineDao.cascadeVolumesOfBook(bookId, timestamp, batchId)
            outlineDao.cascadeChaptersOfBook(bookId, timestamp, batchId)
        }
    }

    override suspend fun restoreDeletionBatch(bookId: String, deletionBatchId: String) {
        db.withTransaction {
            val book = bookDao.byId(bookId) ?: throw NotFoundException("book", bookId)
            val batchBelongsToBook = book.deletionBatchId == deletionBatchId ||
                outlineDao.countVolumesInBatchForBook(deletionBatchId, bookId) > 0 ||
                outlineDao.countChaptersInBatchForBook(deletionBatchId, bookId) > 0
            if (!batchBelongsToBook) throw NotFoundException("deletionBatch", deletionBatchId)

            // 祖先链补救(M1 审核修复 P1-2):恢复子孙批次时,若父卷/父书已被
            // 其他批次独立删除,仅清祖先自身的墓碑标记(不扩展到祖先批次的其他成员),
            // 保证恢复后的章在目录中可见,不产生「已恢复却找不到」的孤儿。
            bookDao.reviveSelf(bookId)
            outlineDao.reviveParentVolumesOfBatch(deletionBatchId)

            val bookRestored = bookDao.restore(bookId, deletionBatchId, now())
            outlineDao.restoreVolumesByBatch(deletionBatchId)
            outlineDao.restoreChaptersByBatch(deletionBatchId)
            if (bookRestored == 0) {
                // 恢复的是卷/章批次:书仍在,触碰书 updated_at 使其在书架浮起(最近编辑倒序)。
                bookDao.touchUpdated(bookId, now())
            }
        }
    }

    companion object {
        const val FIRST_VOLUME_TITLE = "第一卷"
        const val FIRST_CHAPTER_TITLE = "未命名章节"
    }
}
