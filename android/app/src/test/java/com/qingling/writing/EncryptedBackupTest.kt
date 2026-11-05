package com.qingling.writing

import com.qingling.writing.core.files.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PushbackInputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EncryptedBackupTest {
    @get:Rule val temp = TemporaryFolder()
    private fun encode(password: String = "strong password"): ByteArray = ByteArrayOutputStream().also {
        EncryptedBackup.write(BackupFixture.sample(), it, password.toCharArray())
    }.toByteArray()

    @Test fun roundTripAndRandomizedCiphertext() {
        val a = encode(); val b = encode()
        assertFalse(a.contentEquals(b))
        assertTrue(EncryptedBackup.isEncrypted(PushbackInputStream(ByteArrayInputStream(a), 4)))
        assertEquals(BackupFixture.sample(), EncryptedBackup.read(ByteArrayInputStream(a), "strong password".toCharArray()))
        assertFalse(String(a, Charsets.ISO_8859_1).contains(BackupFixture.sample().books.first().title))
    }

    @Test fun wrongPasswordAndTamperingAreRejected() {
        val data = encode()
        assertThrows(Exception::class.java) { EncryptedBackup.read(ByteArrayInputStream(data), "wrong password".toCharArray()) }
        data[data.lastIndex] = (data.last().toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { EncryptedBackup.read(ByteArrayInputStream(data), "strong password".toCharArray()) }
    }

    @Test fun changedHeaderAndTruncatedFilesAreRejected() {
        val data = encode()
        data[5] = (data[5].toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { EncryptedBackup.read(ByteArrayInputStream(data), "strong password".toCharArray()) }
        assertThrows(Exception::class.java) { EncryptedBackup.read(ByteArrayInputStream(data.copyOf(20)), "strong password".toCharArray()) }
    }

    @Test fun encryptedStageHasNoPlainBookNameAndCopies() {
        val files = BackupFileStore(temp.newFolder())
        val staged = files.stage(BackupFixture.sample(), singleBook = true, password = "strong password".toCharArray())
        assertFalse(staged.name.contains(BackupFixture.sample().books.first().title))
        val copy = ByteArrayOutputStream(); files.copyTo(staged) { copy }
        assertEquals(BackupFixture.sample(), EncryptedBackup.read(ByteArrayInputStream(copy.toByteArray()), "strong password".toCharArray()))
    }
}
