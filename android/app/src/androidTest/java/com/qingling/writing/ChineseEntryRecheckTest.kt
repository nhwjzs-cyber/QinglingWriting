package com.qingling.writing

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.design.QinglingWritingTheme
import com.qingling.writing.core.model.GuestSpace
import com.qingling.writing.feature.editor.EditorScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import android.graphics.Bitmap

/** Accessibility text entry is deliberate: this does NOT emulate a Chinese IME candidate session. */
class ChineseEntryRecheckTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val c get() = (inst.targetContext.applicationContext as WritingApplication).container
    private fun nodes(n: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> =
        if (n == null) emptyList() else listOf(n) + (0 until n.childCount).flatMap { nodes(n.getChild(it)) }
    private fun find(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        repeat(80) {
            nodes(inst.uiAutomation.rootInActiveWindow).firstOrNull(predicate)?.let { return it }
            SystemClock.sleep(150)
        }
        error("Missing editor node: " + nodes(inst.uiAutomation.rootInActiveWindow).map { it.text ?: it.contentDescription })
    }
    private fun set(n: AccessibilityNodeInfo, text: String) {
        assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }))
    }
    @Test fun chineseTitleBodyAndActualEnterThreeTimes() {
        repeat(3) { round ->
            val first = runBlocking { c.writingRepository.createBook(GuestSpace.ID, "本轮中文界面测试-$round", "", "") }
            val activity = inst.startActivitySync(Intent(inst.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            try {
                inst.runOnMainSync { activity.setContent {
                    QinglingWritingTheme { CompositionLocalProvider(LocalAppContainer provides c) {
                        EditorScreen(first.id, {}, {}, {}, {}, {})
                    } }
                } }
                val title = "中文章节${round + 1}：雨夜😀"
                val body = "　　中文正文${round + 1}，保留标点与表情😀。"
                set(find { it.isEditable && nodes(it).any { n -> n.contentDescription?.toString() == "章节标题，自动同步目录" } }, title)
                set(find { it.isEditable && nodes(it).any { n -> n.contentDescription?.toString()?.startsWith("正文，") == true } }, body)
                val field = find { it.isEditable && it.text?.toString() == body }
                assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                SystemClock.sleep(500)
                assertTrue(find { it.isEditable && it.text?.toString() == body }.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, body.length)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, body.length)
                }))
                inst.uiAutomation.executeShellCommand("input keyevent 66").use {
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
                }
                val afterEnter = body + "\n　　"
                find { it.isEditable && it.text?.toString() == afterEnter }
                val expected = afterEnter + "第二段继续写作，不能丢失。"
                set(find { it.isEditable && it.text?.toString() == afterEnter }, expected)
                SystemClock.sleep(2200)
                val stored = runBlocking { c.writingRepository.readChapter(first.id) }
                assertEquals(title, stored.title)
                assertEquals(expected, stored.body)
                File(inst.targetContext.getExternalFilesDir("recheck20261105"), "normal-$round.json").writeText(
                    org.json.JSONObject().put("round", round + 1).put("pass", true).put("chapter", first.id).put("title", stored.title).put("body", stored.body).put("method", "accessibility Chinese text + actual Enter keyevent; not Chinese IME").toString()
                )
                if (round == 2) File(inst.targetContext.getExternalFilesDir("recheck20261105"), "normal-editor.png").outputStream().use {
                    inst.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            } finally { inst.runOnMainSync { activity.finish() } }
        }
    }
}
