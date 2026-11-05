package com.qingling.writing
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import com.qingling.writing.app.*
import com.qingling.writing.core.model.*
import com.qingling.writing.core.database.entities.SpaceEntity
import com.qingling.writing.feature.editor.*
import com.qingling.writing.feature.planning.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.io.File
class LossProbeActivity:ComponentActivity() {
 private val c get()=(application as WritingApplication).container
 private val prefs get()=getSharedPreferences("loss-probe",0)
 private fun result(j:JSONObject){File(filesDir,"loss-result.json").writeText(j.toString())}
 private fun editor(id:String):EditorViewModel=ViewModelProvider(this,object:ViewModelProvider.Factory {override fun <T:ViewModel> create(cls:Class<T>):T { @Suppress("UNCHECKED_CAST") return EditorViewModel(c,c.outlineRepository,id) as T }} )["probe-"+id,EditorViewModel::class.java]
 override fun onNewIntent(next:android.content.Intent){super.onNewIntent(next);setIntent(next);dispatch()}
 override fun onCreate(state:Bundle?){super.onCreate(state);dispatch()}
 private fun dispatch(){lifecycleScope.launch {
  try {when(intent.getStringExtra("mode")){"verify"->verify();"recover-edit"->recoverEdit();else->runCase()}}catch(e:Throwable){result(JSONObject().put("error",e.stackTraceToString()));finish()}
 }}
 private suspend fun runCase(){
  val case=intent.getStringExtra("case")!!;val run=intent.getStringExtra("run")!!
  c.database.spaceDao().insert(SpaceEntity(GuestSpace.ID,GuestSpace.KIND_GUEST,null))
  val first=c.writingRepository.createBook(GuestSpace.ID,"保护测试-"+case+"-"+run,"","")
  val base="BASE-"+case+"-"+run;val marker="MARK-"+case+"-"+run+"-中文😀-END"
  c.writingRepository.saveDraft(ChapterDraft(first.id,"BASE_TITLE",base,first.editSeq+1,titleEdited=true))
  val body=when(case){"long"->("中文😀，长章。\n".repeat(12000))+marker;"burst"->base+(1..200).joinToString(""){"[$it]中😀"};"recoverytwice"->base+"\n"+marker+"\n二次恢复后的续写😀";else->base+"\n"+marker};val title=if(case=="title" || case=="burst")marker else "BASE_TITLE"
  val meta=JSONObject().put("case",case).put("run",run).put("chapter",first.id).put("book",first.bookId).put("body",if(case=="title")base else body).put("title",title).put("marker",marker)
  if(case=="recoverytwice"){
   prefs.edit().putString("last",meta.toString()).commit()
   check(c.recoveryDrafts.putEditor(ChapterDraft(first.id,"BASE_TITLE",base+"\n"+marker,first.editSeq+2),first.editSeq+1,"BASE_TITLE"))
   result(JSONObject().put("armed",true).put("case",case).put("run",run));Process.killProcess(Process.myPid());return
  }else if(case=="planning" || case=="outline"){
   val kind=if(case=="outline")PlanningKind.CHAPTER_OUTLINE else PlanningKind.CHARACTER
   val entry=c.planning.save(c.planning.scoped(first.bookId,kind,chapterId=first.id.takeIf{case=="outline"}).copy(body=base))
   meta.put("entry",entry.id);prefs.edit().putString("last",meta.toString()).commit()
   val model=PlanningViewModel(c.planning,c.outlineRepository,first.bookId,c.recoveryDrafts);viewModelStore.put("planning",model)
   model.edit(entry);model.update(body=marker)
   setContent{MaterialTheme{Text(marker)}}
  }else if(case=="note"){
   prefs.edit().putString("last",meta.toString()).commit()
   setContent{MaterialTheme{CompositionLocalProvider(LocalAppContainer provides c){QuickNoteSheet({},bookId=first.bookId,chapterId=first.id,sourceTitle="保护测试",initialText=marker,sourceQuote=base,foreshadow=true)}}}
  }else{
   prefs.edit().putString("last",meta.toString()).commit()
   val model=editor(first.id);val session=model.editorSession.filterNotNull().first()
   setContent{MaterialTheme{Column{BasicTextField(state=session.titleState);BasicTextField(state=session.editorState)}}}
   delay(200)
   if(case=="burst"){
    for(i in 1..200){session.editorState.edit{append("[$i]中😀")};session.titleState.edit{replace(0,length,if(i==200)marker else "连续标题$i")};delay(20)}
   }else if(case=="title")session.titleState.edit{replace(0,length,marker)} else session.editorState.edit{replace(0,length,body)}
   if(case=="background")session.checkpoint()
   if(case=="switch") {val next=c.outlineRepository.createChapter(first.bookId,first.volumeId,"第二章");check(model.switchToChapter(next.id))}
  }
  delay(if(case=="saved")1500 else 150)
  result(JSONObject().put("armed",true).put("case",case).put("run",run))
  Process.killProcess(Process.myPid())
 }
 private suspend fun recoverEdit(){
  val meta=JSONObject(prefs.getString("last",null)!!);check(meta.getString("case")=="recoverytwice")
  val model=editor(meta.getString("chapter"));val session=model.editorSession.filterNotNull().first()
  val before=meta.getString("body").removeSuffix("\n二次恢复后的续写😀")
  check(session.editorState.text.toString()==before){"First recovery lost text"}
  setContent{MaterialTheme{Column{BasicTextField(state=session.titleState);BasicTextField(state=session.editorState)}}}
  delay(200)
  session.editorState.edit{append("\n二次恢复后的续写😀")};delay(150)
  val expected=meta.getString("body")
  result(JSONObject().put("armed",true).put("stage","second-kill").put("uiMounted",true).put("bufferMatches",session.editorState.text.toString()==expected).put("journalMatches",c.recoveryDrafts.editor(meta.getString("chapter"))?.body==expected));Process.killProcess(Process.myPid())
 }
 private suspend fun verify(){
  val meta=JSONObject(prefs.getString("last",null)!!);val case=meta.getString("case")
  var actual="";var title=""
  if(case=="note"){
   actual=c.planning.inbox().first().map{QuickNote.decode(it.body).text}.firstOrNull{it==meta.getString("marker")}.orEmpty()
   if(actual.isEmpty())actual=c.recoveryDrafts.entries.value.firstOrNull{it.optString("kind")=="quick" && it.optString("text")==meta.getString("marker")}?.optString("text").orEmpty()
  }else if(case=="planning" || case=="outline"){
   val entry=c.planning.load(meta.getString("entry"))!!
   val model=PlanningViewModel(c.planning,c.outlineRepository,meta.getString("book"),c.recoveryDrafts);viewModelStore.put("verify-plan",model);model.edit(entry)
   actual=model.draft.value!!.entry.body
  }else{
   val model=editor(meta.getString("chapter"));val session=model.editorSession.filterNotNull().first()
   actual=session.editorState.text.toString();title=session.titleState.text.toString()
  }
  val expected=if(case in listOf("note","planning","outline"))meta.getString("marker") else meta.getString("body")
  val pass=actual==expected && (case in listOf("note","planning","outline") || title==meta.getString("title"))
  result(meta.put("actual",actual).put("actualTitle",title).put("pass",pass));finish()
 }
}
