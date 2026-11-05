package com.qingling.writing

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.qingling.writing.feature.planning.QuickNoteActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.*
import org.junit.Test
import java.io.File

/** Opt-in end-to-end probe. Never clears existing app data or previous consent. */
class Privacy037UiProbe {
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = inst.targetContext
    private val store get() = (context.applicationContext as WritingApplication).container.settingsStore
    private fun nodes(n: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = if (n == null) emptyList() else listOf(n) + (0 until n.childCount).flatMap { nodes(n.getChild(it)) }
    private fun all() = nodes(inst.uiAutomation.rootInActiveWindow)
    private fun waitFor(test: () -> Boolean) { repeat(150) { if (test()) return; SystemClock.sleep(100) }; error("Timed out: " + all().mapNotNull { it.text ?: it.contentDescription }.joinToString()) }
    private fun text(value: String): AccessibilityNodeInfo { waitFor { all().any { it.text?.toString() == value } }; return all().first { it.text?.toString() == value } }
    private fun click(node: AccessibilityNodeInfo) { var n = node; while (!n.isClickable && n.parent != null) n = n.parent; assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_CLICK)); SystemClock.sleep(400) }
    private fun shot(name: String) { File(context.getExternalFilesDir("design037"), name + ".png").outputStream().use { inst.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) } }
    private fun launch() = inst.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity

    @Test fun firstLaunchRefusalExternalEntryPersistenceAndSettings() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("fixture") == "privacy037")
        assumeFalse("Only run this probe before the first consent", runBlocking { store.privacyAccepted.first() })
        assumeFalse("Requires an unlocked test fixture", runBlocking { store.appLockEnabled.first() })
        var activity = launch()
        text("同意并继续"); text("不同意并退出"); shot("01-first-launch")
        assertFalse(all().any { it.contentDescription?.toString() == "设置" || it.isEditable })
        // Rotate before consent: both decisions must remain visible, and the gate must survive recreation.
        inst.uiAutomation.setRotation(android.app.UiAutomation.ROTATION_FREEZE_90)
        waitFor { context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        SystemClock.sleep(600)
        for (label in listOf("同意并继续", "不同意并退出")) {
            val bounds = android.graphics.Rect().also { text(label).getBoundsInScreen(it) }
            assertTrue(label + " must be on screen", bounds.top >= 0 && bounds.bottom <= context.resources.displayMetrics.heightPixels)
        }
        shot("01b-first-launch-landscape")
        inst.uiAutomation.setRotation(android.app.UiAutomation.ROTATION_FREEZE_0)
        waitFor { context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }
        SystemClock.sleep(600)
        inst.runOnMainSync { activity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
            .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().first() }
        // Outside touch cannot dismiss the mandatory dialog.
        inst.uiAutomation.injectInputEvent(android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_DOWN, 2f, 2f, 0), true)
        inst.uiAutomation.injectInputEvent(android.view.MotionEvent.obtain(0, 10, android.view.MotionEvent.ACTION_UP, 2f, 2f, 0), true)
        text("同意并继续")
        inst.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        waitFor { activity.isFinishing || activity.isDestroyed }
        assertFalse(runBlocking { store.privacyAccepted.first() })
        val payload = "未同意前不得显示的外部灵感"
        val external = inst.startActivitySync(Intent(context, QuickNoteActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, payload).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as QuickNoteActivity
        text("同意并继续")
        assertFalse(all().any { it.text?.toString()?.contains(payload) == true || it.isEditable })
        shot("02-external-entry-blocked"); click(text("不同意并退出"))
        waitFor { external.isFinishing || external.isDestroyed }
        assertFalse(runBlocking { store.privacyAccepted.first() })
        activity = launch(); click(text("同意并继续"))
        waitFor { runBlocking { store.privacyAccepted.first() } }
        waitFor { all().any { it.contentDescription?.toString() == "设置" || it.text?.toString() == "选一张写作的底色" } }
        all().firstOrNull { it.text?.toString() == "深灰" }?.let { click(it) }
        inst.runOnMainSync { activity.finish() }
        activity = launch()
        waitFor { all().any { it.contentDescription?.toString() == "设置" } }
        assertFalse(all().any { it.text?.toString() == "同意并继续" })
        click(all().first { it.contentDescription?.toString() == "设置" })
        var reachedBottom = false
        repeat(15) {
            if (!reachedBottom) {
                val scroll = all().lastOrNull { it.isScrollable }
                if (scroll == null || !scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) reachedBottom = true
                SystemClock.sleep(180)
            }
        }
        text("隐私政策"); text("查看全文"); shot("03-settings-bottom")
        click(text("隐私政策")); text("关闭")
        assertFalse(all().any { it.text?.toString() == "不同意并退出" })
        shot("04-policy-from-settings"); click(text("关闭"))
        inst.runOnMainSync { activity.finish() }
    }
}
