package com.qingling.writing

import com.qingling.writing.core.files.*
import com.qingling.writing.core.model.*
import com.qingling.writing.domain.repository.BackupRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.*

@OptIn(ExperimentalCoroutinesApi::class)
class AutoBackupWriterTest {
    @get:Rule val temp = TemporaryFolder()
    private fun repo(snapshot: LibraryBackup = BackupFixture.sample()) = object : BackupRepository {
        override suspend fun capture() = snapshot
        override suspend fun restoreCopies(backup: LibraryBackup) = error("Writer must not restore")
    }
    private class Target(val fault: String = "", val onWrite: () -> Unit = {}) : AutoBackupTarget {
        val bytes = ByteArrayOutputStream()
        var deleted = false
        override fun output(): OutputStream = object : FilterOutputStream(bytes) {
            override fun write(b: ByteArray, off: Int, len: Int) {
                if (fault == "write") throw IOException("Full")
                out.write(b, off, len); onWrite()
            }
            override fun close() { if (fault == "close") throw IOException("Close failed") }
        }
        override fun input(): InputStream {
            val data = bytes.toByteArray()
            return ByteArrayInputStream(when (fault) {
                "corrupt" -> data.copyOf().apply { this[0] = (this[0].toInt() xor 1).toByte() }
                "short" -> data.copyOf(data.size - 1)
                "extra" -> data + byteArrayOf(1)
                else -> data
            })
        }
        override fun delete() { deleted = true }
    }
    @Test fun successfulBackupIsVerifiedRestorableAndStageRemoved() = runTest {
        val root = temp.newFolder(); val target = Target()
        AutoBackupWriter(repo(), BackupFileStore(root)).write { target }
        assertEquals(BackupFixture.sample(), BackupCodec().read(target.input()))
        assertFalse(target.deleted)
        assertTrue(root.listFiles()!!.isEmpty())
    }
    @Test fun writeAndCloseFailureCleanOnlyNewTarget() = runTest {
        for (fault in listOf("write", "close")) {
            val root = temp.newFolder(); val target = Target(fault)
            try { AutoBackupWriter(repo(), BackupFileStore(root)).write { target }; fail("Expected I/O error") }
            catch (_: IOException) { }
            assertTrue(target.deleted); assertTrue(root.listFiles()!!.isEmpty())
        }
    }
    @Test fun corruptionTruncationAndExtraBytesNeverCountAsSuccessful() = runTest {
        for (fault in listOf("corrupt", "short", "extra")) {
            val root = temp.newFolder(); val target = Target(fault)
            try { AutoBackupWriter(repo(), BackupFileStore(root)).write { target }; fail("Expected verification failure") }
            catch (_: IllegalStateException) { }
            assertTrue(target.deleted); assertTrue(root.listFiles()!!.isEmpty())
        }
    }
    @Test fun destinationCreationFailureLeavesNoStagingFiles() = runTest {
        val root = temp.newFolder()
        try { AutoBackupWriter(repo(), BackupFileStore(root)).write { throw SecurityException("Permission revoked") }; fail("Expected denial") }
        catch (_: SecurityException) { }
        assertTrue(root.listFiles()!!.isEmpty())
    }
    @Test fun cancellationRemovesIncompleteFileAndStage() = runTest {
        val root = temp.newFolder()
        lateinit var backupJob: Job
        val target = Target(onWrite = { backupJob.cancel() })
        backupJob = launch { AutoBackupWriter(repo(), BackupFileStore(root)).write { target } }
        backupJob.join()
        assertTrue(backupJob.isCancelled); assertTrue(target.deleted); assertTrue(root.listFiles()!!.isEmpty())
    }
    @Test fun emptyLibraryDoesNotCreateDestination() = runTest {
        var created = false
        val empty = LibraryBackup(BackupFixture.TIME, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        try { AutoBackupWriter(repo(empty), BackupFileStore(temp.newFolder())).write { created = true; Target() }; fail("Expected empty") }
        catch (_: IllegalStateException) { }
        assertFalse(created)
    }
    @Test fun duePolicyRespectsDisableIntervalAndRetryThrottle() {
        val now = 100 * AutoBackupPolicy.HOUR
        assertFalse(AutoBackupPolicy.due(AutoBackupState(folder = "folder"), now))
        assertFalse(AutoBackupPolicy.due(AutoBackupState(enabled = true), now))
        assertTrue(AutoBackupPolicy.due(AutoBackupState(enabled = true, folder = "folder"), now))
        assertFalse(AutoBackupPolicy.due(AutoBackupState(true, "folder", lastSuccess = now - AutoBackupPolicy.HOUR), now))
        assertFalse(AutoBackupPolicy.due(AutoBackupState(true, "folder", lastAttempt = now - 1000), now))
        assertTrue(AutoBackupPolicy.due(AutoBackupState(true, "folder", 24, now - 24 * AutoBackupPolicy.HOUR), now))
    }
    @Test fun summaryNeverCallsUnattemptedBackupSuccessful() {
        assertTrue(AutoBackupPolicy.summary(AutoBackupState(), 100).contains("尚未成功"))
        assertTrue(AutoBackupPolicy.summary(AutoBackupState(lastSuccess = 1), 4 * 24 * AutoBackupPolicy.HOUR).contains("天"))
    }
}
