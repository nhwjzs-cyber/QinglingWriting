package com.qingling.writing

import androidx.lifecycle.ViewModelStore
import com.qingling.writing.core.files.BackupFileStore
import com.qingling.writing.core.files.BackupCodec
import com.qingling.writing.core.model.*
import com.qingling.writing.domain.repository.BackupRepository
import com.qingling.writing.feature.recovery.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.io.*

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {
    @get:Rule val temp = TemporaryFolder()
    private val models = ViewModelStore()
    private val dispatcher = StandardTestDispatcher()
    private var restores = 0
    private var capturePause: CompletableDeferred<Unit>? = null
    private val captureStarted = CompletableDeferred<Unit>()
    private val repository = object : BackupRepository {
        override suspend fun capture(): LibraryBackup {
            captureStarted.complete(Unit)
            capturePause?.await()
            return BackupFixture.sample()
        }
        override suspend fun restoreCopies(backup: LibraryBackup): BackupRestoreResult {
            restores++; return BackupRestoreResult(backup.books.size, backup.chapters.size)
        }
    }
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { models.clear(); Dispatchers.resetMain() }
    private fun model() = BackupViewModel(repository, BackupFileStore(temp.newFolder())).also { models.put("vm", it) }
    @Test fun cancelledPickerNeverReportsExternalBackupSaved() = runTest {
        val vm = model(); assertNotNull(vm.beginSave()); assertTrue(vm.ui.value.awaitingSave)
        vm.finishSave(null)
        assertEquals(BackupNotice.CANCELLED, vm.ui.value.notice); assertFalse(vm.ui.value.awaitingSave)
    }
    @Test fun failedProviderDoesNotReportSavedAndUnlocks() = runTest {
        val vm = model(); vm.beginSave(); vm.finishSave { throw IOException("unavailable") }
        vm.ui.first { !it.busy && it.notice == BackupNotice.FAILED }
        assertFalse(vm.ui.value.awaitingSave)
    }
    @Test fun inspectCannotMutateDatabaseAndRestoreRequiresExplicitAction() = runTest {
        val vm = model()
        val output = ByteArrayOutputStream(); BackupCodec().write(BackupFixture.sample(), output)
        vm.inspect { ByteArrayInputStream(output.toByteArray()) }
        vm.ui.first { !it.busy && it.preview != null }
        assertEquals(0, restores)
        vm.restore(); vm.ui.first { !it.busy && it.notice == BackupNotice.RESTORED }
        assertEquals(1, restores); assertNull(vm.ui.value.preview)
    }
    @Test fun invalidBackupDoesNotReachRestoreRepository() = runTest {
        val vm = model(); vm.inspect { ByteArrayInputStream(byteArrayOf(1, 2)) }
        vm.ui.first { !it.busy && it.notice == BackupNotice.INVALID }
        vm.restore(); assertEquals(0, restores)
    }
    @Test fun encryptedBackupRequiresPasswordAndWrongPasswordCannotRestore() = runTest {
        val vm = model()
        val output = ByteArrayOutputStream()
        com.qingling.writing.core.files.EncryptedBackup.write(BackupFixture.sample(), output, "safe password".toCharArray())
        val bytes = output.toByteArray()
        vm.inspect { ByteArrayInputStream(bytes) }
        vm.ui.first { !it.busy && it.notice == BackupNotice.PASSWORD_REQUIRED }
        vm.retryPassword("wrong password".toCharArray())
        vm.ui.first { !it.busy && it.notice == BackupNotice.PASSWORD_ERROR }
        vm.restore(); assertEquals(0, restores)
        vm.retryPassword("safe password".toCharArray())
        vm.ui.first { !it.busy && it.preview != null }
        assertEquals(0, restores)
        vm.restore(); vm.ui.first { !it.busy && it.notice == BackupNotice.RESTORED }
        assertEquals(1, restores)
    }
    @Test fun cancelledPreparationUnlocksSurvivingViewModel() = runTest {
        capturePause = CompletableDeferred()
        val vm = model()
        val request = launch { vm.beginSave() }
        captureStarted.await()
        assertTrue(vm.ui.value.busy)
        request.cancelAndJoin()
        assertFalse(vm.ui.value.busy); assertFalse(vm.ui.value.awaitingSave)
    }
}
