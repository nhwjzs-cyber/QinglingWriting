package com.qingling.writing.data

import android.app.job.JobService
import android.app.job.JobParameters
import com.qingling.writing.WritingApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class AutoBackupJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var running: Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        running = scope.launch {
            try {
                val container = (application as WritingApplication).container
                if (container.settingsStore.privacyAccepted.first()) container.autoBackup.runBackup(onlyIfDue = true)
            }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* DataStore unavailable: do not claim success. Next period will retry. */ }
            jobFinished(params, false)
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { running?.cancel(); running = null; return true }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
