package com.qingling.writing.core.files

import com.qingling.writing.domain.repository.BackupRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

interface AutoBackupTarget {
    fun output(): OutputStream
    fun input(): InputStream
    fun delete()
}

/** Append-only destination. Only a newly created, incomplete file may be removed. */
class AutoBackupWriter(private val repository: BackupRepository, private val files: BackupFileStore) {
    suspend fun write(create: () -> AutoBackupTarget) {
        val snapshot = repository.capture()
        check(snapshot.books.isNotEmpty() || snapshot.planning.isNotEmpty()) { "AUTO_BACKUP_EMPTY" }
        val staged = files.stage(snapshot)
        var target: AutoBackupTarget? = null
        var verified = false
        try {
            currentCoroutineContext().ensureActive()
            val created = create().also { target = it }
            staged.file.inputStream().use { input -> created.output().use { output ->
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                output.flush()
            } }
            // Read the destination back: closing an output stream alone is not proof of a good backup.
            val hash = MessageDigest.getInstance("SHA-256")
            var size = 0L
            created.input().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    size += count
                    check(size <= staged.file.length()) { "AUTO_BACKUP_VERIFY" }
                    hash.update(buffer, 0, count)
                }
            }
            val checksum = hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            check(size == staged.file.length() && checksum == staged.checksum) { "AUTO_BACKUP_VERIFY" }
            currentCoroutineContext().ensureActive()
            verified = true
        } finally {
            if (!verified) runCatching { target?.delete() }
            staged.file.delete()
        }
    }
}
