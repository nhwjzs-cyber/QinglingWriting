package com.qingling.writing.data

import androidx.room.withTransaction
import com.qingling.writing.core.database.WritingDatabase
import com.qingling.writing.core.model.ExportChapter
import com.qingling.writing.core.model.ExportSelection
import com.qingling.writing.core.model.ExportSnapshot
import com.qingling.writing.core.model.ExportVolume
import com.qingling.writing.domain.repository.ExportRepository

/**
 * ExportRepository 的 Room 实现(docs/01 §5:按目录顺序,不合并/删除章)。
 * 一个快照内数据一致(事务内读取);不含软删除项。
 * groupBy 保持查询排序(LinkedHashMap 保键插入序),卷和章都在正确目录序。
 */
internal class RoomExportRepository(
    private val db: WritingDatabase,
) : ExportRepository {

    override suspend fun capture(selection: ExportSelection): ExportSnapshot = db.withTransaction {
        val book = db.bookDao().byId(selection.bookId)
            ?.takeIf { it.deletedAt == null }
            ?: throw com.qingling.writing.domain.NotFoundException("book", selection.bookId)

        require(book.spaceId == com.qingling.writing.core.model.GuestSpace.ID)
        require(selection.chapterIds.size <= 1000) { "单次最多导出 1000 章，请分批选择" }
        val chunks = selection.chapterIds.toList().chunked(200)
        require(chunks.sumOf { db.structureDao().size(selection.bookId,it) } <= 8L*1024*1024) { "单次导出正文不超过 8 MB，请按卷或选章导出" }
        val volumeOrder = db.outlineDao().activeVolumes(book.id).associateBy { it.id }
        val chapters = chunks.flatMap { db.outlineDao().exportChapters(selection.bookId,it) }
            .sortedWith(compareBy({ volumeOrder[it.volumeId]?.position ?: Long.MAX_VALUE },{ it.volumeId },{ it.position },{ it.id }))

        // 查询已按卷 position → 章 position 排序;groupBy(LinkedHashMap)保持键序。
        val volumes = chapters
            .groupBy { it.volumeId }
            .map { (volumeId, chapterList) ->
                val volume = db.outlineDao().volumeById(volumeId)!!
                ExportVolume(
                    id = volumeId,
                    title = volume.title,
                    chapters = chapterList.map { ch ->
                        ExportChapter(id = ch.id, volumeId = ch.volumeId, title = ch.title, body = ch.body)
                    },
                )
            }

        ExportSnapshot(bookTitle = book.title, volumes = volumes, bookLocalSeq = book.localSeq)
    }
}
