package com.qingling.writing.app

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.room.Room
import com.qingling.writing.BuildConfig
import com.qingling.writing.core.datastore.SettingsStore
import com.qingling.writing.core.database.WritingDatabase
import com.qingling.writing.core.database.entities.SpaceEntity
import com.qingling.writing.core.database.migrations.Migrations
import com.qingling.writing.core.model.GuestSpace
import com.qingling.writing.data.DemoSeeder
import com.qingling.writing.data.RoomHistoryRepository
import com.qingling.writing.data.RoomBackupRepository
import com.qingling.writing.data.RoomOutlineRepository
import com.qingling.writing.data.RoomRecycleBinRepository
import com.qingling.writing.data.RoomWritingRepository
import com.qingling.writing.domain.repository.HistoryRepository
import com.qingling.writing.domain.repository.OutlineRepository
import com.qingling.writing.domain.repository.RecycleBinRepository
import com.qingling.writing.domain.repository.WritingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import java.time.Instant
import java.util.UUID

/**
 * CompositionLocal 通道:把容器注入 Compose 树,供各 ViewModel 工厂读取。
 * 约束(docs/03 §3):UI 仍不直连数据层,数据一律经 ViewModel 暴露。
 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer is not provided. It must be initialized by WritingApplication and injected in MainActivity.")
}

/**
 * 手工依赖容器(docs/03 §2 app/)。
 * M1:Room 数据库 + 三个领域仓储(书与章/卷章目录/回收站);依赖方向 UI → ViewModel → 仓储实现。
 * 演示数据改为 debug 构建下写入真实数据库的种子(仍带「演示·」前缀与角标,docs/05 §5);
 * release 永不种子,书架空态。游客空间幂等确保(INSERT OR IGNORE)。
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    /** 应用级后台作用域:仅用于启动期的空间确保与演示种子,不承载任何正文/编辑任务。 */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val recoveryDrafts=com.qingling.writing.data.RecoveryDraftStore(java.io.File(appContext.noBackupFilesDir,"recovery-drafts"))
    val importedFonts=com.qingling.writing.core.files.ImportedFontStore(appContext)

    val database: WritingDatabase =
        Room.databaseBuilder(appContext, WritingDatabase::class.java, WritingDatabase.NAME)
            .addMigrations(*Migrations.ALL) // 版本化迁移;禁止破坏性回退(docs/05 §3)
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addCallback(com.qingling.writing.data.DurableDatabaseCallback)
            .build()
    val storageManager = com.qingling.writing.data.StorageManager(appContext,database)

    // 身份一律 UUID(docs/05 §3);时间为 UTC RFC3339(contracts 头注);注入便于测试。
    private val newId: () -> String = { UUID.randomUUID().toString() }
    private val now: () -> String = { Instant.now().toString() }

    val writingRepository: WritingRepository = RoomWritingRepository(database, newId, now)
    val recoveryRescue=com.qingling.writing.data.RecoveryRescueRepository(database,writingRepository,recoveryDrafts)
    val writingProgress = com.qingling.writing.data.WritingProgressRepository(database)
    val outlineRepository: OutlineRepository = RoomOutlineRepository(database, newId, now)
    val planning = com.qingling.writing.data.PlanningRepository(database,outlineRepository,writingRepository,newId,now)
    val searchRepository: com.qingling.writing.domain.repository.SearchRepository = com.qingling.writing.data.RoomSearchRepository(database)
    val recycleBinRepository: RecycleBinRepository = RoomRecycleBinRepository(database)
    val settingsStore: SettingsStore = SettingsStore(appContext)
    val historyRepository: HistoryRepository = RoomHistoryRepository(database, newId, now) { settingsStore.autoHistoryLimit.first() }
    val txtImportRepository = com.qingling.writing.data.TxtImportRepository(database, writingRepository, outlineRepository)
    val batchEditRepository = com.qingling.writing.data.RoomBatchEditRepository(database, newId, now)
    val structure = com.qingling.writing.data.ChapterStructureRepository(database,newId,now)
    val exportFiles = com.qingling.writing.core.files.ExportFileStore(java.io.File(appContext.cacheDir, "exports"))

    val exportRepository: com.qingling.writing.domain.repository.ExportRepository =
        com.qingling.writing.data.RoomExportRepository(database)

    /** 偏好(DataStore,只放偏好;当前仅主题三态,G04)。 */
    val backgroundImages = com.qingling.writing.core.files.EditorBackgroundStore(appContext)
    val backupRepository: com.qingling.writing.domain.repository.BackupRepository = RoomBackupRepository(database, newId, now)
    val backupFiles = com.qingling.writing.core.files.BackupFileStore(java.io.File(appContext.cacheDir, "backup-exports"))
    val autoBackupStore = com.qingling.writing.core.datastore.AutoBackupStore(appContext)
    val autoBackup = com.qingling.writing.data.AutoBackupController(appContext, autoBackupStore,
        com.qingling.writing.core.files.AutoBackupWriter(backupRepository,
            com.qingling.writing.core.files.BackupFileStore(java.io.File(appContext.cacheDir, "auto-backups"))))

    val idleBackup = com.qingling.writing.data.IdleBackupController(java.io.File(appContext.noBackupFilesDir, "idle-backups"),
        com.qingling.writing.core.files.AutoBackupWriter(backupRepository,
            com.qingling.writing.core.files.BackupFileStore(java.io.File(appContext.cacheDir, "idle-staging")))) { autoBackup.runBackup() }

    init {
        applicationScope.launch {
        // No database observation, automatic backup or demo initialization before consent.
        if (settingsStore.privacyAccepted.firstOrNull { it } != true) return@launch
        applicationScope.launch { recoveryDrafts.upgradeLegacyCopies() }
        applicationScope.launch {
            kotlinx.coroutines.flow.combine(writingProgress.days(), settingsStore.appLockEnabled) { rows, locked -> rows to locked }.collect { (rows, locked) ->
                try { com.qingling.writing.feature.widget.WritingWidget.render(appContext, rows, locked) }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { /* Widget updates must not affect writing. */ }
            }
        }
        applicationScope.launch {importedFonts.refresh()}
        applicationScope.launch { idleBackup.monitor(kotlinx.coroutines.flow.combine(
            database.batchEditDao().observeVersions(),database.planningDao().versions(),database.bookGoalDao().versions(),database.shelfFlagsDao().versions()
        ) { chapters,planning,goals,shelf -> chapters + planning + goals + shelf }) }
        applicationScope.launch {
            database.spaceDao().insert(
                SpaceEntity(id = GuestSpace.ID, kind = GuestSpace.KIND_GUEST, accountId = null),
            )
            if (BuildConfig.DEBUG) {
                DemoSeeder.seedIfEmpty(writingRepository, outlineRepository, database, appContext)
            }
            try { autoBackup.onApplicationStart() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { /* Opening a local manuscript must not depend on backup availability. */ }
        }
        }
    }
}
