package com.qingling.writing.data

import androidx.room.withTransaction
import com.qingling.writing.core.database.WritingDatabase
import com.qingling.writing.core.database.entities.*
import com.qingling.writing.core.model.*
import com.qingling.writing.domain.validation.TitleValidator
import com.qingling.writing.domain.validation.WordCounter

class ChapterStructureRepository(private val db: WritingDatabase,private val id: () -> String,
    private val now: () -> String) {
    private suspend fun capture(bookId: String,ids: List<String>): StructureSnapshot {
        require(ids.isNotEmpty() && ids.size <= ChapterStructure.MAX_CHAPTERS && ids.distinct().size == ids.size) { "单次最多处理 200 个不同章节" }
        val book = checkNotNull(db.bookDao().byId(bookId)) { "作品已不存在" }
        check(book.deletedAt == null && book.spaceId == GuestSpace.ID) { "作品不可编辑" }
        require(db.structureDao().size(bookId,ids) <= ChapterStructure.MAX_BYTES) { "单次处理正文不超过 8 MB" }
        val documents = db.structureDao().selected(bookId,ids)
        check(documents.size == ids.size) { "所选章节已删除或不属于本书，请重新选择" }
        return StructureSnapshot(bookId,book.localSeq,documents)
    }
    suspend fun previewSplit(bookId: String,chapterId: String,expectedBody: String,offset: Int): StructurePlan = db.withTransaction {
        val source = capture(bookId,listOf(chapterId))
        check(source.documents.single().body == expectedBody) { "正文状态已变化，请重新打开拆分" }
        ChapterStructure.split(expectedBody,offset)
        StructurePlan(source,splitOffset = offset)
    }
    suspend fun previewMerge(bookId: String,ids: List<String>,titles: Boolean): StructurePlan = db.withTransaction {
        val source = capture(bookId,ids)
        ChapterStructure.merge(source.documents,titles)
        StructurePlan(source,includeTitles = titles)
    }
    /** Entire rewrite, history, deletion markers, positions and resume pointer commit together. */
    suspend fun apply(plan: StructurePlan,title: String): String = db.withTransaction {
        val checked = TitleValidator.checkSectionTitle(title)
        require(checked.error == null) { "标题不能为空、不能换行，最多 120 字" }
        val source = plan.source
        check(capture(source.bookId,source.documents.map { it.id }) == source) { "预览已过期：正文或目录已变化，请重新预览" }
        val stamp = now()
        val first = source.documents.first()
        check(source.documents.all { it.editSeq < Long.MAX_VALUE }) { "章节版本已超出可处理范围" }
        fun revision(row: BatchDocument) = RevisionEntity(id(),row.id,row.title,row.body,ContentHash.of(row.body),Revision.Source.BEFORE_CHANGE.name,stamp)
        val target: String
        if (plan.splitOffset != null) {
            require(source.documents.size == 1 && !plan.includeTitles)
            val (left,right) = ChapterStructure.split(first.body,plan.splitOffset)
            db.revisionDao().insert(revision(first))
            check(db.outlineDao().saveChapterBodyGuarded(first.id,left,WordCounter.count(left),first.editSeq+1,stamp) == 1)
            val siblings = db.outlineDao().activeChaptersOfVolume(first.volumeId)
            val sourceIndex = siblings.indexOfFirst { it.id == first.id }
            target = id()
            check(db.bookDao().incrementChapterNumber(source.bookId) == 1) { "章节编号已达上限" }
            db.outlineDao().insertChapter(ChapterEntity(target,source.bookId,first.volumeId,checked.value,right,
                siblings.size.toLong(),0,WordCounter.count(right),stamp,stamp,null,null))
            val order = siblings.map { it.id }.toMutableList().apply { add(sourceIndex+1,target) }
            order.forEachIndexed { i,cid -> db.outlineDao().setChapterPosition(cid,i.toLong()) }
            db.editorPositionDao().upsert(EditorPositionEntity(first.id,left.length.toLong(),left.length.toLong(),0))
        } else {
            val joined = ChapterStructure.merge(source.documents,plan.includeTitles)
            source.documents.forEach { db.revisionDao().insert(revision(it)) }
            check(db.outlineDao().renameChapter(first.id, checked.value, stamp) == 1)
            check(db.outlineDao().restoreChapterContent(first.id,checked.value,joined,WordCounter.count(joined),first.editSeq+1,stamp) == 1)
            source.documents.drop(1).forEach { check(db.outlineDao().softDeleteChapter(it.id,stamp,id()) == 1) }
            source.documents.map { it.volumeId }.distinct().forEach { vid ->
                db.outlineDao().activeChaptersOfVolume(vid).forEachIndexed { index,c -> db.outlineDao().setChapterPosition(c.id,index.toLong()) }
            }
            target = first.id
        }
        db.progressDao().setDetails(ChapterDetailsEntity(first.id,false))
        db.editorPositionDao().upsert(EditorPositionEntity(target,0,0,0))
        db.activeDao().upsertBookActive(BookActiveEntity(source.bookId,target,stamp))
        db.activeDao().upsertSpaceActive(SpaceActiveEntity(GuestSpace.ID,source.bookId,target,stamp))
        db.structureDao().setActive(source.bookId,target)
        check(db.bookDao().bumpLocalSeq(source.bookId,stamp) == 1)
        target
    }
}
