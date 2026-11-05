package com.qingling.writing
import android.content.Context
import android.util.AtomicFile
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.ViewModelStore
import com.qingling.writing.core.model.*
import com.qingling.writing.data.*
import com.qingling.writing.domain.repository.WritingRepository
import com.qingling.writing.feature.editor.EditorViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.io.File
import java.util.UUID
class JournalProtection045Test {
 private val app get()=ApplicationProvider.getApplicationContext<WritingApplication>()
 @Test fun interruptedAtomicWriteKeepsPreviousDraftThreeTimes(){repeat(3){
  val dir=File(app.cacheDir,"journal-test-"+UUID.randomUUID());val store=RecoveryDraftStore(dir)
  assertTrue(store.put("q",JSONObject().put("kind","quick").put("text","上一份完整草稿😀")))
  val atomic=AtomicFile(dir.listFiles()!!.single{it.name.endsWith(".json")});val out=atomic.startWrite();out.write("{partial".toByteArray());out.close()
  assertEquals("上一份完整草稿😀",RecoveryDraftStore(dir).read("q")!!.getString("text"))
 }}
 @Test fun recoveryNeverOverwritesNewerBodyThreeTimes()=runBlocking {repeat(3){withContext(Dispatchers.Main){
  val c=app.container;val first=c.writingRepository.createBook(GuestSpace.ID,"保护校验-版本冲突","","")
  c.writingRepository.saveDraft(ChapterDraft(first.id,"原题","原正文",1,titleEdited=true))
  c.recoveryDrafts.putEditor(ChapterDraft(first.id,"原题","未保存草稿",2),1)
  c.writingRepository.saveDraft(ChapterDraft(first.id,"原题","后续已存新正文",3))
  val model=EditorViewModel(c,c.outlineRepository,first.id);val store=ViewModelStore();store.put("vm",model)
  try {val s=model.editorSession.filterNotNull().first();assertEquals("后续已存新正文",s.editorState.text.toString());val j=c.recoveryDrafts.entries.value.first{it.optString("chapterId")==first.id && it.optString("kind")=="editorConflict"};assertEquals("未保存草稿",j.getString("text"));c.recoveryDrafts.putEditor(ChapterDraft(first.id,"原题","新一轮输入",4),3);assertEquals("未保存草稿",c.recoveryDrafts.read(j.getString("key"))!!.getString("text"));c.recoveryDrafts.remove(j.getString("key"))}
  finally{store.clear();c.recoveryDrafts.remove("editor:"+first.id)}
 }}}
 @Test fun failedDatabaseWriteKeepsDurableDraftThreeTimes()=runBlocking {repeat(3){withContext(Dispatchers.Main){
  val c=app.container;val first=c.writingRepository.createBook(GuestSpace.ID,"保护校验-保存失败","","")
  val failing=object:WritingRepository by c.writingRepository {override suspend fun saveDraft(d:ChapterDraft):SaveResult=SaveResult.Failed(d.chapterId,d.editSeq,SaveFailure.DISK_FULL)}
  val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
  val coordinator=SaveCoordinatorImpl(first.id,0,failing,c.historyRepository,scope,{java.time.Instant.now().toString()},debounceMs=600,recovery=c.recoveryDrafts)
  val d=ChapterDraft(first.id,"保护标题","数据库写入失败时必须保留的输入😀",1)
  try {coordinator.offer(d);assertTrue(coordinator.flush(first.id,1) is SaveResult.Failed);assertEquals(d.body,c.recoveryDrafts.read("editor:"+first.id)!!.getString("text"));assertEquals("",c.writingRepository.readChapter(first.id).body)}
  finally{scope.cancel();c.recoveryDrafts.remove("editor:"+first.id)}
 }}}
 @Test fun confirmedFlushDoesNotLeaveGhostRecoveryThreeTimes()=runBlocking {repeat(3){withContext(Dispatchers.Main){
  val c=app.container;val first=c.writingRepository.createBook(GuestSpace.ID,"保护校验-已保存","","")
  val model=EditorViewModel(c,c.outlineRepository,first.id);val store=ViewModelStore();store.put("vm",model)
  try {val s=model.editorSession.filterNotNull().first();s.editorState.edit{replace(0,length,"已保存的完整内容😀")};assertTrue(model.requestClose());assertEquals("已保存的完整内容😀",c.writingRepository.readChapter(first.id).body);assertNull(c.recoveryDrafts.read("editor:"+first.id))}
  finally{store.clear()}
 }}}
 @Test fun recoveryDoesNotUndoLaterDirectoryRenameThreeTimes()=runBlocking {repeat(3){withContext(Dispatchers.Main){
  val c=app.container;val first=c.writingRepository.createBook(GuestSpace.ID,"保护校验-目录改名","","")
  c.writingRepository.saveDraft(ChapterDraft(first.id,"原题","正文",1,titleEdited=true))
  c.recoveryDrafts.putEditor(ChapterDraft(first.id,"用户未存标题","正文",2,titleEdited=true),1,"原题")
  c.outlineRepository.renameChapter(first.id,"后来改名")
  val model=EditorViewModel(c,c.outlineRepository,first.id);val scope=ViewModelStore();scope.put("vm",model)
  try {val s=model.editorSession.filterNotNull().first();assertEquals("后来改名",s.titleState.text.toString());val j=c.recoveryDrafts.entries.value.first{it.optString("chapterId")==first.id && it.optString("kind")=="editorConflict"};assertEquals("用户未存标题",j.getString("title"));c.recoveryDrafts.remove(j.getString("key"))}
  finally{scope.clear()}
 }}}

}
