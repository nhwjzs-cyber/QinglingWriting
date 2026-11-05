package com.qingling.writing
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.qingling.writing.core.model.QuickNote
import com.qingling.writing.feature.planning.QuickNoteActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class Design035ExternalProbe {
    private val inst get()=InstrumentationRegistry.getInstrumentation()
    private fun nodes(n:AccessibilityNodeInfo?):List<AccessibilityNodeInfo> = if(n==null) emptyList() else listOf(n)+(0 until n.childCount).flatMap{nodes(n.getChild(it))}
    private fun all()=nodes(inst.uiAutomation.rootInActiveWindow)
    private fun waitNode(test:(AccessibilityNodeInfo)->Boolean):AccessibilityNodeInfo {repeat(100){all().firstOrNull(test)?.let{return it};SystemClock.sleep(100)};error("Missing capture node: "+all().mapNotNull{it.text?:it.contentDescription}.joinToString())}
    private fun click(n:AccessibilityNodeInfo){var p=n;while(!p.isClickable&&p.parent!=null)p=p.parent;assertTrue(p.performAction(AccessibilityNodeInfo.ACTION_CLICK))}
    @Test fun externalCaptureRestoresDraftAfterRecreation() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("fixture")=="design035")
        val context=inst.targetContext;val container=(context.applicationContext as WritingApplication).container
        val before=runBlocking{container.settingsStore.appLockEnabled.first()}
        runBlocking{container.settingsStore.setAppLockEnabled(false)}
        var activity:Activity?=null
        try {
            val initial="来自系统选区的灵感😀"
            activity=inst.startActivitySync(Intent(context,QuickNoteActivity::class.java).setAction(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
                .putExtra(Intent.EXTRA_PROCESS_TEXT,initial).putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY,true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitNode{it.text?.toString()=="灵感速记"}
            val changed=initial+"\n重新创建窗口后也要保留。"
            val field=waitNode{it.isEditable};assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,changed)}))
            inst.runOnMainSync{activity?.recreate()}
            SystemClock.sleep(800)
            waitNode{it.isEditable&&it.text?.toString()==changed}
            inst.runOnMainSync{activity=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).first{it is QuickNoteActivity}}
            click(waitNode{it.text?.toString()=="保存"})
            repeat(80){if(runBlocking{container.planning.inbox().first()}.none{QuickNote.decode(it.body).text==changed})SystemClock.sleep(100)}
            assertTrue(runBlocking{container.planning.inbox().first()}.any{QuickNote.decode(it.body).text==changed})
            assertTrue(context.packageManager.queryIntentActivities(Intent(Intent.ACTION_SEND).setType("text/plain"),0).any{it.activityInfo.name.endsWith("QuickNoteActivity")})
        }finally{inst.runOnMainSync{activity?.finish()};runBlocking{container.settingsStore.setAppLockEnabled(before)}}
    }
    @Test fun externalCaptureHonorsApplicationLock() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("fixture")=="design035")
        val context=inst.targetContext;val container=(context.applicationContext as WritingApplication).container
        val before=runBlocking{container.settingsStore.appLockEnabled.first()}
        runBlocking{container.settingsStore.setAppLockEnabled(true)}
        val activity=inst.startActivitySync(Intent(context,QuickNoteActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT,"锁定时不得显示的选区").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            waitNode{it.text?.toString()=="轻灵写作已锁定"}
            assertFalse(all().any{it.isEditable||it.text?.toString()?.contains("锁定时不得显示的选区")==true})
        }finally{inst.runOnMainSync{activity.finish()};runBlocking{container.settingsStore.setAppLockEnabled(before)}}
    }
}
