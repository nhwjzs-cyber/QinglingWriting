package com.qingling.writing.data

import com.qingling.writing.core.database.WritingDatabase
import com.qingling.writing.domain.repository.SearchRepository

internal class RoomSearchRepository(private val db: WritingDatabase) : SearchRepository {
    override suspend fun searchBook(bookId: String, literal: String) =
        if (literal.isEmpty()) emptyList() else db.outlineDao().searchBook(bookId, literal)
}
