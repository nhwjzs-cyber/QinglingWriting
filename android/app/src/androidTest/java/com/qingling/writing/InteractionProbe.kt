package com.qingling.writing

import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import com.qingling.writing.core.model.GuestSpace

/** Explicitly invoked UI probe, using the imported custom-chapters fixture; not part of ordinary fixture-free regression. */
class InteractionProbe {
    @Test fun chapterGripMovesAfterTarget() = drag(false)
    @Test fun chapterGripAutoScrollsBeyondOriginalViewport() = drag(true)
    private fun drag(autoScroll: Boolean) {
        org.junit.Assume.assumeTrue("Requires the explicit imported UI fixture",InstrumentationRegistry.getArguments().getString("fixture")=="custom-chapters")
        val inst=InstrumentationRegistry.getInstrumentation();val automation=inst.uiAutomation
        automation.executeShellCommand("am start -W -n com.qingling.writing/.MainActivity").use {fd->android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().readText()}
        SystemClock.sleep(2500)
        val container=(inst.targetContext.applicationContext as WritingApplication).container
        val fixture=runBlocking {
            val book=container.writingRepository.observeBooks(GuestSpace.ID).first().first {it.title=="custom-chapters"}
            container.outlineRepository.observeOutline(book.id).first().flatMap{it.chapters}.first{it.title=="Chapter 1"}.also {
                container.outlineRepository.moveChapter(it.id,it.volumeId,1)
            }
        }
        fun event(down: Long,action: Int,x: Float,y: Float) {
            val e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);e.source=InputDevice.SOURCE_TOUCHSCREEN
            try {assertTrue(automation.injectInputEvent(e,true))}finally{e.recycle()}
        }
        fun tap(x: Float,y: Float) {val down=SystemClock.uptimeMillis();event(down,MotionEvent.ACTION_DOWN,x,y);event(down,MotionEvent.ACTION_UP,x,y)}
        fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
            if(node==null)return emptyList()
            return listOf(node)+(0 until node.childCount).flatMap {nodes(node.getChild(it))}
        }
        fun find(text: String): AccessibilityNodeInfo {
            repeat(30) {nodes(automation.rootInActiveWindow).firstOrNull {it.text?.toString()==text}?.let {return it};SystemClock.sleep(250)}
            error("Missing $text; visible="+nodes(automation.rootInActiveWindow).mapNotNull {it.text ?: it.contentDescription}.joinToString(" | "))
        }
        for(attempt in 0 until 20) {
            val button=nodes(automation.rootInActiveWindow).firstOrNull {it.contentDescription?.toString()=="目录"}
            if(button!=null) {val bounds=Rect();button.getBoundsInScreen(bounds);tap(bounds.exactCenterX(),bounds.exactCenterY());break}
            SystemClock.sleep(200)
        }
        SystemClock.sleep(1000)
        fun bounds(node: AccessibilityNodeInfo)=Rect().also {node.getBoundsInScreen(it)}
        val title=find("Chapter 1");val row=title.parent
        fun grip(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if(node.contentDescription?.toString()?.startsWith("拖动章节排序")==true) return node
            for(i in 0 until node.childCount) {val result=grip(node.getChild(i) ?: continue);if(result!=null)return result}
            return null
        }
        val source=bounds(checkNotNull(grip(row)));val target=bounds(find("Chapter 4"))
        val endY=if(autoScroll) 2050f else target.exactCenterY()
        val down=SystemClock.uptimeMillis();event(down,MotionEvent.ACTION_DOWN,source.exactCenterX(),source.exactCenterY())
        try {
            SystemClock.sleep(1000)
            assertTrue("drag must activate before movement",nodes(automation.rootInActiveWindow).any {it.text?.toString()?.contains("拖动中")==true})
            for(i in 1..15) {event(down,MotionEvent.ACTION_MOVE,source.exactCenterX(),source.exactCenterY()+(endY-source.exactCenterY())*i/15);SystemClock.sleep(35)}
            if(autoScroll) SystemClock.sleep(1800)
        } finally {event(down,MotionEvent.ACTION_UP,source.exactCenterX(),endY)}
        SystemClock.sleep(1200)
        val ordered=runBlocking {container.database.outlineDao().activeChaptersOfVolume(fixture.volumeId)}
        val position=ordered.indexOfFirst{it.id==fixture.id}
        if(autoScroll) assertTrue("must move beyond initial screen; position=$position",position>6)
        else assertTrue("chapter 1 should follow chapter 4; position=$position",position>ordered.indexOfFirst{it.title=="Chapter 4"})
    }
}
