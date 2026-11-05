package com.qingling.writing

import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.design.QinglingWritingTheme
import com.qingling.writing.feature.recovery.BackupNotice
import com.qingling.writing.feature.recovery.BackupScreen
import com.qingling.writing.feature.recovery.BackupViewModel
import com.qingling.writing.feature.settings.AppLockControl
import com.qingling.writing.feature.settings.LocalAppLockControl
import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Exercises a delayed picker result against the actual BackupScreen without modifying manuscripts. */
class BackupLockProbe {
    @Test fun passwordPromptWaitsForUnlockAndRetainsResult() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("fixture") == "backup-lock")
        val inst = InstrumentationRegistry.getInstrumentation()
        val container = (inst.targetContext.applicationContext as WritingApplication).container
        val activity = inst.startActivitySync(Intent(inst.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val accessible = mutableStateOf(false)
        lateinit var model: BackupViewModel
        inst.runOnMainSync {
            model = ViewModelProvider(activity, viewModelFactory {
                initializer { BackupViewModel(container.backupRepository, container.backupFiles) }
            })[BackupViewModel::class.java]
            activity.setContent {
                QinglingWritingTheme {
                    CompositionLocalProvider(LocalAppContainer provides container,
                        LocalAppLockControl provides AppLockControl(true, {}, accessible.value)) {
                        BackupScreen(onBack = {})
                    }
                }
            }
        }
        fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = if(node == null) emptyList() else
            listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }
        fun promptVisible() = nodes(inst.uiAutomation.rootInActiveWindow).any { it.text?.toString() == "输入备份密码" }
        try {
            // Header recognition is sufficient to request a password; payload is validated only after entry.
            inst.runOnMainSync { model.inspect { ByteArrayInputStream(byteArrayOf(0x51, 0x4c, 0x45, 0x31)) } }
            repeat(80) { if(model.ui.value.busy || model.ui.value.notice != BackupNotice.PASSWORD_REQUIRED) SystemClock.sleep(100) }
            assertEquals(BackupNotice.PASSWORD_REQUIRED, model.ui.value.notice)
            inst.waitForIdleSync(); SystemClock.sleep(1000)
            assertFalse("Locked picker result must not create a password window", promptVisible())
            inst.runOnMainSync { accessible.value = true }
            var shown = false
            repeat(60) { if(promptVisible()) shown = true; if(!shown) SystemClock.sleep(200) }
            assertTrue("Unlock should display the retained password request", shown)
            inst.runOnMainSync { accessible.value = false }
            var hidden = false
            repeat(60) { if(!promptVisible()) hidden = true; if(!hidden) SystemClock.sleep(200) }
            assertTrue("Relocking should remove the password window", hidden)
            assertEquals(BackupNotice.PASSWORD_REQUIRED, model.ui.value.notice)
        } finally { inst.runOnMainSync { activity.finish() } }
    }
}
