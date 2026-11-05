package com.qingling.writing
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.test.platform.app.InstrumentationRegistry
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.database.entities.SpaceEntity
import com.qingling.writing.core.design.*
import com.qingling.writing.core.model.*
import com.qingling.writing.feature.bookshelf.*
import com.qingling.writing.feature.editor.EditorScreen
import com.qingling.writing.feature.planning.InspirationPage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class Design036UiProbe {
    private val inst get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=inst.targetContext
    private val container get()=(context.applicationContext as WritingApplication).container
    private fun nodes(n:AccessibilityNodeInfo?):List<AccessibilityNodeInfo> = if(n==null)emptyList()else listOf(n)+(0 until n.childCount).flatMap{nodes(n.getChild(it))}
    private fun all()=nodes(inst.uiAutomation.rootInActiveWindow)
    private fun waitFor(test:()->Boolean){repeat(120){if(test())return;SystemClock.sleep(100)};error("Timed out: "+all().mapNotNull{it.text?:it.contentDescription}.joinToString())}
    private fun node(test:(AccessibilityNodeInfo)->Boolean):AccessibilityNodeInfo {waitFor{all().any(test)};return all().first(test)}
    private fun text(t:String)=node{it.text?.toString()==t}
    private fun desc(t:String)=node{it.contentDescription?.toString()==t}
    private fun click(n:AccessibilityNodeInfo){var p=n;while(!p.isClickable&&p.parent!=null)p=p.parent;assertTrue(p.performAction(AccessibilityNodeInfo.ACTION_CLICK));SystemClock.sleep(450)}
    private fun set(n:AccessibilityNodeInfo,value:String){assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value)}))}
    private fun scrollText(t:String):AccessibilityNodeInfo {SystemClock.sleep(400);all().firstOrNull{it.text?.toString()==t}?.let{return it};for(direction in listOf(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)){repeat(8){all().firstOrNull{it.text?.toString()==t}?.let{return it};all().lastOrNull{it.isScrollable}?.performAction(direction);SystemClock.sleep(180)}};return text(t)}
    private fun shot(name:String){SystemClock.sleep(400);File(context.getExternalFilesDir("design036"),name+".png").outputStream().use{inst.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)}}
    private fun prepare(){assumeTrue(InstrumentationRegistry.getArguments().getString("fixture")=="design036");runBlocking{container.database.spaceDao().insert(SpaceEntity(GuestSpace.ID,GuestSpace.KIND_GUEST,null));container.settingsStore.setAppLockEnabled(false)}}
    private fun launch()=inst.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity

    @Test fun emptyBookToolbarSheetsAndScopedInspiration() {
        prepare()
        val labelA="界面甲-"+SystemClock.uptimeMillis();val labelB="界面乙-"+SystemClock.uptimeMillis()
        val book=runBlocking{container.writingRepository.createEmptyBook(GuestSpace.ID,"增补界面·甲书","","")}
        val other=runBlocking{container.writingRepository.createBook(GuestSpace.ID,"增补界面·乙书","","")}
        runBlocking{
            val a=container.planning.capture(labelA,bookId=book,foreshadow=true)
            container.planning.updateNote(a,QuickNote.decode(a.body).copy(capturedAt="1000-01-01T00:00:00Z"))
            val b=container.planning.capture(labelB,bookId=other.bookId,foreshadow=true)
            container.planning.updateNote(b,QuickNote.decode(b.body).copy(capturedAt="1100-01-01T00:00:00Z"))
            container.planning.capture("通用箱独有灵感",bookId="")
        }
        val activity=launch();val mode=mutableIntStateOf(0)
        inst.runOnMainSync{activity.setContent{QinglingWritingTheme{CompositionLocalProvider(LocalAppContainer provides container){
            if(mode.intValue==0)EditorScreen("book:"+book,{mode.intValue=1},{},{},{},{},onOpenInspiration={mode.intValue=1})
            else HomeScreen({}, {}, {},initialPage=1)
        }}}}
        try {
            text("还没有章节");shot("01-empty-book");click(text("写第一章"));desc("选区模式")
            val chapter=runBlocking{container.writingRepository.resolveBookActiveChapter(book)}!!
            assertEquals(1,runBlocking{container.outlineRepository.observeOutline(book).first()}.sumOf{it.chapters.size})
            val bar=Rect().also{desc("编辑导航栏").getBoundsInScreen(it)}
            assertEquals(48f*context.resources.displayMetrics.density,bar.height().toFloat(),3f)
            val body="夜里两点，灯塔的的光扫过海面。（未闭合"
            set(node{it.isEditable},body);click(node{it.isEditable})
            val accessories=Rect().also{desc("写作配件条").getBoundsInScreen(it)}
            assertEquals(38f*context.resources.displayMetrics.density,accessories.height().toFloat(),3f)
            assertFalse(all().any{it.text?.toString()=="目录"});shot("02-writing-accessories")
            inst.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);desc("编辑导航栏")
            click(desc("工具"));click(scrollText("本地校对"));desc("底部弹层：本地校对");node{it.text?.toString()?.startsWith("重复字词")==true};shot("03-proof-sheet")
            click(node{it.text?.toString()=="忽略"});click(desc("关闭本地校对"))
            click(desc("工具"));click(scrollText("一键排版"));node{it.contentDescription?.toString()?.startsWith("底部弹层：")==true&&it.contentDescription.toString().contains("排版")};shot("04-format-sheet");click(node{it.contentDescription?.toString()?.startsWith("关闭")==true})
            click(desc("工具"));click(scrollText("历史版本"));desc("底部弹层：历史版本");click(text("保存当前版本"));click(text("预览"));desc("底部弹层：历史版本预览");shot("05-history-preview");click(desc("关闭历史版本预览"));click(desc("关闭历史版本"))
            click(desc("工具"));click(scrollText("便签与伏笔"));text("本书灵感");text(labelA)
            assertFalse(all().any{it.text?.toString()==labelB||it.text?.toString()=="通用箱独有灵感"});shot("06-book-inspiration")
            click(text("全部书的灵感在主页灵感页 ›"));text("灵感");shot("07-inspiration-drawers")
            click(node{it.text?.toString()?.startsWith("欠账 · 全书")==true});val ar=Rect().also{text(labelA).getBoundsInScreen(it)};val br=Rect().also{text(labelB).getBoundsInScreen(it)}
            assertTrue(ar.top<br.top);shot("08-inspiration-debt")
            click(desc("主页"));text("让灵感落在纸上");shot("09-home-pager")
            click(desc("灵感页"));text("灵感")
            assertEquals(body,runBlocking{container.writingRepository.readChapter(chapter)}.body)
        }finally{inst.runOnMainSync{activity.finish()}}
    }
    @Test fun compactBookFormAlwaysSeedsAndDeletionMovesNotes() {
        prepare();runBlocking{container.settingsStore.setLastAuthor("上次作者")}
        val activity=launch();val mode=mutableIntStateOf(0);val created=mutableStateOf("");val target=mutableStateOf<BookSummary?>(null)
        inst.runOnMainSync{activity.setContent{QinglingWritingTheme{CompositionLocalProvider(LocalAppContainer provides container){
            when(mode.intValue){
                0->HomeScreen({}, {created.value=it;mode.intValue=1}, {})
                1->EditorScreen("book:"+created.value,{}, {}, {}, {}, {})
                2->target.value?.let{DeleteBookDialog(it,{mode.intValue=1},{mode.intValue=3})}
                else->InspirationPage({_,_,_->})
            }
        }}}}
        try {
            click(scrollText("＋ 创建一本书"));click(text("空白新书"));desc("底部弹层：新建书")
            assertFalse(all().any{it.text?.toString()=="只建空书"});set(node{it.isEditable},"增补表单验收")
            inst.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            val preview=Rect().also{desc("新书封面预览").getBoundsInScreen(it)}
            assertEquals(120f*context.resources.displayMetrics.density,preview.height().toFloat(),3f);shot("10-new-book-sheet")
            click(text("创建并打开第一章"));desc("选区模式")
            val book=runBlocking{container.writingRepository.observeBooks(GuestSpace.ID).first().first{it.id==created.value}}
            assertEquals("上次作者",book.author);assertEquals(1,book.chapterCount)
            val note=runBlocking{container.planning.capture("删书前要保留这条灵感😀",bookId=book.id)}
            inst.runOnMainSync{target.value=book;mode.intValue=2}
            text("迁移灵感并移入回收站");shot("11-delete-migration")
            click(text("迁移灵感并移入回收站"));waitFor{runBlocking{container.planning.load(note.id)}?.bookId==""}
            assertEquals(note.body,runBlocking{container.planning.load(note.id)}!!.body)
            assertNotNull(runBlocking{container.database.bookDao().byId(book.id)}!!.deletedAt)
        }finally{inst.runOnMainSync{activity.finish()}}
    }
}
