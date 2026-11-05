package com.qingling.writing

import android.os.Bundle
import android.os.Process
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.then
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import com.qingling.writing.core.model.*
import com.qingling.writing.feature.editor.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.io.File

/** Debug only. Uses the focused Compose view's real InputConnection.commitText. */
class ExtremeProbeActivity:ComponentActivity(){
 private val c get()=(application as WritingApplication).container
 private val prefs get()=getSharedPreferences("extreme-probe",0)
 private fun result(j:JSONObject){File(filesDir,"extreme-result.json").writeText(j.toString())}
 private fun model(id:String)=ViewModelProvider(this,object:ViewModelProvider.Factory{override fun<T:ViewModel>create(cls:Class<T>):T{@Suppress("UNCHECKED_CAST")return EditorViewModel(c,c.outlineRepository,id)as T}})["extreme-"+id,EditorViewModel::class.java]
 private fun payload(n:Int)=(if(n%2==0)"春夏秋冬山川湖海日月" else "天地玄黄宇宙洪荒日月").repeat(10000)
 override fun onCreate(b:Bundle?){super.onCreate(b);lifecycleScope.launch{try{if(intent.getStringExtra("mode")=="verify")verify()else runCase()}catch(e:Throwable){result(JSONObject().put("error",e.stackTraceToString()));finish()}}}
 private fun composeView(v:View):View?{if(v.javaClass.name=="androidx.compose.ui.platform.AndroidComposeView")return v;if(v is ViewGroup)for(i in 0 until v.childCount)composeView(v.getChildAt(i))?.let{return it};return null}
 private suspend fun runCase(){
  val case=intent.getStringExtra("case")!!;val run=intent.getStringExtra("run")!!
  val first=c.writingRepository.createBook(GuestSpace.ID,"极端输入-$case-$run","","")
  check(c.writingRepository.saveDraft(ChapterDraft(first.id,"极端测试标题","原有已保存内容不可丢失",1,titleEdited=true)) is SaveResult.Committed)
  val sync=withContext(Dispatchers.IO){c.database.withTransaction{c.database.openHelper.writableDatabase.query("PRAGMA synchronous").use{it.moveToFirst();it.getInt(0)}}}
  val expected=if(case=="revert")"原有已保存内容不可丢失" else payload(if(case=="rapid")5 else 0)
  val meta=JSONObject().put("case",case).put("run",run).put("chapter",first.id).put("expected",expected).put("title","极端测试标题").put("writerSynchronous",sync)
  check(prefs.edit().putString("last",meta.toString()).commit())
  val session=withTimeout(15000){model(first.id).editorSession.filterNotNull().first()}
  val focus=FocusRequester()
  setContent{BasicTextField(state=session.editorState,inputTransformation=ParagraphIndentation.onInput.then(session.bodyInputProtection),modifier=Modifier.focusRequester(focus));LaunchedEffect(Unit){focus.requestFocus()}}
  delay(600)
  val connection=checkNotNull(composeView(window.decorView)?.onCreateInputConnection(EditorInfo())){"No focused InputConnection"}
  val start=android.os.SystemClock.elapsedRealtime()
  for(i in 0 until if(case=="rapid")6 else 1){
   check(connection.setSelection(0,session.editorState.text.length));check(connection.commitText(payload(if(case=="rapid")i else 0),1))
   check(session.editorState.text.toString()==payload(if(case=="rapid")i else 0)){"Input was not accepted"}
  }
  if(case=="revert"){check(connection.setSelection(0,session.editorState.text.length));check(connection.commitText(expected,1));check(session.editorState.text.toString()==expected)}
  val accepted=android.os.SystemClock.elapsedRealtime()
  if(case=="kill50" || case=="systemstop")delay(50)
  val journal=c.recoveryDrafts.editor(first.id)?.body
  result(JSONObject().put("armed",true).put("case",case).put("run",run).put("chapter",first.id).put("inputAccepted",session.editorState.text.toString()==expected).put("journalMatches",journal==expected).put("inputDurationMs",accepted-start).put("elapsedSinceAcceptedMs",android.os.SystemClock.elapsedRealtime()-accepted).put("writerSynchronous",sync))
  when(case){
   "java"->Thread({throw IllegalStateException("EXTREME_PROBE_UNCAUGHT_$run")},"ExtremeProbeCrash").start()
   "systemstop"->Unit
   else->Process.killProcess(Process.myPid())
  }
 }
 private suspend fun verify(){
  val meta=JSONObject(prefs.getString("last",null)!!)
  val vm=model(meta.getString("chapter"))
  val state=withTimeout(30000){vm.uiState.first{it!=EditorUiState.Loading}}
  if(state !is EditorUiState.Ready){val pending=c.recoveryDrafts.editor(meta.getString("chapter"));result(meta.put("actual","").put("actualTitle","").put("pass",false).put("loadState",state.toString()).put("protectedText",pending?.body.orEmpty()));finish();return}
  val session=withTimeout(15000){vm.editorSession.filterNotNull().first()}
  val actual=session.editorState.text.toString();val stored=c.writingRepository.readChapter(meta.getString("chapter"))
  result(meta.put("actual",actual).put("actualTitle",stored.title).put("pass",actual==meta.getString("expected")&&stored.title==meta.getString("title")));finish()
 }
}
