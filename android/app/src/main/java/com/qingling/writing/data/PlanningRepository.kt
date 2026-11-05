package com.qingling.writing.data

import androidx.room.withTransaction
import com.qingling.writing.core.database.WritingDatabase
import com.qingling.writing.core.database.entities.toEntity
import com.qingling.writing.core.model.*
import com.qingling.writing.domain.repository.OutlineRepository
import com.qingling.writing.domain.repository.WritingRepository
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.util.UUID

class PlanningConflict : IllegalStateException("内容已在另一处修改，请保留当前文字并重新载入")

class PlanningRepository(private val db: WritingDatabase,
    private val outline: OutlineRepository, private val writing: WritingRepository,
    private val id: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> String = { Instant.now().toString() }) {
    fun inbox() = db.planningDao().inbox(GuestSpace.ID).map { rows -> rows.map { it.toEntry() }
        .filterNot { QuickNote.decode(it.body).deleted } }
    suspend fun capture(text: String, bookId: String? = null, chapterId: String = "",
        sourceTitle: String = "", offset: Int = 0, foreshadow: Boolean = false, sourceQuote:String=""): PlanningEntry = db.withTransaction {
        require((text.isNotBlank() || sourceQuote.isNotBlank()) && text.length+sourceQuote.length <= 50_000) { "请输入内容，单条最多 5 万字符" }
        db.spaceDao().insert(com.qingling.writing.core.database.entities.SpaceEntity(GuestSpace.ID,GuestSpace.KIND_GUEST,null))
        val target = bookId ?: writing.resolveLastActiveChapter()?.let { writing.readChapter(it).bookId }
            ?: ""
        val body=if(chapterId.isNotBlank()) writing.readChapter(chapterId).body else ""
        val point=offset.coerceIn(0,body.length)
        val validQuote=sourceQuote.takeIf {it.isNotEmpty() && body.startsWith(it,point)}.orEmpty()
        require(sourceQuote.isEmpty() || validQuote.isNotEmpty()) {"来源正文已变更，请重新选取原文"}
        var before=(point-24).coerceAtLeast(0);if(before>0 && Character.isLowSurrogate(body[before]))before--
        var after=(point+validQuote.length+24).coerceAtMost(body.length);if(after<body.length && Character.isLowSurrogate(body[after]))after++
        save(PlanningEntry(id(),target,null,null,PlanningKind.INSPIRATION.name,QuickNote.title(text.ifBlank {validQuote}),
            QuickNote(text,chapterId,sourceTitle,point,foreshadow,capturedAt=now(),sourceQuote=validQuote,
                sourceBefore=if(validQuote.isEmpty()) "" else body.substring(before,point),
                sourceAfter=if(validQuote.isEmpty()) "" else body.substring(point+validQuote.length,after),sourceEnd=point+validQuote.length,sourceKind=if(validQuote.isNotEmpty()) "QUOTE" else if(chapterId.isNotEmpty()) "CHAPTER" else "FREE").encode(),now()))
    }
    suspend fun updateNote(entry: PlanningEntry, note: QuickNote): PlanningEntry =
        save(entry.copy(body = note.copy(capturedAt=note.capturedAt.ifBlank{entry.updatedAt}).encode(),title=if(note.text!=QuickNote.decode(entry.body).text) QuickNote.title(note.text.ifBlank {note.sourceQuote}) else entry.title))
    suspend fun saveNoteWithOwner(entry:PlanningEntry,text:String,target:String):PlanningEntry = db.withTransaction {
        val updated=updateNote(entry,QuickNote.decode(entry.body).copy(text=text))
        if(updated.bookId==target)updated else moveNote(updated,target)
    }
    suspend fun moveNote(entry:PlanningEntry,target:String):PlanningEntry = db.withTransaction {
        require(entry.kind==PlanningKind.INSPIRATION.name)
        val current=db.planningDao().byId(entry.id)?.toEntry() ?: throw PlanningConflict()
        if(current.version!=entry.version || current.bookId!=entry.bookId) throw PlanningConflict()
        if(target.isNotEmpty()) require(db.bookDao().byId(target)?.deletedAt==null && db.bookDao().byId(target)!=null) {"目标作品已不存在"}
        if(db.planningDao().moveNote(entry.id,target.takeIf{it.isNotEmpty()},QuickNote.decode(entry.body).let{it.copy(capturedAt=it.capturedAt.ifBlank{entry.updatedAt}).encode()},now(),entry.version)!=1)throw PlanningConflict()
        if(entry.bookId.isNotEmpty())db.bookDao().bumpLocalSeq(entry.bookId,now())
        if(target.isNotEmpty())db.bookDao().bumpLocalSeq(target,now())
        requireNotNull(load(entry.id))
    }
    suspend fun moveNotesAndDeleteBook(bookId:String,target:String) = db.withTransaction {
        require(bookId!=target) {"请选择另一本书或通用灵感箱"}
        db.planningDao().bookNotes(bookId).forEach {moveNote(it.toEntry(),target)}
        writing.softDeleteBook(bookId)
    }
    suspend fun deleteUserEntry(entry:PlanningEntry):PlanningEntry = db.withTransaction {
        val current=db.planningDao().byId(entry.id)?.toEntry() ?: throw PlanningConflict()
        require(current.chapterId==null && current.volumeId==null) {"细纲和概要请在所属章节或分卷中编辑"}
        if(current.version!=entry.version) throw PlanningConflict()
        if(db.planningDao().deleteUserEntry(current.id,current.version)!=1) throw PlanningConflict()
        if(current.bookId.isNotEmpty())db.bookDao().bumpLocalSeq(current.bookId,now())
        current
    }
    suspend fun undoDeleteUserEntry(entry:PlanningEntry):PlanningEntry = db.withTransaction {
        require(entry.chapterId==null && entry.volumeId==null)
        check(db.planningDao().byId(entry.id)==null) {"条目已存在，未重复恢复"}
        require(entry.version>0 && entry.version<Long.MAX_VALUE)
        PlanningRules.validate(entry)
        if(entry.bookId.isNotEmpty()) {val book=db.bookDao().byId(entry.bookId);require(book!=null && book.deletedAt==null){"作品已被删除，未恢复"}}
        val restored=entry.copy(version=entry.version+1,updatedAt=now())
        db.planningDao().insert(restored.toEntity())
        if(entry.bookId.isNotEmpty())db.bookDao().bumpLocalSeq(entry.bookId,now())
        restored
    }
    fun observe(bookId: String) = db.planningDao().observe(bookId).map { rows -> rows.map { it.toEntry() } }
    fun search(bookId: String,query: String,kind: String) = db.planningDao().search(bookId,
        "%"+query.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%",kind).map { rows -> rows.map { it.toEntry() } }
    suspend fun load(id: String): PlanningEntry? = db.planningDao().byId(id)?.toEntry()
    suspend fun scoped(bookId: String,kind: PlanningKind,chapterId: String? = null,volumeId: String? = null): PlanningEntry {
        val existing = when(kind) {
            PlanningKind.CHAPTER_OUTLINE -> db.planningDao().forChapter(requireNotNull(chapterId))
            PlanningKind.VOLUME_SUMMARY -> db.planningDao().forVolume(requireNotNull(volumeId))
            else -> null
        }
        if (existing != null) { require(existing.bookId == bookId); return existing.toEntry() }
        return PlanningEntry(id(),bookId,chapterId,volumeId,kind.name,kind.label,"",now())
    }
    suspend fun save(entry: PlanningEntry): PlanningEntry = db.withTransaction {
        PlanningRules.validate(entry)
        if(entry.bookId.isNotEmpty()) { val book=db.bookDao().byId(entry.bookId)
            require(book!=null && book.deletedAt==null) {"作品已被删除，未保存"} }
        entry.chapterId?.let { cid ->
            val c = db.outlineDao().chapterById(cid)
            require(c != null && c.bookId == entry.bookId && c.deletedAt == null) { "章节已不存在" }
        }
        entry.volumeId?.let { vid ->
            val v = db.outlineDao().volumeById(vid)
            require(v != null && v.bookId == entry.bookId && v.deletedAt == null) { "分卷已不存在" }
        }
        val existing = db.planningDao().byId(entry.id)
        val stamp = now()
        if (existing == null) {
            if (entry.version != 0L) throw PlanningConflict()
            val scoped = entry.chapterId?.let { db.planningDao().forChapter(it) }
                ?: entry.volumeId?.let { db.planningDao().forVolume(it) }
            if (scoped != null) throw PlanningConflict()
            db.planningDao().insert(entry.copy(updatedAt = stamp,version = 1).toEntity())
        } else {
            require(existing.bookId == entry.bookId && existing.kind == entry.kind &&
                existing.chapterId == entry.chapterId && existing.volumeId == entry.volumeId)
            if (db.planningDao().update(entry.id,entry.title,entry.body,stamp,entry.version) != 1) throw PlanningConflict()
        }
        if(entry.bookId.isNotEmpty()) db.bookDao().bumpLocalSeq(entry.bookId,stamp)
        requireNotNull(db.planningDao().byId(entry.id)).toEntry()
    }
    /** 保留便签，复制为新章；原条目版本变化时拒绝使用过期正文。 */
    suspend fun toChapter(entry: PlanningEntry,volumeId: String): ChapterDocument = db.withTransaction {
        val current = db.planningDao().byId(entry.id)?.toEntry() ?: throw PlanningConflict()
        if (current != entry || current.kind != PlanningKind.INSPIRATION.name) throw PlanningConflict()
        val created = outline.createChapter(entry.bookId,volumeId,entry.title)
        val result = writing.saveDraft(ChapterDraft(created.id,created.title,QuickNote.decode(entry.body).text,1))
        check(result is SaveResult.Committed) { "新章保存未成功" }
        writing.readChapter(created.id)
    }
}
