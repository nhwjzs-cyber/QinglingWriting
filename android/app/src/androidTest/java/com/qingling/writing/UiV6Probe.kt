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
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.database.entities.*
import com.qingling.writing.core.design.*
import com.qingling.writing.core.model.GuestSpace
import com.qingling.writing.feature.bookshelf.HomeScreen
import com.qingling.writing.feature.bookshelf.WritingDashboard
import com.qingling.writing.feature.bookshelf.DashboardViewModel
import com.qingling.writing.feature.outline.OutlineContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Explicit opt-in: creates disposable UI fixtures, never run on a user's device. */
class UiV6Probe {
    @Test fun dashboardGoalAndContainedMenu() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("fixture")=="ui-v6")
        val inst=InstrumentationRegistry.getInstrumentation()
        val context=inst.targetContext
        val container=(context.applicationContext as WritingApplication).container
        val chapter=runBlocking {
            container.database.spaceDao().insert(SpaceEntity(GuestSpace.ID,GuestSpace.KIND_GUEST,null))
            container.writingRepository.createBook(GuestSpace.ID,"晚风来信","林晚","")
        }
        runBlocking {
            container.outlineRepository.renameChapter(chapter.id,"第三章 旧车站")
            val volume=container.outlineRepository.observeOutline(chapter.bookId).first().first()
            repeat(5) { container.outlineRepository.createChapter(chapter.bookId,volume.id,"第${it+4}章 远方") }
            container.database.activeDao().upsertSpaceActive(SpaceActiveEntity(GuestSpace.ID,chapter.bookId,chapter.id,"2026-11-03T00:00:00Z"))
            container.writingProgress.setGoal(chapter.bookId,100000)
            for(i in 0L..27L) container.database.progressDao().insertDay(WritingDayEntity(LocalDate.now().minusDays(i).toString(),if(i==3L) -120 else (i%5)*210,1))
        }
        val activity=inst.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val mode=mutableIntStateOf(0);val dark=mutableStateOf(true)
        fun nodes(n: AccessibilityNodeInfo?):List<AccessibilityNodeInfo> = if(n==null) emptyList() else listOf(n)+(0 until n.childCount).flatMap {nodes(n.getChild(it))}
        fun all()=nodes(inst.uiAutomation.rootInActiveWindow)
        fun waitNode(predicate:(AccessibilityNodeInfo)->Boolean):AccessibilityNodeInfo {
            repeat(80) {all().firstOrNull(predicate)?.let {return it};SystemClock.sleep(150)}
            error("Missing UI node: "+all().mapNotNull {it.text?:it.contentDescription}.joinToString())
        }
        fun click(n:AccessibilityNodeInfo) {
            var target=n
            while(!target.isClickable && target.parent!=null) target=target.parent
            assertTrue(target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        }
        fun snapshot(name:String) {
            SystemClock.sleep(600)
            val file=File(context.getExternalFilesDir("ui-v6"),"$name.png")
            file.outputStream().use {inst.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)}
        }
        fun mount()=inst.runOnMainSync {
            activity.setContent {
                QinglingWritingTheme(darkTheme=dark.value) {
                    CompositionLocalProvider(LocalAppContainer provides container) {
                        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
                            when(mode.intValue) {
                                0->HomeScreen({}, {}, {})
                                1->WritingDashboard({},chapter.bookId)
                                else-> Box(Modifier.fillMaxSize()) {
                                    Surface(Modifier.fillMaxHeight().fillMaxWidth(.82f),color=MaterialTheme.colorScheme.surfaceContainer) {
                                        OutlineContent(chapter.bookId,chapter.id,{},onClose={},modifier=Modifier.statusBarsPadding().navigationBarsPadding())
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        try {
            mount()
            waitNode {it.text?.toString()=="▎继续最近编辑"}
            snapshot("home-dark")
            inst.runOnMainSync {mode.intValue=1}
            waitNode {it.text?.toString()=="活动日历"}
            snapshot("stats-dark")
            click(waitNode {it.text?.toString()=="数据说明"})
            waitNode {it.text?.toString()?.startsWith("净增以成功保存为准")==true}
            // Dismiss this sheet with Android back, which is handled in its own window.
            inst.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            SystemClock.sleep(500)
            val scroll=waitNode {it.isScrollable}
            repeat(3) {scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);SystemClock.sleep(400)}
            click(waitNode {it.text?.toString()=="每日目标"})
            val field=waitNode {it.isEditable}
            val args=Bundle().apply {putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"2300")}
            assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args))
            repeat(60) {if(runBlocking {container.settingsStore.dailyGoal.first()}!=2300) SystemClock.sleep(100)}
            assertEquals(2300,runBlocking {container.settingsStore.dailyGoal.first()})
            inst.runOnMainSync {
                val model=ViewModelProvider(activity)["writing-dashboard",DashboardViewModel::class.java]
                model.saveDaily("23");model.saveDaily("230");model.saveDaily("2301")
            }
            repeat(60) {if(runBlocking {container.settingsStore.dailyGoal.first()}!=2301) SystemClock.sleep(100)}
            assertEquals("Rapid changes must retain the last value",2301,runBlocking {container.settingsStore.dailyGoal.first()})
            snapshot("goal-dark")
            inst.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            SystemClock.sleep(400)
            snapshot("stats-goals-dark")
            inst.runOnMainSync {mode.intValue=2}
            val title=waitNode {it.text?.toString()=="第三章 旧车站"}
            val titleRect=Rect().also {title.getBoundsInScreen(it)}
            val menu=all().filter {it.contentDescription?.toString()=="更多操作"}.minBy {val r=Rect();it.getBoundsInScreen(r);kotlin.math.abs(r.centerY()-titleRect.centerY())}
            click(menu)
            val danger=waitNode {it.text?.toString()=="移入回收站"}
            val dangerBounds=Rect().also {waitNode {it.contentDescription?.toString()=="操作菜单"}.getBoundsInScreen(it)}
            val display=context.resources.displayMetrics
            assertTrue("Menu must stay inside 82% drawer",dangerBounds.right <= display.widthPixels*.82f-10*display.density)
            assertTrue("Menu should align near the panel's right edge",dangerBounds.right > display.widthPixels*.82f-15*display.density)
            snapshot("chapter-menu-dark")
            inst.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            SystemClock.sleep(400)
            inst.runOnMainSync {mode.intValue=1;dark.value=false}
            waitNode {it.text?.toString()=="活动日历"}
            snapshot("stats-light")
        } finally {inst.runOnMainSync {activity.finish()}}
    }
}
