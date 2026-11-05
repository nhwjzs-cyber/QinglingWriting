package com.qingling.writing.domain.repository

import com.qingling.writing.core.model.BackupRestoreResult
import com.qingling.writing.core.model.LibraryBackup
import com.qingling.writing.core.model.forBook

interface BackupRepository {
    /** One consistent transaction, including soft-deleted records and history. */
    suspend fun capture(): LibraryBackup
    suspend fun captureBook(bookId: String): LibraryBackup = capture().forBook(bookId)
    /** Fresh identities in one transaction; never overwrite any existing row. */
    suspend fun restoreCopies(backup: LibraryBackup): BackupRestoreResult
}
