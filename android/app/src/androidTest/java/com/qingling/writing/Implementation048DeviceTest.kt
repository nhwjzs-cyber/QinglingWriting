package com.qingling.writing

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.design.QinglingWritingTheme
import com.qingling.writing.core.model.*
import com.qingling.writing.feature.planning.*
import com.qingling.writing.feature.export.ExportViewModel
import com.qingling.writing.feature.bookshelf.AddBookFlow
import com.qingling.writing.feature.editor.EditorScreen
import android.graphics.Rect
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class Implementation048DeviceTest {
    private val inst get()=InstrumentationRegistry.getInstrumentation()
    private val c get()=(inst.targetContext.applicationContext as WritingApplication).container
    private fun nodes(n:AccessibilityNodeInfo?):List<AccessibilityNodeInfo> = if(n==null)emptyList()else listOf(n)+(0 until n.childCount).flatMap{nodes(n.getChild(it))}
    private fun find(test:(AccessibilityNodeInfo)->Boolean):AccessibilityNodeInfo {
        repeat(100){nodes(inst.uiAutomation.rootInActiveWindow).firstOrNull(test)?.let{return it};SystemClock.sleep(150)}
        error("Missing UI: "+nodes(inst.uiAutomation.rootInActiveWindow).mapNotNull{it.text?:it.contentDescription}.joinToString(" | "))
    }
    private fun click(n:AccessibilityNodeInfo){var p=n;while(!p.isClickable&&p.parent!=null)p=p.parent;assertTrue(p.performAction(AccessibilityNodeInfo.ACTION_CLICK));SystemClock.sleep(300)}
    private fun prepareUi()=runBlocking{c.settingsStore.setReferenceDrawerLevel(0);c.settingsStore.dismissEditorGestureGuide()}
    private fun text(s:String)=find{it.text?.toString()==s}
    private fun set(n:AccessibilityNodeInfo,s:String){assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,s)}));SystemClock.sleep(100)}
    @Test fun readbackFailureKeepsDraftAndRetryUsesCommittedVersionThreeTimes()=runBlocking{repeat(3){withContext(Dispatchers.Main){
        val doc=c.writingRepository.createBook(GuestSpace.ID,"回读失败检查","","")
        var failRead=true
        val model=PlanningViewModel(c.planning,c.outlineRepository,doc.bookId,c.recoveryDrafts){id->if(failRead)null else c.planning.load(id)}
        val owner=ViewModelStore();owner.put("vm",model)
        try{model.openScope(PlanningKind.CHAPTER_OUTLINE,chapterId=doc.id);model.update(body="  自由细纲😀\n\n末尾空白  ")
            assertFalse(model.saveAndVerify());val d=model.draft.value!!;assertTrue(d.dirty);assertNotNull(model.error.value)
            assertEquals(d.entry.version,c.planning.load(d.entry.id)!!.version)
            assertEquals(d.entry.body,c.recoveryDrafts.read("planning:"+d.entry.id)!!.getString("text"))
            failRead=false;assertTrue(model.finishEdit());assertNull(model.draft.value);assertEquals(d.entry.body,c.planning.load(d.entry.id)!!.body)
        }finally{owner.clear()}
    }}}
    @Test fun inputDuringVerificationIsNotClosedOrLostThreeTimes()=runBlocking{repeat(3){withContext(Dispatchers.Main){
        val doc=c.writingRepository.createBook(GuestSpace.ID,"回读期间新输入","","");var mutate=true
        lateinit var model:PlanningViewModel
        model=PlanningViewModel(c.planning,c.outlineRepository,doc.bookId,c.recoveryDrafts){id->val stored=c.planning.load(id);if(mutate){mutate=false;model.update(body="回读期间到来的新文字😀")};stored}
        val owner=ViewModelStore();owner.put("vm",model)
        try{model.openScope(PlanningKind.CHAPTER_OUTLINE,chapterId=doc.id);model.update(body="第一份文字")
            assertFalse(model.finishEdit());assertEquals("回读期间到来的新文字😀",model.draft.value!!.entry.body)
            assertTrue(model.finishEdit());assertNull(model.draft.value)
        }finally{owner.clear()}
    }}}
    @Test fun untouchedNewOutlineCanCloseWithoutCreatingBlankRowThreeTimes()=runBlocking{repeat(3){withContext(Dispatchers.Main){
        val doc=c.writingRepository.createBook(GuestSpace.ID,"空细纲返回","","")
        val model=PlanningViewModel(c.planning,c.outlineRepository,doc.bookId,c.recoveryDrafts);val owner=ViewModelStore();owner.put("vm",model)
        try{model.openScope(PlanningKind.CHAPTER_OUTLINE,chapterId=doc.id);val id=model.draft.value!!.entry.id;assertTrue(model.finishEdit());assertNull(c.planning.load(id))}finally{owner.clear()}
    }}}
    @Test fun exportKeepsPreferencesButNeverReusesOtherEntrySelectionThreeTimes()=runBlocking{repeat(3){withContext(Dispatchers.Main){withTimeout(30000){
        val original=c.settingsStore.exportPreferences.first();val doc=c.writingRepository.createBook(GuestSpace.ID,"导出预设检查","","")
        val sibling=c.outlineRepository.createChapter(doc.bookId,doc.volumeId,"第二章")
        val prefs=ExportPreferences(ExportFormat.DOCX,false,true,false);c.settingsStore.setExportPreferences(prefs)
        val owner=ViewModelStore()
        try{val vm=ExportViewModel(c.outlineRepository,c.exportRepository,c.exportFiles,doc.bookId,SavedStateHandle(),c.settingsStore,ExportPreset(ExportScope.CHAPTER,doc.id));owner.put("vm",vm)
            val first=vm.ui.first{it.loaded};assertEquals(setOf(doc.id),first.selected);assertEquals(ExportFormat.DOCX,first.format);assertFalse(first.includeBook)
            vm.toggleChapter(sibling.id);assertEquals("自定义",vm.ui.value.scopeLabel)
            vm.setFormat(ExportFormat.TXT);vm.setOptions(true,false,true)
            c.settingsStore.exportPreferences.first{it==ExportPreferences(ExportFormat.TXT,true,false,true)}
            val fresh=ExportViewModel(c.outlineRepository,c.exportRepository,c.exportFiles,doc.bookId,SavedStateHandle(),c.settingsStore,ExportPreset(ExportScope.CHAPTER,sibling.id));owner.put("fresh",fresh)
            val again=fresh.ui.first{it.loaded};assertEquals(setOf(sibling.id),again.selected);assertEquals(ExportFormat.TXT,again.format);assertFalse(again.includeVolumes)
        }finally{owner.clear();c.settingsStore.setExportPreferences(original)}
    }}}}
    @Test fun bothScopedEntrancesAndPlanningListSaveStructuredOutlineThreeTimes(){
        prepareUi()
        val oracles=org.json.JSONArray()
        repeat(3){round->for(mode in 0..2){
            val doc=runBlocking{c.writingRepository.createBook(GuestSpace.ID,"细纲入口-$mode-$round","","")}
            val old="自由旧细纲😀  \n\n最后一段  \n"
            runBlocking{c.planning.save(c.planning.scoped(doc.bookId,PlanningKind.CHAPTER_OUTLINE,chapterId=doc.id).copy(body=old))}
            val activity=inst.startActivitySync(Intent(inst.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            var dismissed by mutableStateOf(false)
            try{inst.runOnMainSync{activity.setContent{QinglingWritingTheme{CompositionLocalProvider(LocalAppContainer provides c){
                if(dismissed)Text("已返回")
                else when(mode){0->ScopedPlanningSheet(doc.bookId,doc.id,null,{dismissed=true});1->PlanningDialog(doc.bookId,doc.id,{dismissed=true},initialChapterId=doc.id);else->PlanningDialog(doc.bookId,doc.id,{dismissed=true})}
            }}}}
                if(mode==2)click(find{it.text?.toString()=="章节细纲"&&nodes(it.parent).any{n->n.text?.toString()?.contains("自由旧细纲")==true}})
                val goal=find{it.isEditable && it.text?.toString()==old}
                assertEquals(old,runBlocking{c.planning.scoped(doc.bookId,PlanningKind.CHAPTER_OUTLINE,chapterId=doc.id)}.body)
                set(goal,"新的目标-$mode-$round😀");click(text("＋ 添加分场"))
                set(find{it.isEditable&&it.text?.toString().orEmpty().isEmpty()},"追查线索😀")
                click(text("待写"));click(text("保存并返回"));SystemClock.sleep(350)
                val saved=runBlocking{c.planning.scoped(doc.bookId,PlanningKind.CHAPTER_OUTLINE,chapterId=doc.id)}
                val parsed=ChapterReference.parse(saved.body);assertEquals("新的目标-$mode-$round😀",parsed.goal);assertEquals("追查线索😀",parsed.nextScene)
                assertEquals(SceneStatus.CURRENT,parsed.scenes.single().status)
                oracles.put(org.json.JSONObject().put("id",saved.id).put("title",saved.title).put("body",saved.body).put("mode",mode).put("round",round))
                java.io.File(inst.targetContext.filesDir,"implementation048-outline-oracles.json").writeText(oracles.toString())
            }finally{inst.runOnMainSync{activity.finish()}}
        }}
    }
    @Test fun verifySavedOutlinesAfterFreshProcess(){
        org.junit.Assume.assumeTrue("Explicit fresh-process verification only",InstrumentationRegistry.getArguments().getString("verify048")=="true")
        val records=org.json.JSONArray(java.io.File(inst.targetContext.filesDir,"implementation048-outline-oracles.json").readText())
        assertEquals(9,records.length())
        repeat(records.length()){index->val j=records.getJSONObject(index);val actual=runBlocking{c.planning.load(j.getString("id"))}!!
            assertEquals(j.getString("title"),actual.title);assertEquals(j.getString("body"),actual.body)
        }
    }
    @Test fun directNewBookFormUsesLastSuccessfulDefaultsThreeTimes(){
        prepareUi()
        val author=runBlocking{c.settingsStore.lastAuthor.first()};val cover=runBlocking{c.settingsStore.lastCoverStyle.first()}
        try{repeat(3){round->
            val style=CoverStyle(3,2);runBlocking{c.settingsStore.setLastBookDefaults("默认作者",style)}
            val activity=inst.startActivitySync(Intent(inst.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            var created:String?=null
            try{inst.runOnMainSync{activity.setContent{QinglingWritingTheme{CompositionLocalProvider(LocalAppContainer provides c){AddBookFlow({}, {created=it})}}}}
                text("新建书");text("默认作者")
                set(find{it.isEditable&&it.text?.toString().orEmpty().isEmpty()},"直接创建-$round")
                click(text("创建并打开第一章"));repeat(60){if(created==null)SystemClock.sleep(100)}
                val id=checkNotNull(created);assertEquals(style,runBlocking{c.settingsStore.coverStyles.first()}[id]);assertEquals("默认作者",runBlocking{c.database.bookDao().byId(id)}!!.author)
            }finally{inst.runOnMainSync{activity.finish()}}
        }}finally{runBlocking{c.settingsStore.setLastBookDefaults(author,cover)}}
    }
    @Test fun savingFromReferenceShowsOneTimePeekWithoutChangingPreferenceThreeTimes(){repeat(3){round->
        val oldLevel=runBlocking{c.settingsStore.referenceDrawerLevel.first()}
        val doc=runBlocking{c.settingsStore.setReferenceDrawerEnabled(true);c.settingsStore.setReferenceDrawerLevel(0);c.writingRepository.createBook(GuestSpace.ID,"细纲保存反馈-$round","","")}
        val activity=inst.startActivitySync(Intent(inst.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try{inst.runOnMainSync{activity.setContent{QinglingWritingTheme{CompositionLocalApp(doc.id)}}}
            click(text("细纲 ⌄"));runBlocking{c.settingsStore.referenceDrawerLevel.first{it==1}};click(text("本章暂无细纲 · 写一笔"))
            set(find{it.isEditable&&it.text?.toString().orEmpty().isEmpty()},"保存后能看见-$round")
            click(text("保存并返回"));text("本章细纲");find{it.text?.toString()?.contains("保存后能看见-$round")==true}
            assertEquals(1,runBlocking{c.settingsStore.referenceDrawerLevel.first()})
        }finally{inst.runOnMainSync{activity.finish()};runBlocking{c.settingsStore.setReferenceDrawerLevel(oldLevel)}}
    }}
    @Composable private fun CompositionLocalApp(id:String){CompositionLocalProvider(LocalAppContainer provides c){EditorScreen(id,{},{},{},{},{})}}
    @Test fun titleFocusKeepsSameWritingBarAndBodyActionThreeTimes(){repeat(3){
        prepareUi()
        val doc=runBlocking{val d=c.writingRepository.createBook(GuestSpace.ID,"标题底栏验收","","");c.writingRepository.saveDraft(ChapterDraft(d.id,"验收标题","正文",1,titleEdited=true));d}
        val activity=inst.startActivitySync(Intent(inst.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try{inst.runOnMainSync{activity.setContent{QinglingWritingTheme{CompositionLocalApp(doc.id)}}}
            click(find{it.isEditable&&it.text?.toString()=="验收标题"});text("写正文 ›")
            val bar=find{it.contentDescription?.toString()=="写作配件条"};val before=Rect().also{bar.getBoundsInScreen(it)}
            assertFalse(nodes(inst.uiAutomation.rootInActiveWindow).any{it.text?.toString()=="正在编辑标题"})
            click(text("写正文 ›"));val after=Rect().also{find{it.contentDescription?.toString()=="写作配件条"}.getBoundsInScreen(it)}
            assertEquals(before.height(),after.height());assertEquals(before.top.toFloat(),after.top.toFloat(),3f)
        }finally{inst.runOnMainSync{activity.finish()}}
    }}
    @Test fun proofReviewRemovesOnlyRequestedRepeatAndCanUndoThreeTimes(){repeat(3){
        val body="前".repeat(40)+"今天今天出发。"+"后".repeat(100)
        val doc=runBlocking{val d=c.writingRepository.createBook(GuestSpace.ID,"校对小条验收","","");c.writingRepository.saveDraft(ChapterDraft(d.id,"校对标题",body,1,titleEdited=true));d}
        val activity=inst.startActivitySync(Intent(inst.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try{inst.runOnMainSync{activity.setContent{QinglingWritingTheme{CompositionLocalApp(doc.id)}}}
            click(find{it.contentDescription?.toString()=="工具"});click(text("本地校对"))
            var parent=text("相邻重复词：今天");var locate:AccessibilityNodeInfo?=null
            repeat(6){if(locate==null){locate=nodes(parent).firstOrNull{it.text?.toString()=="定位"};parent=parent.parent?:parent}}
            click(checkNotNull(locate));click(text("删去重复"))
            find{it.isEditable&&it.text?.toString()==body.replace("今天今天","今天")}
            click(find{it.contentDescription?.toString()=="撤销"&&it.isEnabled})
            find{it.isEditable&&it.text?.toString()==body}
            assertTrue(runBlocking{c.historyRepository.observeRevisions(doc.id).first()}.any{it.body==body})
        }finally{inst.runOnMainSync{activity.finish()}}
    }}
}
