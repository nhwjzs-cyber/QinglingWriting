package com.qingling.writing.core.files

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

/** Owns only cache/exports/<uuid>/<safe-name>.txt. Never exposes database or arbitrary paths. */
class ExportFileStore(private val root: File) {
    data class Staged(val token: String, val fileName: String, val file: File)
    data class Preview(val text: String, val truncated: Boolean)

    fun stage(title: String, bytes: ByteArray,extension: String = "txt"): Staged {
        require(extension in setOf("txt","docx"))
        root.mkdirs()
        val directory = File(root, UUID.randomUUID().toString())
        check(directory.mkdir()) { "Cannot create export cache" }
        val file = File(directory, safeFileName(title,extension))
        try { file.outputStream().use { it.write(bytes); it.flush() } }
        catch (e: Exception) { file.delete(); directory.delete(); throw e }
        return Staged("${directory.name}/${file.name}", file.name, file)
    }

    fun resolve(token: String): File {
        val parts = token.split('/')
        require(parts.size == 2 && UUID_PATTERN.matches(parts[0]))
        val extension = parts[1].substringAfterLast('.',"")
        require(extension in setOf("txt","docx"))
        require(parts[1] == safeFileName(parts[1].removeSuffix(".$extension"),extension))
        val file = File(root, token)
        require(file.canonicalPath.startsWith(root.canonicalPath + File.separator))
        if (!file.isFile) throw IOException("Export cache is missing")
        return file
    }

    /** Success means write, flush AND close succeeded. A null provider stream is a failure. */
    fun copyTo(token: String, openOutput: () -> OutputStream?) {
        resolve(token).inputStream().use { input ->
            val stream = openOutput() ?: throw IOException("Provider returned no output stream")
            stream.use { output -> input.copyTo(output); output.flush() }
        }
    }

    fun preview(token: String, limit: Int = 8000): Preview {
        require(resolve(token).extension == "txt") { "Binary documents need a text preview" }
        val text = StringBuilder()
        resolve(token).reader(Charsets.UTF_8).use { reader ->
            val buffer = CharArray(1024)
            while (text.length <= limit) {
                val n = reader.read(buffer, 0, minOf(buffer.size, limit + 1 - text.length))
                if (n < 0) break
                text.append(buffer, 0, n)
            }
        }
        val truncated = text.length > limit
        var result = text.take(limit).toString()
        if (result.lastOrNull()?.isHighSurrogate() == true) result = result.dropLast(1)
        return Preview(result, truncated)
    }

    /** Leave recent shared files available to receiving apps; prune only our old flat directories. */
    fun pruneExpired(nowMs: Long = System.currentTimeMillis()) {
        root.listFiles()?.filter { it.isDirectory && UUID_PATTERN.matches(it.name) &&
            nowMs - it.lastModified() > 7L * 24 * 60 * 60 * 1000 }?.forEach { dir ->
            dir.listFiles()?.filter { it.isFile && it.extension in setOf("txt","docx") }?.forEach { it.delete() }
            dir.delete()
        }
    }

    companion object {
        private val UUID_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        fun safeFileName(title: String,extension: String = "txt"): String {
            require(extension in setOf("txt","docx"))
            val clean = title.map { if (it.code < 32 || it in "\\/:*?\"<>|") '_' else it }
                .joinToString("").trim().trim('.').ifBlank { "未命名作品" }
            val name = buildString { clean.codePoints().limit(80).forEach { appendCodePoint(it) } }
            return "$name.$extension"
        }
    }
}
