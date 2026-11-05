package com.qingling.writing.core.files

import com.qingling.writing.core.model.LibraryBackup
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

class BackupFileStore(private val root: File, private val codec: BackupCodec = BackupCodec()) {
    data class Staged(val file: File, val name: String, val checksum: String)
    fun stage(backup: LibraryBackup, singleBook: Boolean = false, password: CharArray? = null): Staged {
        check(root.mkdirs() || root.isDirectory)
        val file = File(root, "${UUID.randomUUID()}.qlbackup")
        try {
            if (password == null) codec.write(backup, file.outputStream())
            else EncryptedBackup.write(backup, file.outputStream(), password)
            file.inputStream().use { if (password == null) codec.read(it) else EncryptedBackup.read(it, password) } // Verify before presenting a Save action.
            val prefix = if (password != null) "轻灵写作" else if (singleBook) ExportFileStore.safeFileName(buildString { backup.books.single().title.codePoints().limit(40).forEach { appendCodePoint(it) } }).removeSuffix(".txt")+"-单书" else "轻灵写作"
            return Staged(file, "$prefix-${backup.createdAt.take(10)}${if (password == null) "" else "-加密"}.qlbackup", checksum(file))
        } catch (failure: Exception) { file.delete(); throw failure }
    }
    fun copyTo(staged: Staged, openOutput: () -> OutputStream?) {
        require(staged.file.canonicalFile.parentFile == root.canonicalFile)
        require(staged.file.isFile && checksum(staged.file) == staged.checksum) { "BACKUP_STAGED_CHANGED" }
        staged.file.inputStream().use { input ->
            val stream = openOutput() ?: throw IOException("No output stream")
            stream.use { input.copyTo(it); it.flush() }
        }
    }
    private fun checksum(file: File): String {
        val hash = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) { val count = input.read(buffer); if (count < 0) break; hash.update(buffer, 0, count) }
        }
        return hash.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
