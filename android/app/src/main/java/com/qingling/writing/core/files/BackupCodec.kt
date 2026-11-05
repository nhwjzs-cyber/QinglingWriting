package com.qingling.writing.core.files

import com.qingling.writing.core.model.*
import com.qingling.writing.domain.validation.BackupValidator
import java.io.*
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** v1: two fixed ZIP entries; no filesystem extraction, Java object deserialization or SQL execution. */
class BackupCodec(private val payloadLimit: Int = MAX_PAYLOAD, private val writeVersion: Int = VERSION) {
    fun write(a: LibraryBackup, output: OutputStream) {
        BackupValidator.validate(a)
        require(writeVersion in 1..VERSION)
        require(writeVersion >= 7 || (a.books.none { it.chapterNumberCounter != 0L } && a.chapters.none { it.automaticNumber != 0L || !it.titleLocked })) { "BACKUP_VERSION_DATA_LOSS" }
        require(writeVersion>=6 || a.planning.none{it.bookId.isEmpty()}) {"BACKUP_VERSION_DATA_LOSS"}
        require(writeVersion >= 2 || a.chapters.none { it.completed })
        require(writeVersion >= 3 || a.planning.isEmpty()) { "BACKUP_VERSION_DATA_LOSS" }
        require(writeVersion >= 4 || a.books.none { it.targetWords != 0L }) { "BACKUP_VERSION_DATA_LOSS" }
        require(writeVersion >= 5 || a.books.none {it.pinned || it.archived}) { "BACKUP_VERSION_DATA_LOSS" }
        val buffer = ByteArrayOutputStream()
        val bounded = object : FilterOutputStream(buffer) {
            private var size = 0L
            override fun write(b: Int) { require(++size <= payloadLimit) { "BACKUP_LIMIT" }; out.write(b) }
            override fun write(b: ByteArray, off: Int, len: Int) {
                size += len; require(size <= payloadLimit) { "BACKUP_LIMIT" }; out.write(b, off, len)
            }
        }
        DataOutputStream(bounded).use { d ->
            d.writeInt(writeVersion); d.string(a.createdAt)
            d.rows(a.books) { b -> string(b.id); string(b.title); string(b.author); string(b.description)
                string(b.createdAt); string(b.updatedAt); optional(b.deletedAt); optional(b.batch); optional(b.lastChapter);if(writeVersion >= 4) writeLong(b.targetWords)
                if(writeVersion >= 5) {writeBoolean(b.pinned);writeBoolean(b.archived)}
                if(writeVersion >= 7) writeLong(b.chapterNumberCounter) }
            d.rows(a.volumes) { v -> string(v.id); string(v.bookId); string(v.title); writeLong(v.position)
                string(v.createdAt); string(v.updatedAt); optional(v.deletedAt); optional(v.batch) }
            d.rows(a.chapters) { c -> string(c.id); string(c.bookId); string(c.volumeId); string(c.title); string(c.body)
                writeLong(c.position); string(c.createdAt); string(c.updatedAt); optional(c.deletedAt); optional(c.batch); if (writeVersion >= 2) writeBoolean(c.completed)
                if(writeVersion >= 7) { writeLong(c.automaticNumber); writeBoolean(c.titleLocked) } }
            d.rows(a.revisions) { r -> string(r.id); string(r.chapterId); string(r.title); string(r.body); string(r.source); string(r.createdAt) }
            d.rows(a.positions) { p -> string(p.chapterId); writeLong(p.start); writeLong(p.end); writeLong(p.scroll) }
            if (writeVersion >= 3) d.rows(a.planning) { p -> string(p.id);if(writeVersion>=6) optional(p.bookId.takeIf{it.isNotEmpty()}) else string(p.bookId);optional(p.chapterId);optional(p.volumeId)
                string(p.kind);string(p.title);string(p.body);string(p.updatedAt);writeLong(p.version) }
        }
        val bytes = buffer.toByteArray()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.properties"))
            zip.write("format=qingling-writing\nversion=$writeVersion\nsha256=${sha256(bytes)}\n".toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("library.bin")); zip.write(bytes); zip.closeEntry()
        }
    }

    fun read(input: InputStream): LibraryBackup {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && entry.name in setOf("manifest.properties", "library.bin") && entry.name !in entries) { "BACKUP_ENTRY" }
                entries[entry.name] = readBounded(zip, if (entry.name == "library.bin") payloadLimit else 4096)
                zip.closeEntry()
            }
        }
        require(entries.size == 2) { "BACKUP_ENTRIES" }
        val manifest = Properties().apply { load(ByteArrayInputStream(entries.getValue("manifest.properties"))) }
        require(manifest.getProperty("format") == "qingling-writing" && manifest.getProperty("version") in setOf("1", "2", "3", "4", "5", "6", "7")) { "BACKUP_VERSION" }
        val payload = entries.getValue("library.bin")
        require(manifest.getProperty("sha256") == sha256(payload)) { "BACKUP_CHECKSUM" }
        return DataInputStream(ByteArrayInputStream(payload)).use { d ->
            val version = d.readInt()
            require(version in 1..VERSION && version.toString() == manifest.getProperty("version")) { "BACKUP_VERSION" }
            val a = LibraryBackup(d.string(),
                d.rows(500) { BackupBook(string(), string(), string(), string(), string(), string(), optional(), optional(), optional())
                    .let {if(version>=4) it.copy(targetWords=readLong()) else it}.let {if(version>=5) it.copy(pinned=readBoolean(),archived=readBoolean()) else it}
                    .let {if(version>=7) it.copy(chapterNumberCounter=readLong()) else it} },
                d.rows(10_000) { BackupVolume(string(), string(), string(), readLong(), string(), string(), optional(), optional()) },
                d.rows(50_000) { BackupChapter(string(), string(), string(), string(), string(), readLong(), string(), string(), optional(), optional()).let { c ->
                    c.copy(completed = if (version < 2) false else when (readUnsignedByte()) { 0 -> false; 1 -> true; else -> error("BACKUP_BOOLEAN") }) }
                    .let { if(version>=7) it.copy(automaticNumber=readLong(), titleLocked=when(readUnsignedByte()) { 0 -> false; 1 -> true; else -> error("BACKUP_BOOLEAN") }) else it } },
                d.rows(50_000) { BackupRevision(string(), string(), string(), string(), string(), string()) },
                d.rows(50_000) { BackupPosition(string(), readLong(), readLong(), readLong()) },
                if (version < 3) emptyList() else d.rows(50_000) { PlanningEntry(string(),if(version>=6) optional().orEmpty() else string(),optional(),optional(),string(),string(),string(),string(),readLong()) })
            require(d.read() == -1) { "BACKUP_TRAILING_DATA" }
            BackupValidator.validate(a)
            a
        }
    }

    private fun DataOutputStream.string(value: String) {
        require(value.length <= MAX_STRING) { "BACKUP_STRING" }
        val encoded = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value))
        val bytes = ByteArray(encoded.remaining()); encoded.get(bytes)
        require(bytes.size <= MAX_STRING) { "BACKUP_STRING" }
        writeInt(bytes.size); write(bytes)
    }
    private fun DataInputStream.string(): String {
        val size = readInt()
        require(size in 0..minOf(MAX_STRING, available())) { "BACKUP_STRING" }
        val bytes = ByteArray(size); readFully(bytes)
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    }
    private fun DataOutputStream.optional(value: String?) { writeBoolean(value != null); if (value != null) string(value) }
    private fun DataInputStream.optional(): String? = when (readUnsignedByte()) { 0 -> null; 1 -> string(); else -> error("BACKUP_BOOLEAN") }
    private fun <T> DataOutputStream.rows(rows: List<T>, writeRow: DataOutputStream.(T) -> Unit) {
        writeInt(rows.size); rows.forEach { writeRow(it) }
    }
    private fun <T> DataInputStream.rows(max: Int, readRow: DataInputStream.() -> T): List<T> {
        val size = readInt(); require(size in 0..max) { "BACKUP_COUNT" }; return List(size) { readRow() }
    }
    private fun readBounded(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream(); val buffer = ByteArray(8192); var total = 0L
        while (true) {
            val count = input.read(buffer); if (count < 0) break
            total += count; require(total <= limit) { "BACKUP_LIMIT" }; output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
    companion object {
        const val MAX_PAYLOAD = 32 * 1024 * 1024
        private const val MAX_STRING = 8 * 1024 * 1024
        private const val VERSION = 7
        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
