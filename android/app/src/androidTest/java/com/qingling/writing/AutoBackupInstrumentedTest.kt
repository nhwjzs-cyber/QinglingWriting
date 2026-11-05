package com.qingling.writing

import android.app.job.JobScheduler
import android.content.Intent
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qingling.writing.core.files.BackupCodec
import com.qingling.writing.core.model.*
import com.qingling.writing.data.AutoBackupController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoBackupInstrumentedTest {
    private val app get() = ApplicationProvider.getApplicationContext<WritingApplication>()
    private val container get() = app.container
    private val tree get() = BackupTestDocumentsProvider.TREE
    @Before fun initialize() = runBlocking {
        container.database.spaceDao().insert(com.qingling.writing.core.database.entities.SpaceEntity(
            GuestSpace.ID, GuestSpace.KIND_GUEST, null))
        container.autoBackup.configure(false, 6)
        container.autoBackupStore.configure("", false, 6)
        Unit
    }
    private suspend fun grant(failRead: Boolean = false) {
        app.startActivity(Intent().setClassName("com.qingling.writing.test", BackupTestGrantActivity::class.java.name)
            .putExtra("failRead", failRead).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        withTimeout(15_000) {
            while (true) {
                try { container.autoBackup.selectFolder(tree); break }
                catch (_: SecurityException) { delay(100) }
            }
        }
    }
    private fun files(): List<String> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, "root")
        return app.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)!!.use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }
    }
    @After fun disable() = runBlocking {
        container.autoBackup.configure(false, 6)
        container.autoBackupStore.configure("test-reset", false, 6)
        container.autoBackupStore.configure("", false, 6)
        Unit
    }

    @Test fun folderGrantWritesVerifiedRestorableBackupAndDisableKeepsFiles() = runBlocking {
        val doc = container.writingRepository.createBook(GuestSpace.ID, "自动备份验证", "", "")
        container.writingRepository.saveDraft(ChapterDraft(doc.id, doc.title, "自动备份正文😀", 1))
        grant()
        val before = files().toSet()
        container.autoBackup.runBackup()
        val after = files()
        val added = after.filter { it !in before }
        assertTrue(added.isNotEmpty())
        assertTrue(container.autoBackupStore.state.first().lastSuccess > 0)
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, added.last())
        val snapshot = app.contentResolver.openInputStream(uri)!!.use { BackupCodec().read(it) }
        assertTrue(snapshot.chapters.any { it.id == doc.id && it.body == "自动备份正文😀" })
        val restored = container.backupRepository.restoreCopies(snapshot)
        assertTrue(restored.books > 0)
        val scheduler = app.getSystemService(JobScheduler::class.java)
        assertTrue(scheduler.getPendingJob(AutoBackupController.JOB_ID)!!.isPersisted)
        container.autoBackup.configure(true, 24)
        assertEquals(24 * AutoBackupPolicy.HOUR, scheduler.getPendingJob(AutoBackupController.JOB_ID)!!.intervalMillis)
        container.autoBackup.configure(false, 24)
        assertNull(scheduler.getPendingJob(AutoBackupController.JOB_ID))
        assertTrue(files().containsAll(after))
    }
    @Test fun failedReadbackDoesNotAdvanceSuccessAndKeepsOldFiles() = runBlocking {
        container.writingRepository.createBook(GuestSpace.ID, "失败验证", "", "")
        grant(failRead = true)
        container.autoBackupStore.success(123)
        val before = files().toSet()
        container.autoBackup.runBackup()
        val state = container.autoBackupStore.state.first()
        assertEquals(123L, state.lastSuccess)
        assertTrue(state.error.isNotEmpty())
        assertEquals(before, files().toSet())
    }
    @Test fun scheduleFailureIsNotClearedBySuccessfulWriteStatus() = runBlocking {
        container.autoBackupStore.scheduleStatus("test scheduling error")
        container.autoBackupStore.success(456)
        assertEquals("test scheduling error", container.autoBackupStore.state.first().scheduleError)
    }
    @Test fun systemJobRunsDueBackupAndStaysScheduled() = runBlocking {
        container.writingRepository.createBook(GuestSpace.ID, "后台任务验证", "", "")
        grant()
        container.autoBackup.configure(false, 6)
        container.autoBackupStore.configure("", false, 6)
        container.autoBackupStore.configure(tree.toString(), true, 6)
        container.autoBackup.configure(true, 6)
        val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.executeShellCommand("cmd jobscheduler run -f com.qingling.writing ${AutoBackupController.JOB_ID}").close()
        val result = withTimeout(20_000) { container.autoBackupStore.state.first { it.lastSuccess > 0 || it.error.isNotEmpty() } }
        assertTrue(result.error, result.lastSuccess > 0)
        assertTrue(files().isNotEmpty())
        assertNotNull(app.getSystemService(JobScheduler::class.java).getPendingJob(AutoBackupController.JOB_ID))
    }
}
