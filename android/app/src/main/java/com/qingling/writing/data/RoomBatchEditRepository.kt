package com.qingling.writing.data

import androidx.room.withTransaction
import com.qingling.writing.core.database.WritingDatabase
import com.qingling.writing.core.database.entities.RevisionEntity
import com.qingling.writing.core.model.*
import com.qingling.writing.core.processing.BatchEditEngine
import com.qingling.writing.domain.validation.WordCounter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map

class RoomBatchEditRepository(private val db: WritingDatabase, private val newId: () -> String,
    private val now: () -> String) {
    fun observeVolumes(bookId: String) = db.outlineDao().observeActiveVolumes(bookId).map { rows -> rows.map { BatchVolume(it.id, it.title) } }
    private suspend fun capture(scope: BatchScope): BatchSource {
        val book = checkNotNull(db.bookDao().byId(scope.bookId)) { "作品不存在" }
        check(book.spaceId == GuestSpace.ID && book.deletedAt == null) { "作品不可编辑" }
        scope.volumeId?.let { id ->
            val volume = checkNotNull(db.outlineDao().volumeById(id))
            check(volume.bookId == book.id && volume.deletedAt == null)
        }
        require(db.batchEditDao().count(scope.bookId, scope.volumeId) <= BatchEditEngine.MAX_CHAPTERS) { "超过 1000 章，请按卷处理" }
        require(db.batchEditDao().size(scope.bookId, scope.volumeId) <= BatchEditEngine.MAX_BYTES) { "正文超过 8 MB，请按卷处理" }
        return BatchSource(scope, book.title, db.batchEditDao().documents(scope.bookId, scope.volumeId))
    }
    suspend fun preview(scope: BatchScope, operation: BatchOperation): BatchPlan {
        val source = db.withTransaction { capture(scope) }
        return withContext(Dispatchers.Default) { BatchEditEngine.prepare(source, operation) }
    }
    suspend fun apply(plan: BatchPlan): Int = db.withTransaction {
        check(capture(plan.source.scope) == plan.source) { "预览已过期：目录或正文发生变化，请重新预览" }
        check(BatchEditEngine.prepare(plan.source, plan.operation).changes == plan.changes) { "预览不一致，请重新生成" }
        check(plan.changes.isNotEmpty()) { "没有需要修改的章节" }
        val source = plan.source.documents.associateBy { it.id }
        val timestamp = now()
        plan.changes.forEach { change ->
            val before = source.getValue(change.chapterId)
            check(before.editSeq < Long.MAX_VALUE)
            db.revisionDao().insert(RevisionEntity(newId(), before.id, before.title, before.body,
                ContentHash.of(before.body), Revision.Source.BEFORE_CHANGE.name, timestamp))
            check(db.outlineDao().saveChapterBodyGuarded(before.id, change.after, WordCounter.count(change.after),
                before.editSeq + 1, timestamp) == 1)
        }
        check(db.bookDao().bumpLocalSeq(plan.source.scope.bookId, timestamp) == 1)
        plan.changes.size
    }
}
