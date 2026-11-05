package com.qingling.writing

import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
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

/** Opt-in only: creates a book on a disposable phone emulator. */
class EditorEdgeSwipeProbe {
    @Test fun bothEdgesOpenAtTopMiddleAndBottomAfterHintsExpire() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("fixture") == "editor-edges")
        val inst = InstrumentationRegistry.getInstrumentation()
        val context = inst.targetContext
        assumeTrue(context.resources.configuration.screenWidthDp < 720)
        val container = (context.applicationContext as WritingApplication).container
        runBlocking {
            container.settingsStore.setAppLockEnabled(false)
            container.database.spaceDao().insert(SpaceEntity(GuestSpace.ID, GuestSpace.KIND_GUEST, null))
            val chapter = container.writingRepository.createBook(GuestSpace.ID, "全高度滑动验证", "", "")
            container.database.activeDao().upsertSpaceActive(SpaceActiveEntity(GuestSpace.ID, chapter.bookId, chapter.id, "2026-11-03T00:00:00Z"))
        }
        val activity = inst.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val automation = inst.uiAutomation
        fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = if (node == null) emptyList() else
            listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }
        fun hasClose(left: Boolean) = nodes(automation.rootInActiveWindow).any {
            it.contentDescription?.toString() == if (left) "关闭目录" else "关闭"
        }
        fun back() {
            automation.injectInputEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK), true)
            automation.injectInputEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK), true)
        }
        try {
            inst.waitForIdleSync()
            SystemClock.sleep(6500) // Hint visibility must not determine the gesture hit region.
            val metrics = context.resources.displayMetrics
            val w = metrics.widthPixels.toFloat()
            val h = metrics.heightPixels.toFloat()
            val inset = 44f * metrics.density
            for (left in listOf(true, false)) for (fraction in listOf(0.08f, 0.5f, 0.90f)) {
                val x = if (left) inset else w - inset
                val end = if (left) w * 0.75f else w * 0.25f
                val y = h * fraction
                val down = SystemClock.uptimeMillis()
                for (step in 0..20) {
                    val action = when (step) { 0 -> MotionEvent.ACTION_DOWN; 20 -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE }
                    val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x + (end - x) * step / 20f, y, 0)
                    event.source = InputDevice.SOURCE_TOUCHSCREEN
                    try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
                    SystemClock.sleep(16)
                }
                repeat(40) { if (!hasClose(left)) SystemClock.sleep(200) }
                assertTrue("Panel left=$left should open at height=$fraction; size=$w/$h inset=$inset; UI=" + nodes(automation.rootInActiveWindow).map { it.text ?: it.contentDescription }.filterNotNull().joinToString(), hasClose(left))
                SystemClock.sleep(1200) // Wait for the moving close button to settle before tapping.
                val close = nodes(automation.rootInActiveWindow).first {
                    it.contentDescription?.toString() == if (left) "关闭目录" else "关闭"
                }
                var button = close
                while (!button.isClickable && button.parent != null) button = button.parent
                assertTrue("Close action should be available", button.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                repeat(40) { if (hasClose(left)) SystemClock.sleep(200) }
                assertTrue("Panel left=$left should close at height=$fraction", !hasClose(left))
            }
        } finally { inst.runOnMainSync { activity.finish() } }
    }
}
