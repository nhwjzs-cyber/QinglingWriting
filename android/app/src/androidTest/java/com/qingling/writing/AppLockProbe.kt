package com.qingling.writing

import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.qingling.writing.core.database.entities.SpaceActiveEntity
import com.qingling.writing.core.database.entities.SpaceEntity
import com.qingling.writing.core.model.GuestSpace
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicitly prepared disposable emulator only; sets an app preference and creates a test manuscript. */
class AppLockProbe {
    @Test fun lockHidesManuscriptAndRequiresDeviceCredential() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("fixture") == "app-lock" && args.getString("pin") == "741258")
        val inst = InstrumentationRegistry.getInstrumentation()
        val container = (inst.targetContext.applicationContext as WritingApplication).container
        runBlocking {
            container.database.spaceDao().insert(SpaceEntity(GuestSpace.ID, GuestSpace.KIND_GUEST, null))
            val chapter = container.writingRepository.createBook(GuestSpace.ID, "锁屏私密测试", "", "")
            container.database.activeDao().upsertSpaceActive(SpaceActiveEntity(GuestSpace.ID, chapter.bookId, chapter.id, "2026-11-03T00:00:00Z"))
            container.settingsStore.setAppLockEnabled(true)
        }
        val activity = inst.startActivitySync(Intent(inst.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val automation = inst.uiAutomation
        fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = if (node == null) emptyList() else
            listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }
        fun has(text: String) = nodes(automation.rootInActiveWindow).any { it.text?.toString()?.contains(text) == true }
        fun waitFor(text: String): Boolean { repeat(80) { if (has(text)) return true; SystemClock.sleep(250) }; return false }
        fun tapText(text: String) {
            val node = nodes(automation.rootInActiveWindow).first { it.text?.toString() == text }
            val rect = Rect(); node.getBoundsInScreen(rect); val down = SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, rect.exactCenterX(), rect.exactCenterY(), 0)
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
            }
        }
        fun shell(command: String) { automation.executeShellCommand(command).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() } }
        try {
            assertTrue("Lock screen should appear", waitFor("轻灵写作已锁定"))
            assertFalse("Locked screen must hide manuscript semantics", has("锁屏私密测试"))
            assertTrue("Secure window flag must protect the app switcher", activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            tapText("解锁")
            SystemClock.sleep(3000)
            shell("input keyevent 4")
            assertTrue("Cancelling authentication must keep the lock", waitFor("轻灵写作已锁定"))
            assertFalse("Cancelled authentication must not reveal manuscript", has("锁屏私密测试"))
            SystemClock.sleep(500)
            tapText("解锁")
            SystemClock.sleep(3000)
            val pinOption = nodes(automation.rootInActiveWindow).firstOrNull { it.text?.toString() in setOf("Use PIN", "使用 PIN 码", "使用 PIN", "使用密码") }
            if (pinOption != null) { tapText(pinOption.text.toString()); SystemClock.sleep(1000) }
            shell("input text 741258")
            shell("input keyevent 66")
            assertTrue("Correct device credential should unlock", waitFor("锁屏私密测试"))
            var privateDialog: android.app.AlertDialog? = null
            inst.runOnMainSync {
                privateDialog = android.app.AlertDialog.Builder(activity).setTitle("私密对话框测试").setMessage("正文内容测试").setPositiveButton("关闭", null).show()
            }
            assertTrue("Private dialog should be present before leaving", waitFor("私密对话框测试"))
            shell("input keyevent 3")
            SystemClock.sleep(1500)
            shell("am start -n com.qingling.writing/.MainActivity")
            assertTrue("Returning from background should lock again", waitFor("轻灵写作已锁定"))
            assertFalse("Background lock must hide manuscript semantics", has("锁屏私密测试"))
            assertFalse("Lock must cover separate dialog windows", has("私密对话框测试"))
            inst.runOnMainSync { privateDialog?.dismiss() }
            shell("am start -n com.qingling.writing/.MainActivity -a com.qingling.writing.RESUME_WRITING -f 0x34000000")
            assertTrue("Widget resume must not bypass the lock", waitFor("轻灵写作已锁定"))
            assertFalse("Widget resume must not expose manuscript", has("锁屏私密测试"))
        } finally {
            activity.finish()
            runBlocking { container.settingsStore.setAppLockEnabled(false) }
        }
    }
}
