package com.qingling.writing.feature.planning
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import com.qingling.writing.MainActivity

class QuickNoteTile: TileService() {
    @Suppress("DEPRECATION")
    override fun onClick() {
        super.onClick()
        val intent=Intent(this,QuickNoteActivity::class.java).setAction("com.qingling.writing.QUICK_NOTE")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        unlockAndRun {
            if(Build.VERSION.SDK_INT>=34) startActivityAndCollapse(PendingIntent.getActivity(this,7,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            else launchLegacy(intent)
        }
    }
    // API 26–33 has no PendingIntent overload; the branch above excludes API 34+.
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun launchLegacy(intent: Intent) { startActivityAndCollapse(intent) }
}
