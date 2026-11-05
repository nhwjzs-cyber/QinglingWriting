package com.qingling.writing.domain.repository

import com.qingling.writing.core.model.BookSearchHit

interface SearchRepository {
    /** One result per active chapter, in outline order. At most 201 rows (200 + overflow indicator). */
    suspend fun searchBook(bookId: String, literal: String): List<BookSearchHit>
}
