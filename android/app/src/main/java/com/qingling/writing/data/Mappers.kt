package com.qingling.writing.data

import com.qingling.writing.core.database.entities.ChapterEntity
import com.qingling.writing.core.database.entities.RevisionEntity
import com.qingling.writing.core.model.ChapterDocument
import com.qingling.writing.core.model.OutlineChapter
import com.qingling.writing.core.model.Revision

/** 实体 → 领域模型映射(data/ 层内复用;DTO/Entity/Domain 分离,docs/03 §3)。 */
internal fun ChapterEntity.toOutlineChapter(): OutlineChapter = OutlineChapter(
    id = id,
    bookId = bookId,
    volumeId = volumeId,
    title = title,
    position = position,
    wordCount = wordCount,
    updatedAt = updatedAt,
)

internal fun ChapterEntity.toDocument(): ChapterDocument = ChapterDocument(
    id = id,
    bookId = bookId,
    volumeId = volumeId,
    title = title,
    body = body,
    editSeq = editSeq,
)

internal fun RevisionEntity.toModel(): Revision = Revision(
    id = id,
    chapterId = chapterId,
    title = title,
    body = body,
    contentSha256 = contentSha256,
    source = Revision.Source.entries.firstOrNull { it.name == source } ?: Revision.Source.AUTO,
    createdAt = createdAt,
)

