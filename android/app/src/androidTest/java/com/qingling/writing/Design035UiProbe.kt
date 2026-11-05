package com.qingling.writing

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.InputDevice
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.design.*
import com.qingling.writing.core.model.*
import com.qingling.writing.core.database.entities.SpaceEntity
import com.qingling.writing.feature.editor.*
import com.qingling.writing.feature.bookshelf.HomeScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Explicit opt-in: creates named test books on the emulator, without clearing user data. */
class Design035UiProbe {
    @Test fun selectCaptureBrowseCompleteAndAddBook() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("fixture")=="design035")
        val inst=InstrumentationRegistry.getInstrumentation();val context=inst.targetContext
        val container=(context.applicationContext as WritingApplication).container
        val body="夜里两点，灯塔的光扫过海面。\n\n老人说，这座塔的灯芯里，藏着一封没有寄出的信。\n\n守塔人听完，只是把灯擦得更亮了一些。"
        val first=runBlocking {
            container.database.spaceDao().insert(SpaceEntity(GuestSpace.ID,GuestSpace.KIND_GUEST,null))
            val doc=container.writingRepository.createBook(GuestSpace.ID,"界面验收·夜航笔记","林晚","")
            container.writingRepository.saveDraft(ChapterDraft(doc.id,"第一章 灯塔",body,1))
            container.outlineRepository.createChapter(doc.bookId,doc.volumeId,"第二章 远方")
            doc
        }
        val other=runBlocking {
            val doc=container.writingRepository.createBook(GuestSpace.ID,"界面验收·晚风来信","","")
            container.outlineRepository.renameChapter(doc.id,"第一章 旧车站");doc
        }
        val activity=inst.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val home=mutableStateOf(false);val dark=mutableStateOf(true)
        fun nodes(n:AccessibilityNodeInfo?):List<AccessibilityNodeInfo> = if(n==null) emptyList() else listOf(n)+(0 until n.childCount).flatMap{nodes(n.getChild(it))}
        fun all()=nodes(inst.uiAutomation.rootInActiveWindow)
        fun waitFor(test:()->Boolean){repeat(100){if(test())return;SystemClock.sleep(100)};error("UI condition timed out: "+all().mapNotNull{it.text?:it.contentDescription}.joinToString())}
        fun waitNode(test:(AccessibilityNodeInfo)->Boolean):AccessibilityNodeInfo {waitFor{all().any(test)};return all().first(test)}
        fun click(n:AccessibilityNodeInfo){var p=n;while(!p.isClickable&&p.parent!=null)p=p.parent;assertTrue(p.performAction(AccessibilityNodeInfo.ACTION_CLICK));SystemClock.sleep(200)}
        fun text(t:String)=waitNode{it.text?.toString()==t}
        fun desc(t:String)=waitNode{it.contentDescription?.toString()==t}
        fun shot(name:String){SystemClock.sleep(350);File(context.getExternalFilesDir("design035"),name+".png").outputStream().use{inst.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)}}
        fun model()=ViewModelProvider(activity)["editor",EditorViewModel::class.java]
        inst.runOnMainSync {activity.setContent {QinglingWritingTheme(darkTheme=dark.value) {CompositionLocalProvider(LocalAppContainer provides container) {
            if(home.value) HomeScreen({}, {}, {}) else EditorScreen(first.id,{}, {}, {}, {}, {})
        }}}}
        try {
            desc("选区模式");shot("01-editor-dark")
            click(desc("选区模式"));desc("退出选区模式")
            val bounds=Rect().also {waitNode{it.text?.toString()==body}.getBoundsInScreen(it)}
            val down=SystemClock.uptimeMillis()
            listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP).forEachIndexed{i,a ->
                val event=MotionEvent.obtain(down,down+i*70L,a,bounds.left+40f,bounds.top+30f,0).apply{source=InputDevice.SOURCE_TOUCHSCREEN}
                inst.uiAutomation.injectInputEvent(event,true);event.recycle()
            }
            waitFor {var selected=false;inst.runOnMainSync {selected=model().session?.editorState?.selection?.collapsed==false};selected}
            shot("02-selection-dark")
            click(text("存便签"));text("灵感速记");shot("03-note-sheet")
            click(text("保存"))
            waitFor{runBlocking{container.planning.inbox().first()}.any{QuickNote.decode(it.body).chapterId==first.id}}
            click(text("目录"));text("书库");shot("04-outline-dark")
            val menus=all().filter{it.contentDescription?.toString()=="更多操作"}.map{Rect().also(it::getBoundsInScreen)}
            assertTrue(menus.size>=3)
            assertTrue("Volume/chapter menus align",menus.maxOf{it.right}-menus.minOf{it.right}<=3)
            val grips=all().filter{it.contentDescription?.toString()?.startsWith("拖动")==true}.map{Rect().also(it::getBoundsInScreen)}
            assertTrue(grips.size>=3);assertTrue("Reorder grips align",grips.maxOf{it.right}-grips.minOf{it.right}<=3)
            click(text("书库"));shot("05-library-dark")
            click(text("界面验收·晚风来信"));text("第一章 旧车站")
            inst.runOnMainSync {assertEquals(first.id,model().session?.chapterId)}
            click(text("第一章 旧车站"));waitFor{var same=false;inst.runOnMainSync {same=model().session?.chapterId==other.id};same}
            assertEquals(body,runBlocking{container.writingRepository.readChapter(first.id)}.body)
            click(desc("工具"));click(text("完成本章"));waitNode{it.text?.toString()?.startsWith("本章完成")==true};shot("06-completion-strip")
            assertFalse(all().any{it.text?.toString()=="✓ 本章已完成"})
            inst.runOnMainSync {dark.value=false};SystemClock.sleep(600);shot("07-editor-light")
            inst.runOnMainSync {dark.value=true;home.value=true};text("让灵感落在纸上");shot("08-home-dark");repeat(4){if(all().none{it.text?.toString()=="＋ 创建一本书"}){all().firstOrNull{it.isScrollable}?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);SystemClock.sleep(250)}};click(text("＋ 创建一本书"));text("空白新书");shot("08-add-entry")
            click(text("空白新书"));text("创建并开始写作")
            val field=waitNode{it.isEditable};val args=Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"长夜里的来信")}
            assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args));shot("09-new-book-form")
            inst.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        } finally {inst.runOnMainSync{activity.finish()}}
    }
}
