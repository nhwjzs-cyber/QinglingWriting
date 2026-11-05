package com.qingling.writing.data

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PersistableBundle
import android.provider.DocumentsContract
import com.qingling.writing.core.datastore.AutoBackupStore
import com.qingling.writing.core.files.AutoBackupTarget
import com.qingling.writing.core.files.AutoBackupWriter
import com.qingling.writing.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.UUID

class AutoBackupController(context: Context, val store: AutoBackupStore, private val writer: AutoBackupWriter) {
    private val app = context.applicationContext
    private val resolver = app.contentResolver
    private val scheduler = app.getSystemService(JobScheduler::class.java)
    private val mutex = Mutex()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    suspend fun selectFolder(uri: Uri) = withContext(Dispatchers.IO) { mutex.withLock {
        require(uri.scheme == "content" && DocumentsContract.isTreeUri(uri))
        val old = store.state.first()
        val hadGrant = resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission && it.isWritePermission }
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        try { store.configure(uri.toString(), true, old.hours) }
        catch (e: Exception) {
            if (!hadGrant) runCatching { resolver.releasePersistableUriPermission(uri, GRANTS) }
            throw e
        }
        if (old.folder.isNotEmpty() && old.folder != uri.toString()) runCatching {
            resolver.releasePersistableUriPermission(Uri.parse(old.folder), GRANTS)
        }
        schedule(store.state.first())
    } }

    suspend fun configure(enabled: Boolean, hours: Int) = withContext(Dispatchers.IO) { mutex.withLock {
        val old = store.state.first()
        require(!enabled || old.folder.isNotEmpty())
        store.configure(old.folder, enabled, hours)
        schedule(store.state.first())
    } }

    private suspend fun schedule(state: AutoBackupState) {
        if (!state.enabled || state.folder.isEmpty()) { scheduler.cancel(JOB_ID); store.scheduleStatus(""); return }
        try {
            val existing = scheduler.getPendingJob(JOB_ID)
            if (existing != null && existing.extras.getString("folder") == state.folder &&
                existing.intervalMillis == state.hours * AutoBackupPolicy.HOUR) { store.scheduleStatus(""); return }
            val extras = PersistableBundle().apply { putString("folder", state.folder) }
            val job = JobInfo.Builder(JOB_ID, ComponentName(app, AutoBackupJobService::class.java))
                .setPersisted(true).setPeriodic(state.hours * AutoBackupPolicy.HOUR)
                .setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).setExtras(extras).build()
            check(scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS)
            store.scheduleStatus("")
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { store.scheduleStatus("系统暂时无法安排自动备份，请重新启用，或先立即备份。") }
    }

    suspend fun onApplicationStart() {
        withContext(Dispatchers.IO) { mutex.withLock { schedule(store.state.first()) } }
        runBackup(onlyIfDue = true)
    }

    suspend fun runBackup(onlyIfDue: Boolean = false) = withContext(Dispatchers.IO) { mutex.withLock {
        val state = store.state.first()
        if (!state.enabled || state.folder.isEmpty() || (onlyIfDue && !AutoBackupPolicy.due(state, System.currentTimeMillis()))) return@withLock
        _busy.value = true
        try {
            store.attempt(System.currentTimeMillis())
            val tree = Uri.parse(state.folder)
            writer.write {
                val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                val name = "轻灵写作-自动-${java.time.LocalDate.now()}-${UUID.randomUUID()}.qlbackup"
                val created = DocumentsContract.createDocument(resolver, parent, "application/zip", name)
                    ?: throw IOException("无法创建备份文件")
                object : AutoBackupTarget {
                    override fun output() = resolver.openOutputStream(created, "wt") ?: throw IOException("无法写入")
                    override fun input() = resolver.openInputStream(created) ?: throw IOException("无法校验")
                    override fun delete() { DocumentsContract.deleteDocument(resolver, created) }
                }
            }
            store.success(System.currentTimeMillis())
        } catch (e: CancellationException) {
            withContext(NonCancellable) { runCatching { store.failure("备份被中断，尚未记为成功。请重新尝试，并检查最近成功时间。") } }
            throw e
        } catch (e: Exception) {
            val message = when {
                e is SecurityException -> "文件夹授权已失效，请重新选择备份文件夹。"
                e.message == "AUTO_BACKUP_EMPTY" -> "暂无作品可备份，创建作品后会继续尝试。"
                e.message?.contains("LIMIT") == true || e.message?.contains("LARGE") == true -> "备份超过本版大小限制，请先手动检查备份。"
                else -> "未能写入或校验备份，请检查文件夹、剩余空间及文件提供方。可能留有不完整的新文件，旧备份不受影响。"
            }
            store.failure(message)
        } finally { _busy.value = false }
    } }

    companion object {
        const val JOB_ID = 421016
        private const val GRANTS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
