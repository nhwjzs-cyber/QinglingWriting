package com.qingling.writing.data

import android.content.Context
import androidx.room.withTransaction
import com.qingling.writing.core.database.WritingDatabase
import com.qingling.writing.core.model.GuestSpace
import com.qingling.writing.core.files.ExpiredExportCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class StorageReport(val manuscriptBytes: Long,val historyBytes: Long,val historyCount: Long,
    val databaseBytes: Long,val backupBytes: Long,val backupCount: Int,val cacheBytes: Long,val availableBytes: Long,
    val exportCacheBytes: Long,val backupCacheBytes: Long,val otherCacheBytes: Long)
data class AutoCleanupPreview(val keep: Int,val ids: List<String>,val estimatedBytes: Long)

class StorageManager(private val context: Context,private val db: WritingDatabase) {
    private val expiredExports = ExpiredExportCache(File(context.cacheDir, "exports"))
    internal suspend fun previewExportCache() = withContext(Dispatchers.IO) { expiredExports.preview() }
    internal suspend fun clearExportCache(preview: ExpiredExportCache.Preview) = withContext(Dispatchers.IO) { expiredExports.clear(preview) }
    suspend fun report(): StorageReport = withContext(Dispatchers.IO) {
        val info=db.revisionDao().storageInfo(GuestSpace.ID)
        val database=context.getDatabasePath(WritingDatabase.NAME)
        val dbBytes=listOf(database,File(database.path+"-wal"),File(database.path+"-shm")).sumOf {it.length()}
        val backupRoot=File(context.noBackupFilesDir,"idle-backups")
        val copies=backupRoot.listFiles().orEmpty().filter {it.isFile && it.name.endsWith(".qlbackup")}
        val cache=cacheUsage(context.cacheDir)
        StorageReport(info.manuscriptBytes,info.historyBytes,info.historyCount,dbBytes,copies.sumOf {it.length()},copies.size,cache.total,context.filesDir.usableSpace,
            cache.exports,cache.backupStaging,cache.other)
    }
    private suspend fun candidates(keep: Int): AutoCleanupPreview {
        require(keep in listOf(20,50,100))
        val rows=db.revisionDao().autoForCleanup(GuestSpace.ID)
        require(rows.size<=100_000) {"自动历史数量过多，请分批管理"}
        val remove=rows.groupBy {it.chapterId}.values.flatMap {it.drop(keep)}
        return AutoCleanupPreview(keep,remove.map {it.id}.sorted(),remove.sumOf {it.contentBytes})
    }
    suspend fun preview(keep: Int): AutoCleanupPreview = withContext(Dispatchers.IO) {db.withTransaction {candidates(keep)}}
    suspend fun apply(preview: AutoCleanupPreview): Int = withContext(Dispatchers.IO) {
        db.withTransaction {
            val current=candidates(preview.keep)
            check(current==preview) {"历史版本已变化，请重新预览"}
            var deleted=0
            current.ids.chunked(400).forEach {deleted+=db.revisionDao().deleteAutoByIds(it)}
            check(deleted==current.ids.size) {"清理范围已变化，请重新预览"}
            deleted
        }
    }
}
