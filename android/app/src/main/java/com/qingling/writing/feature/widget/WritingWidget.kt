package com.qingling.writing.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.qingling.writing.MainActivity
import com.qingling.writing.R
import com.qingling.writing.WritingApplication
import com.qingling.writing.core.database.entities.WritingDayEntity
import com.qingling.writing.core.model.writingStats
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class WritingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refreshAsync(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action in setOf(Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) refreshAsync(context)
    }

    private fun refreshAsync(context: Context) {
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                withTimeout(8_000) {
                    val container = (context.applicationContext as WritingApplication).container
                    if (!container.settingsStore.privacyAccepted.first()) {
                        render(context, emptyList(), locked = true, privacyAccepted = false)
                        return@withTimeout
                    }
                    val rows = container.writingProgress.days().first()
                    render(context, rows, container.settingsStore.appLockEnabled.first())
                }
            } catch (_: Exception) {
                // Retain the last widget value if storage is temporarily unavailable.
            } finally { pending.finish(); scope.cancel() }
        }
    }

    companion object {
        const val ACTION_RESUME = "com.qingling.writing.RESUME_WRITING"

        fun render(context: Context, rows: List<WritingDayEntity>, locked: Boolean, date: LocalDate = LocalDate.now(), privacyAccepted: Boolean = true) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WritingWidget::class.java))
            if (ids.isEmpty()) return
            val launch = Intent(context, MainActivity::class.java).setAction(ACTION_RESUME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val open = PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val net = writingStats(rows, date).todayNet
            val views = RemoteViews(context.packageName, R.layout.writing_widget).apply {
                setTextViewText(R.id.widget_count, if (!privacyAccepted) "请先同意隐私政策" else if (locked) "应用已锁定" else context.getString(R.string.widget_today_value, net))
                setOnClickPendingIntent(R.id.widget_root, open)
                val note=Intent(context,com.qingling.writing.feature.planning.QuickNoteActivity::class.java).setAction("com.qingling.writing.QUICK_NOTE")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                setOnClickPendingIntent(R.id.widget_quick_note,PendingIntent.getActivity(context,1,note,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
