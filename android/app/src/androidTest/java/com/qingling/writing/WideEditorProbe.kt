package com.qingling.writing

import android.content.Intent
import android.os.SystemClock
import android.graphics.Rect
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.qingling.writing.core.database.entities.SpaceActiveEntity
import com.qingling.writing.core.database.entities.SpaceEntity
import com.qingling.writing.core.model.GuestSpace
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Run only on an explicitly prepared disposable wide emulator. */
class WideEditorProbe {
    @Test fun wideEditorCanHideAndReopenOutline() {
        val inst = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("fixture") == "wide-editor")
        assumeTrue(inst.targetContext.resources.configuration.screenWidthDp >= 720)
        val container = (inst.targetContext.applicationContext as WritingApplication).container
        runBlocking {
            container.database.spaceDao().insert(SpaceEntity(GuestSpace.ID, GuestSpace.KIND_GUEST, null))
            val chapter = container.writingRepository.createBook(GuestSpace.ID, "宽屏验证", "", "")
            container.database.activeDao().upsertSpaceActive(SpaceActiveEntity(GuestSpace.ID, chapter.bookId, chapter.id, "2026-11-03T00:00:00Z"))
        }
        val activity = inst.startActivitySync(Intent(inst.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val automation = inst.uiAutomation
        fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = if (node == null) emptyList() else
            listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }
        fun find(description: String): AccessibilityNodeInfo? {
            repeat(60) {
                nodes(automation.rootInActiveWindow).firstOrNull { it.contentDescription?.toString() == description }?.let { return it }
                SystemClock.sleep(250)
            }
            return null
        }
        fun tap(node: AccessibilityNodeInfo) {
            val bounds = Rect(); node.getBoundsInScreen(bounds)
            val down = SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, bounds.exactCenterX(), bounds.exactCenterY(), 0)
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
            }
        }
        try {
            val close = find("关闭目录")
            assertTrue("Wide outline should be visible", close != null)
            tap(close!!)
            var hidden = false
            repeat(40) {
                if (nodes(automation.rootInActiveWindow).none { it.contentDescription?.toString() == "关闭目录" }) hidden = true
                if (!hidden) SystemClock.sleep(250)
            }
            assertTrue("Outline should hide", hidden)
            val menu = find("目录")
            assertTrue("Outline button should be available", menu != null)
            tap(menu!!)
            assertTrue("Outline should reopen", find("关闭目录") != null)
        } finally { activity.finish() }
    }
}
