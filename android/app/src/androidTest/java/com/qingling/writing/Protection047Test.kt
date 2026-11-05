package com.qingling.writing

import android.system.Os
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.qingling.writing.core.database.entities.SpaceEntity
import com.qingling.writing.core.model.*
import com.qingling.writing.data.*
import com.qingling.writing.domain.repository.WritingRepository
import com.qingling.writing.feature.editor.EditorViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class Protection047Test {
    private val app get()=ApplicationProvider.getApplicationContext<WritingApplication>()
    private val c get()=app.container
    private fun root()=File(app.cacheDir,"protection047-"+UUID.randomUUID()).apply{mkdirs()}
    private fun note(text:String)=JSONObject().put("kind","quick").put("text",text)
    private fun primary(root:File)=root.listFiles()!!.single{it.isFile && it.name.endsWith(".json")}
    private fun mirror(root:File)=File(root,"mirror").listFiles()!!.single{it.isFile && it.name.endsWith(".json")}
    private val full get()="春夏秋冬山川湖海日月".repeat(10000)
    @Test fun truncatedPrimaryFallsBackAndWarningSurvivesOtherWrites(){repeat(3){
        val dir=root();val s=RecoveryDraftStore(dir);assertTrue(s.put("q",note(full)))
        val bad=primary(dir);bad.writeText("{broken")
        val loaded=RecoveryDraftStore(dir);assertEquals(full,loaded.read("q")!!.getString("text"));assertNotNull(loaded.problem.value)
        assertTrue(loaded.put("other",note("其他草稿")));assertNotNull(loaded.problem.value);assertEquals("{broken",bad.readText())
    }}
    @Test fun validJsonWithWrongChecksumCannotReplaceOriginal(){repeat(3){
        val dir=root();val s=RecoveryDraftStore(dir);assertTrue(s.put("q",note(full)))
        val bad=primary(dir);val envelope=JSONObject(bad.readText());val record=JSONObject(envelope.getString("payload"))
        record.put("text","被改坏的内容");envelope.put("payload",record.toString());bad.writeText(envelope.toString())
        val loaded=RecoveryDraftStore(dir);assertEquals(full,loaded.read("q")!!.getString("text"));assertNotNull(loaded.problem.value)
    }}
    @Test fun latestMirrorWinsWhenPrimaryWasNotWritable(){repeat(3){
        val dir=root();val s=RecoveryDraftStore(dir);assertTrue(s.put("q",note("旧稿")))
        Os.chmod(dir.path,365)
        try{assertTrue(s.put("q",note(full)));assertNotNull(s.problem.value)}finally{Os.chmod(dir.path,493)}
        val loaded=RecoveryDraftStore(dir);assertEquals(full,loaded.read("q")!!.getString("text"));assertNotNull(loaded.problem.value)
    }}
    @Test fun deletionTombstoneSuppressesStalePrimaryAfterRestart(){repeat(3){
        val dir=root();val s=RecoveryDraftStore(dir);assertTrue(s.put("q",note(full)))
        Os.chmod(dir.path,365)
        try{assertTrue(s.remove("q"));assertNull(s.read("q"))}finally{Os.chmod(dir.path,493)}
        val loaded=RecoveryDraftStore(dir);assertNull(loaded.read("q"));assertTrue(loaded.entries.value.isEmpty())
        assertEquals(full,JSONObject(JSONObject(primary(dir).readText()).getString("payload")).getString("text"))
    }}
    @Test fun newInputAfterTombstoneGetsNewGeneration(){repeat(3){
        val dir=root();val s=RecoveryDraftStore(dir);assertTrue(s.put("q",note("旧稿")));assertTrue(s.remove("q"))
        val tomb=JSONObject(JSONObject(primary(dir).readText()).getString("payload")).getLong("_generation")
        assertTrue(s.put("q",note(full)));val loaded=RecoveryDraftStore(dir)
        assertEquals(full,loaded.read("q")!!.getString("text"));assertTrue(loaded.read("q")!!.getLong("_generation")>tomb)
    }}
    @Test fun legacyEditorJournalCanUpgradeWithoutLosingText(){repeat(3){
        val dir=root();val s=RecoveryDraftStore(dir);val id=UUID.randomUUID().toString();s.putEditor(ChapterDraft(id,"旧标题",full,2),1)
        val file=primary(dir);val legacy=JSONObject(JSONObject(file.readText()).getString("payload")).apply{remove("_generation")}
        file.writeText(legacy.toString());mirror(dir).delete()
        val loaded=RecoveryDraftStore(dir);assertEquals(full,loaded.editor(id)!!.body)
        loaded.upgradeLegacyCopies()
        assertTrue(JSONObject(primary(dir).readText()).has("_qlFormat"));assertTrue(mirror(dir).exists())
        assertEquals(full,RecoveryDraftStore(dir).editor(id)!!.body)
        assertTrue(loaded.putEditor(ChapterDraft(id,"新标题",full+"尾",3),1));assertEquals(full+"尾",RecoveryDraftStore(dir).editor(id)!!.body)
    }}
    @Test fun twoCorruptCopiesRemainAvailableForInvestigation(){repeat(3){
        val dir=root();val s=RecoveryDraftStore(dir);s.put("q",note(full));val a=primary(dir);val b=mirror(dir)
        a.writeText("{bad-primary");b.writeText("{bad-mirror")
        val loaded=RecoveryDraftStore(dir);assertNull(loaded.read("q"));assertNotNull(loaded.problem.value)
        assertTrue(loaded.put("other",note("独立草稿")));assertNotNull(loaded.problem.value)
        assertEquals("{bad-primary",a.readText());assertEquals("{bad-mirror",b.readText())
    }}
    @Test fun bothUnwritableCopiesRejectNewWriteAndKeepConfirmedOldText(){repeat(3){
        val dir=root();val s=RecoveryDraftStore(dir);assertTrue(s.put("q",note(full)));val shadow=File(dir,"mirror")
        Os.chmod(dir.path,365);Os.chmod(shadow.path,365)
        try{assertFalse(s.put("q",note("未能写入的新稿")));assertEquals(full,s.read("q")!!.getString("text"));assertNotNull(s.problem.value)}
        finally{Os.chmod(dir.path,493);Os.chmod(shadow.path,493)}
        assertEquals(full,RecoveryDraftStore(dir).read("q")!!.getString("text"))
    }}
    @Test fun failedDiscardDoesNotPretendDraftIsGone(){repeat(3){
        val dir=root();val s=RecoveryDraftStore(dir);assertTrue(s.put("q",note(full)));val shadow=File(dir,"mirror")
        Os.chmod(dir.path,365);Os.chmod(shadow.path,365)
        try{assertFalse(s.remove("q"));assertNotNull(s.read("q"));assertNotNull(s.problem.value)}
        finally{Os.chmod(dir.path,493);Os.chmod(shadow.path,493)}
        assertEquals(full,RecoveryDraftStore(dir).read("q")!!.getString("text"))
    }}
    private suspend fun orphan():JSONObject {
        c.database.spaceDao().insert(SpaceEntity(GuestSpace.ID,GuestSpace.KIND_GUEST,null))
        val id="missing-"+UUID.randomUUID();assertTrue(c.recoveryDrafts.putEditor(ChapterDraft(id,"找回标题",full,2),1))
        return c.recoveryDrafts.read("editor:"+id)!!
    }
    @Test fun orphanTextRestoresAsIndependentNewBookThreeTimes()=runBlocking{repeat(3){
        val j=orphan();val saved=c.recoveryRescue.restoreAsNewBook(j)
        assertNotEquals(j.getString("chapterId"),saved.id);assertEquals(full,saved.body);assertEquals("找回标题",saved.title)
        assertNull(c.recoveryDrafts.read(j.getString("key")))
    }}
    @Test fun failedRescueRollsBackNewBookAndKeepsDraftThreeTimes()=runBlocking{repeat(3){
        val j=orphan();val before=c.writingRepository.observeBooks(GuestSpace.ID).first()
        val failing=object:WritingRepository by c.writingRepository{override suspend fun saveDraft(draft:ChapterDraft):SaveResult=SaveResult.Failed(draft.chapterId,draft.editSeq,SaveFailure.DISK_FULL)}
        try{RecoveryRescueRepository(c.database,failing,c.recoveryDrafts).restoreAsNewBook(j);fail("must reject")}catch(_:IllegalStateException){}
        assertEquals(before,c.writingRepository.observeBooks(GuestSpace.ID).first());assertEquals(full,c.recoveryDrafts.read(j.getString("key"))!!.getString("text"))
        c.recoveryDrafts.remove(j.getString("key"))
    }}
    @Test fun laterDraftIsNotDeletedWhenAnOlderCopyIsRescuedThreeTimes()=runBlocking{repeat(3){
        val j=orphan();val key=j.getString("key");val original=j.getString("chapterId")
        val changing=object:WritingRepository by c.writingRepository{override suspend fun saveDraft(draft:ChapterDraft):SaveResult{
            val result=c.writingRepository.saveDraft(draft)
            c.recoveryDrafts.putEditor(ChapterDraft(original,"较新标题","恢复中收到的较新草稿",3),1)
            return result
        }}
        val saved=RecoveryRescueRepository(c.database,changing,c.recoveryDrafts).restoreAsNewBook(j)
        assertEquals(full,saved.body);assertEquals("恢复中收到的较新草稿",c.recoveryDrafts.read(key)!!.getString("text"))
        c.recoveryDrafts.remove(key)
    }}
    @Test fun automaticRecoveryPreservesPreviousConfirmedBodyInHistoryThreeTimes()=runBlocking{repeat(3){withContext(Dispatchers.Main){withTimeout(30000){
        val first=c.writingRepository.createBook(GuestSpace.ID,"恢复前保护检查","","")
        val old="先前确认的正文😀".repeat(1000)
        c.writingRepository.saveDraft(ChapterDraft(first.id,"原题",old,1,titleEdited=true))
        c.recoveryDrafts.putEditor(ChapterDraft(first.id,"原题",full,2),1,"原题")
        val vm=EditorViewModel(c,c.outlineRepository,first.id);val store=ViewModelStore();store.put("vm",vm)
        try{val session=vm.editorSession.filterNotNull().first();assertEquals(full,session.editorState.text.toString())
            assertTrue(c.historyRepository.observeRevisions(first.id).first().any{it.source==Revision.Source.BEFORE_RESTORE && it.body==old && it.title=="原题"})
        }finally{store.clear()}
    }}}}
}
