package com.qingling.writing

import com.qingling.writing.core.files.*
import com.qingling.writing.core.model.*
import com.qingling.writing.data.IdleBackupController
import com.qingling.writing.domain.repository.BackupRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class IdleBackupControllerTest {
    @get:Rule val temp = TemporaryFolder()
    private var fail = false
    private fun writer() = AutoBackupWriter(object : BackupRepository {
        override suspend fun capture(): LibraryBackup { check(!fail); return BackupFixture.sample() }
        override suspend fun restoreCopies(backup: LibraryBackup) = error("Unused")
    }, BackupFileStore(temp.newFolder()))
    @Test fun waitsForQuietPeriodAndDoesNotBackupUnchangedNotifications() = runTest {
        val c = IdleBackupController(temp.newFolder(), writer(), syncDirectory={}) {}
        val input = MutableStateFlow(listOf("a:1"))
        backgroundScope.launch { c.monitor(input) }; runCurrent()
        advanceTimeBy(4_000); input.value = listOf("a:2"); runCurrent()
        advanceTimeBy(4_999); runCurrent(); assertEquals(0, c.state.value.files.size)
        advanceTimeBy(1); runCurrent(); assertEquals(1, c.state.value.files.size)
        input.value = listOf("a:2"); advanceTimeBy(10_000); runCurrent()
        assertEquals(1, c.state.value.files.size)
        assertEquals(BackupFixture.sample(), c.state.value.files.single().inputStream().use { BackupCodec().read(it) })
    }
    @Test fun keepsTwentyVerifiedGenerationsAndFailurePreservesAllOldCopies() = runTest {
        var external = 0
        val c = IdleBackupController(temp.newFolder(), writer(), syncDirectory={}) { external++ }
        val input = MutableStateFlow(listOf("a:0"))
        backgroundScope.launch { c.monitor(input) }; runCurrent()
        repeat(23) { input.value = listOf("a:$it"); runCurrent(); advanceTimeBy(5_000); runCurrent() }
        assertEquals(20, c.state.value.files.size)
        c.state.value.files.forEach { f -> f.inputStream().use { BackupCodec().read(it) } }
        val old = c.state.value.files.toList(); fail = true
        input.value = listOf("a:failed"); runCurrent(); advanceTimeBy(5_000); runCurrent()
        assertEquals(old, c.state.value.files); assertTrue(old.all { it.exists() })
        assertNotNull(c.state.value.error); assertEquals(24, external)
    }
    @Test fun restartSkipsAlreadyProtectedVersionAndExternalFailureDoesNotInvalidateLocal() = runTest {
        val root = temp.newFolder(); val input = MutableStateFlow(listOf("a:1"))
        val first = IdleBackupController(root, writer(), syncDirectory={}) { error("External permission expired") }
        val job = backgroundScope.launch { first.monitor(input) }; runCurrent(); advanceTimeBy(5_000); runCurrent()
        assertEquals(1, first.state.value.files.size); job.cancelAndJoin()
        val next = IdleBackupController(root, writer(), syncDirectory={}) { error("Must not run") }
        backgroundScope.launch { next.monitor(input) }; runCurrent(); advanceTimeBy(5_000); runCurrent()
        assertEquals(first.state.value.files, next.state.value.files); assertNull(next.state.value.error)
    }
    @Test fun missingLatestFileDoesNotLetOlderCopySatisfyCheckpoint() = runTest {
        val root = temp.newFolder(); val input = MutableStateFlow(listOf("a:1"))
        val c = IdleBackupController(root, writer(), syncDirectory={}) {}
        val job = backgroundScope.launch { c.monitor(input) }; runCurrent(); advanceTimeBy(5_000); runCurrent()
        input.value = listOf("a:2"); runCurrent(); advanceTimeBy(5_000); runCurrent()
        job.cancelAndJoin(); c.state.value.files.first().delete()
        val next = IdleBackupController(root, writer(), syncDirectory={}) {}
        backgroundScope.launch { next.monitor(input) }; runCurrent(); advanceTimeBy(5_000); runCurrent()
        assertEquals(2, next.state.value.files.size)
    }
    @Test fun directorySyncFailureNeverPrunesPreviouslyVerifiedBackups() = runTest {
        val root=temp.newFolder()
        repeat(20){java.io.File(root,"%019d-old.qlbackup".format(it.toLong())).writeText("old-$it")}
        val old=root.listFiles()!!.associate{it.name to it.readText()}
        val c=IdleBackupController(root,writer(),syncDirectory={throw java.io.IOException("directory sync fault")}){}
        backgroundScope.launch{c.monitor(MutableStateFlow(listOf("a:changed")))}
        runCurrent();advanceTimeBy(5_000);runCurrent()
        assertNotNull(c.state.value.error)
        old.forEach{(name,text)->assertEquals(text,java.io.File(root,name).readText())}
    }
}
