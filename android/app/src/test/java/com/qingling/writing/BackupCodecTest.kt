package com.qingling.writing

import com.qingling.writing.core.files.BackupCodec
import com.qingling.writing.core.model.*
import com.qingling.writing.domain.validation.BackupValidator
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupCodecTest {
    private val codec = BackupCodec()
    private fun encode(a: LibraryBackup) = ByteArrayOutputStream().also { codec.write(a, it) }.toByteArray()
    @Test fun readsLegacyAndKeepsCompletionInVersionTwo() {
        val sample = BackupFixture.sample()
        val legacy = ByteArrayOutputStream().also { BackupCodec(writeVersion = 1).write(sample,it) }.toByteArray()
        assertEquals(sample,codec.read(ByteArrayInputStream(legacy)))
        val completed = sample.copy(chapters = sample.chapters.map { it.copy(completed = true) })
        assertEquals(completed,codec.read(ByteArrayInputStream(encode(completed))))
        assertThrows(IllegalArgumentException::class.java) { BackupCodec(writeVersion = 1).write(completed,ByteArrayOutputStream()) }
    }
    private fun entries(bytes: ByteArray): LinkedHashMap<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; result[entry.name] = zip.readBytes() }
        }
        return result
    }
    private fun zip(entries: Map<String, ByteArray>) = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { z -> entries.forEach { (name, bytes) -> z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry() } }
    }.toByteArray()

    @Test fun roundTripPreservesUnicodeWhitespaceDeletedRowsHistoryAndPosition() {
        val archive = BackupFixture.sample()
        assertEquals(archive, codec.read(ByteArrayInputStream(encode(archive))))
    }
    @Test fun emptyLibraryIsRepresentableWithoutInventingContent() {
        val a = LibraryBackup(BackupFixture.TIME, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(a, codec.read(ByteArrayInputStream(encode(a))))
    }
    @Test fun damagedPayloadFailsChecksumBeforeAnyRestore() {
        val values = entries(encode(BackupFixture.sample()))
        values.getValue("library.bin")[8] = (values.getValue("library.bin")[8].toInt() xor 1).toByte()
        assertThrows(IllegalArgumentException::class.java) { codec.read(ByteArrayInputStream(zip(values))) }
    }
    @Test fun unknownAndTraversalEntriesAreRejected() {
        val values = entries(encode(BackupFixture.sample()))
        values["../writing.db"] = byteArrayOf(1)
        assertThrows(IllegalArgumentException::class.java) { codec.read(ByteArrayInputStream(zip(values))) }
    }
    @Test fun futureVersionIsRejected() {
        val values = entries(encode(BackupFixture.sample()))
        values["manifest.properties"] = String(values.getValue("manifest.properties")).replace(Regex("version=[0-9]+"), "version=999").toByteArray()
        assertThrows(IllegalArgumentException::class.java) { codec.read(ByteArrayInputStream(zip(values))) }
    }
    @Test fun oversizedCompressedPayloadIsBoundedWhileExpanding() {
        val bytes = encode(BackupFixture.sample("a".repeat(5000)))
        assertThrows(IllegalArgumentException::class.java) { BackupCodec(1024).read(ByteArrayInputStream(bytes)) }
        assertThrows(IllegalArgumentException::class.java) { BackupCodec(1024).write(BackupFixture.sample("b".repeat(5000)), ByteArrayOutputStream()) }
    }
    @Test fun missingManifestAndNonArchiveAreRejected() {
        assertThrows(Exception::class.java) { codec.read(ByteArrayInputStream(byteArrayOf(1, 2, 3))) }
        assertThrows(Exception::class.java) { codec.read(ByteArrayInputStream(zip(mapOf("library.bin" to byteArrayOf(0))))) }
    }
    @Test fun duplicateIdsAreRejected() {
        val a = BackupFixture.sample()
        assertThrows(IllegalArgumentException::class.java) { BackupValidator.validate(a.copy(chapters = a.chapters + a.chapters.first())) }
    }
    @Test fun crossBookVolumeReferenceIsRejected() {
        val a = BackupFixture.sample()
        assertThrows(IllegalArgumentException::class.java) { BackupValidator.validate(a.copy(chapters = a.chapters.map { it.copy(bookId = "foreign") })) }
    }
    @Test fun danglingHistoryAndInvalidActivePointerAreRejected() {
        val a = BackupFixture.sample()
        assertThrows(IllegalArgumentException::class.java) { BackupValidator.validate(a.copy(revisions = a.revisions.map { it.copy(chapterId = "missing") })) }
        assertThrows(IllegalArgumentException::class.java) { BackupValidator.validate(a.copy(books = a.books.map { it.copy(lastChapter = "missing") })) }
    }
    @Test fun deletedParentCannotHaveLiveChild() {
        val a = BackupFixture.sample()
        assertThrows(IllegalArgumentException::class.java) { BackupValidator.validate(a.copy(volumes = a.volumes.map { it.copy(deletedAt = BackupFixture.TIME, batch = "batch") })) }
    }
    @Test fun deletionAndNegativePositionMustBeValid() {
        val a = BackupFixture.sample()
        assertThrows(IllegalArgumentException::class.java) { BackupValidator.validate(a.copy(chapters = a.chapters.map { it.copy(batch = null) })) }
        assertThrows(IllegalArgumentException::class.java) { BackupValidator.validate(a.copy(positions = a.positions.map { it.copy(start = -1) })) }
    }
    @Test fun longChineseChapterAndSurrogatesRemainExact() {
        val a = BackupFixture.sample("　文字👩‍🚀\n\n".repeat(15000))
        assertEquals(a, codec.read(ByteArrayInputStream(encode(a))))
    }
}
