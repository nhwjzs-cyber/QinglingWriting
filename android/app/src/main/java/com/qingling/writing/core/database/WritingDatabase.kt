package com.qingling.writing.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.qingling.writing.core.database.dao.ActiveDao
import com.qingling.writing.core.database.dao.BookDao
import com.qingling.writing.core.database.dao.EditorPositionDao
import com.qingling.writing.core.database.dao.OutlineDao
import com.qingling.writing.core.database.dao.RecycleDao
import com.qingling.writing.core.database.dao.RevisionDao
import com.qingling.writing.core.database.dao.SpaceDao
import com.qingling.writing.core.database.entities.BookActiveEntity
import com.qingling.writing.core.database.entities.BookEntity
import com.qingling.writing.core.database.entities.ChapterEntity
import com.qingling.writing.core.database.entities.EditorPositionEntity
import com.qingling.writing.core.database.entities.RevisionEntity
import com.qingling.writing.core.database.entities.SpaceActiveEntity
import com.qingling.writing.core.database.entities.SpaceEntity
import com.qingling.writing.core.database.entities.VolumeEntity

/**
 * 本地数据库 schema v3(M1 v1 + M2 v2 + UX v0.2 v3;docs/03 §2、contracts/local-schema.sql)。
 *
 * v1:结构四表(spaces/books/volumes/chapters)。
 * v2(M2):+ revisions(历史版本)、editor_positions(光标/滚动位置)。
 * v3(UX v0.2):+ space_active(空间最近激活)、book_active(每书最近激活章)、
 *   books.last_active_chapter_id 列。迁移 Migrations.MIGRATION_2_3。
 * v1:结构四表(spaces/books/volumes/chapters)。
 * v2(M2):+ revisions(历史版本)、editor_positions(光标/滚动位置);迁移 Migrations.MIGRATION_1_2,
 * 迁移测试(androidTest,MigrationTestHelper)覆盖 1→2 数据保留。后续 sync_outbox/sync_cursors/
 * conflicts 等随 M4/M5 落地——每步都有版本化迁移与测试,不在启动时执行脚本绕开 Room。
 *
 * 与 SQL 参考的已声明差异(Room 不支持任意 CHECK,由数据层保证并在验收记录说明):
 * 标题 trim 非空/无 CR/LF、position >= 0、删除配对 (deleted_at IS NULL) = (deletion_batch_id IS NULL)、
 * books 的 synced_local_seq <= local_seq、revisions.source 取值域。
 */
@Database(
    entities = [
        com.qingling.writing.core.database.entities.ShelfFlagsEntity::class,
        com.qingling.writing.core.database.entities.InputMetricDay::class,
        com.qingling.writing.core.database.entities.InputMetricCursor::class,
        com.qingling.writing.core.database.entities.BookGoalEntity::class,
        com.qingling.writing.core.database.entities.BookWritingDayEntity::class,
        com.qingling.writing.core.database.entities.PlanningEntity::class,
        com.qingling.writing.core.database.entities.ChapterDetailsEntity::class,
        com.qingling.writing.core.database.entities.WritingDayEntity::class,
        SpaceEntity::class,
        BookEntity::class,
        VolumeEntity::class,
        ChapterEntity::class,
        RevisionEntity::class,
        EditorPositionEntity::class,
        SpaceActiveEntity::class,
        BookActiveEntity::class,
    ],
    version = 10,
    exportSchema = true,
)
abstract class WritingDatabase : RoomDatabase() {
    abstract fun shelfFlagsDao(): com.qingling.writing.core.database.dao.ShelfFlagsDao
    abstract fun inputMetricDao(): com.qingling.writing.core.database.dao.InputMetricDao
    abstract fun bookGoalDao(): com.qingling.writing.core.database.dao.BookGoalDao
    abstract fun structureDao(): com.qingling.writing.core.database.dao.StructureDao
    abstract fun planningDao(): com.qingling.writing.core.database.dao.PlanningDao
    abstract fun progressDao(): com.qingling.writing.core.database.dao.ProgressDao
    abstract fun batchEditDao(): com.qingling.writing.core.database.dao.BatchEditDao
    abstract fun purgeDao(): com.qingling.writing.core.database.dao.PurgeDao
    abstract fun backupDao(): com.qingling.writing.core.database.dao.BackupDao

    abstract fun spaceDao(): SpaceDao

    abstract fun bookDao(): BookDao

    abstract fun outlineDao(): OutlineDao

    abstract fun recycleDao(): RecycleDao

    abstract fun revisionDao(): RevisionDao

    abstract fun editorPositionDao(): EditorPositionDao

    abstract fun activeDao(): ActiveDao

    companion object {
        const val NAME = "writing.db"
    }
}
