package com.qingling.writing

import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.ViewModelStore
import com.qingling.writing.core.model.*
import com.qingling.writing.data.*
import com.qingling.writing.domain.repository.WritingRepository
import com.qingling.writing.feature.editor.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.io.File
import java.util.UUID

class SupplementalProtectionTest {
    private val c get() = ApplicationProvider.getApplicationContext<WritingApplication>().container
    @Test fun n04OlderCommitKeepsNewerPendingThreeTimes() = runBlocking { repeat(3) { withContext(Dispatchers.Main) { withTimeout(20000) {
        val first=c.writingRepository.createBook(GuestSpace.ID,"二轮并发提交","","")
        val entered1=CompletableDeferred<Unit>();val entered2=CompletableDeferred<Unit>()
        val release1=CompletableDeferred<Unit>();val release2=CompletableDeferred<Unit>()
        val repo=object:WritingRepository by c.writingRepository {override suspend fun saveDraft(d:ChapterDraft):SaveResult {
            if(d.editSeq==1L){entered1.complete(Unit);release1.await()}else{entered2.complete(Unit);release2.await()}
            return c.writingRepository.saveDraft(d)
        }}
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        val writer=SaveCoordinatorImpl(first.id,0,repo,c.historyRepository,scope,{java.time.Instant.now().toString()},0,c.recoveryDrafts,first.title)
        try {
            writer.offer(ChapterDraft(first.id,"第一次标题","第一份正文😀",1,titleEdited=true));entered1.await()
            writer.offer(ChapterDraft(first.id,"第二次标题","更新正文不能被旧提交清除😀",2,titleEdited=true))
            release1.complete(Unit);entered2.await()
            val journal=c.recoveryDrafts.read("editor:"+first.id)!!
            assertEquals(2L,journal.getLong("seq"));assertEquals(1L,journal.getLong("base"))
            assertEquals("第一次标题",journal.getString("baseTitle"));assertEquals("更新正文不能被旧提交清除😀",journal.getString("text"))
            assertEquals("第一份正文😀",c.writingRepository.readChapter(first.id).body)
            release2.complete(Unit);assertTrue(writer.flush(first.id,2) is SaveResult.Committed)
            assertEquals("更新正文不能被旧提交清除😀",c.writingRepository.readChapter(first.id).body)
            assertNull(c.recoveryDrafts.read("editor:"+first.id))
        } finally {release1.complete(Unit);release2.complete(Unit);scope.cancel();c.recoveryDrafts.remove("editor:"+first.id)}
    } } } }
    @Test fun n05BlockedJournalPathStillAllowsDatabaseSaveThreeTimes()=runBlocking {repeat(3){withContext(Dispatchers.Main){
        val app=ApplicationProvider.getApplicationContext<WritingApplication>()
        val blocker=File(app.cacheDir,"blocked-journal-"+UUID.randomUUID()).apply{writeText("a file, not directory")}
        val journal=RecoveryDraftStore(blocker)
        val first=c.writingRepository.createBook(GuestSpace.ID,"二轮草稿路径故障","","")
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        val writer=SaveCoordinatorImpl(first.id,0,c.writingRepository,c.historyRepository,scope,{java.time.Instant.now().toString()},60000,journal,first.title)
        try {
            writer.offer(ChapterDraft(first.id,"可保存标题","草稿目录不可写但数据库仍可保存😀",1,titleEdited=true))
            assertNotNull(journal.problem.value);assertNull(journal.editor(first.id))
            assertTrue(writer.flush(first.id,1) is SaveResult.Committed)
            assertEquals("草稿目录不可写但数据库仍可保存😀",c.writingRepository.readChapter(first.id).body)
            assertEquals("a file, not directory",blocker.readText())
        }finally{scope.cancel();blocker.delete()}
    }}}
    @Test fun n06TruncatedJournalDoesNotHideGoodDraftThreeTimes(){repeat(3){
        val app=ApplicationProvider.getApplicationContext<WritingApplication>()
        val dir=File(app.cacheDir,"malformed-journal-"+UUID.randomUUID())
        val original=RecoveryDraftStore(dir);assertTrue(original.put("good",JSONObject().put("kind","quick").put("text","完整草稿😀\n  尾空格  ")))
        val broken=File(dir,"broken.json").apply{writeText("{\"text\":\"truncated")}
        val loaded=RecoveryDraftStore(dir)
        assertEquals("完整草稿😀\n  尾空格  ",loaded.read("good")!!.getString("text"))
        assertNotNull(loaded.problem.value);assertTrue(broken.exists());assertEquals("{\"text\":\"truncated",broken.readText())
    }}
    private fun backupThreeTimes(test:(M4BackupInstrumentedTest)->Unit){repeat(3){val fixture=M4BackupInstrumentedTest();fixture.setup();try{test(fixture)}finally{fixture.cleanup()}}}
    @Test fun n07FullBackupRoundTripThreeTimes()=backupThreeTimes{it.fullRoundTripCreatesSeparateCopiesWithHistoryCursorAndDeletionBatches()}
    @Test fun n08RestoreTransactionFailureThreeTimes()=backupThreeTimes{it.constraintFailureRollsBackAllNewRowsAndPreservesOriginals()}
    @Test fun n09InvalidBackupReferenceThreeTimes()=backupThreeTimes{it.corruptCrossBookReferenceFailsBeforeAnyInsert()}
    @Test fun n10RepeatedRestoreIndependenceThreeTimes()=backupThreeTimes{it.repeatedRestoreCreatesIndependentCopiesWithoutChangingPreviousCopies()}
    @Test fun n11AlreadyCommittedJournalClearsWithoutChangingTextThreeTimes()=runBlocking {repeat(3){withContext(Dispatchers.Main){withTimeout(15000){
        val first=c.writingRepository.createBook(GuestSpace.ID,"二轮提交后未清理","","")
        val d=ChapterDraft(first.id,"已提交标题😀","　　已提交的正文\n\n尾空格  ",1,titleEdited=true)
        assertTrue(c.recoveryDrafts.putEditor(d,0,first.title));assertTrue(c.writingRepository.saveDraft(d) is SaveResult.Committed)
        val vm=EditorViewModel(c,c.outlineRepository,first.id);val models=ViewModelStore();models.put("vm",vm)
        try {val session=vm.editorSession.filterNotNull().first();assertEquals(d.body,session.editorState.text.toString());assertEquals(d.title,session.titleState.text.toString());assertNull(c.recoveryDrafts.read("editor:"+first.id));assertEquals(1L,c.writingRepository.readChapter(first.id).editSeq)}
        finally{models.clear();c.recoveryDrafts.remove("editor:"+first.id)}
    }}}}
    @Test fun n12DeletedChapterDoesNotResurrectOrEraseDraftThreeTimes()=runBlocking {repeat(3){withContext(Dispatchers.Main){withTimeout(15000){
        val first=c.writingRepository.createBook(GuestSpace.ID,"二轮已删除章节","","")
        val d=ChapterDraft(first.id,first.title,"已删除章节关联的未保存全文😀",1)
        assertTrue(c.recoveryDrafts.putEditor(d,0,first.title));c.outlineRepository.softDeleteChapter(first.id)
        val vm=EditorViewModel(c,c.outlineRepository,first.id);val models=ViewModelStore();models.put("vm",vm)
        try {assertEquals(EditorUiState.EmptyBook(first.bookId,"二轮已删除章节"),vm.uiState.first{it!=EditorUiState.Loading});assertEquals(d.body,c.recoveryDrafts.editor(first.id)!!.body);assertFalse(c.writingRepository.observeChapterOrder(first.bookId).first().contains(first.id))}
        finally{models.clear();c.recoveryDrafts.remove("editor:"+first.id)}
    }}}}
}
