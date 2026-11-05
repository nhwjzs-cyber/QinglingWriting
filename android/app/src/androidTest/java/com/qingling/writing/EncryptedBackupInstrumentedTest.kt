package com.qingling.writing

import com.qingling.writing.core.files.EncryptedBackup
import com.qingling.writing.core.model.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class EncryptedBackupInstrumentedTest {
    @Test fun androidEncryptDecryptAndRejectWrongPassword() {
        val time = "2026-11-01T00:00:00Z"
        val backup = LibraryBackup(time,
            listOf(BackupBook("b", "作品", "", "", time, time, null, null, "c")),
            listOf(BackupVolume("v", "b", "卷", 0, time, time, null, null)),
            listOf(BackupChapter("c", "b", "v", "章", "文字😀", 0, time, time, null, null)),
            emptyList(), emptyList())
        val output = ByteArrayOutputStream()
        EncryptedBackup.write(backup, output, "password 123".toCharArray())
        val bytes = output.toByteArray()
        assertEquals(backup, EncryptedBackup.read(ByteArrayInputStream(bytes), "password 123".toCharArray()))
        try { EncryptedBackup.read(ByteArrayInputStream(bytes), "wrong password".toCharArray()); fail("wrong password accepted") }
        catch (_: java.security.GeneralSecurityException) { }
    }
}
