package com.qingling.writing

import com.qingling.writing.core.files.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.*

class BackupFileStoreTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun stagedSnapshotSurvivesCopyAndCanBeReadBack() {
        val files = BackupFileStore(temp.newFolder())
        val a = BackupFixture.sample(); val stage = files.stage(a)
        val output = ByteArrayOutputStream(); files.copyTo(stage) { output }
        assertEquals(a, BackupCodec().read(ByteArrayInputStream(output.toByteArray())))
    }
    @Test fun missingOrChangedStagingDoesNotOpenDestination() {
        val files = BackupFileStore(temp.newFolder()); val stage = files.stage(BackupFixture.sample())
        stage.file.writeText("modified"); var opened = false
        assertThrows(IllegalArgumentException::class.java) { files.copyTo(stage) { opened = true; ByteArrayOutputStream() } }
        assertFalse(opened)
    }
    @Test fun nullProviderAndCloseFailureAreNotSuccessful() {
        val files = BackupFileStore(temp.newFolder()); val stage = files.stage(BackupFixture.sample())
        assertThrows(IOException::class.java) { files.copyTo(stage) { null } }
        assertThrows(IOException::class.java) { files.copyTo(stage) { object : ByteArrayOutputStream() {
            override fun close() { throw IOException("provider close failed") }
        } } }
    }
}
