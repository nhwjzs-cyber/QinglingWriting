package com.qingling.writing.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 版本化迁移(docs/05 §3)。CREATE 语句必须与 Room 生成的 schema 逐字一致;
 * 迁移测试见 androidTest(MigrationTestHelper + 1.json/2.json/3.json)。
 */
object Migrations {

    /** v1 → v2:新增历史版本表与编辑器光标位置表(M2)。纯新增,不触碰既有四表。 */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `revisions` (`id` TEXT NOT NULL, " +
                    "`chapter_id` TEXT NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, " +
                    "`content_sha256` TEXT NOT NULL, `source` TEXT NOT NULL, `created_at` TEXT NOT NULL, " +
                    "PRIMARY KEY(`id`), FOREIGN KEY(`chapter_id`) REFERENCES `chapters`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE RESTRICT )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_revisions_chapter_time` " +
                    "ON `revisions` (`chapter_id`, `created_at`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `editor_positions` (`chapter_id` TEXT NOT NULL, " +
                    "`selection_start` INTEGER NOT NULL, `selection_end` INTEGER NOT NULL, " +
                    "`scroll_offset` INTEGER NOT NULL, PRIMARY KEY(`chapter_id`), " +
                    "FOREIGN KEY(`chapter_id`) REFERENCES `chapters`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE RESTRICT )",
            )
        }
    }

    /** v2 → v3:新增最近激活表 + books 列(UX v0.2 §1.1 冷启动/点书续写)。 */
    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `books` ADD COLUMN `last_active_chapter_id` TEXT DEFAULT NULL",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `space_active` (`space_id` TEXT NOT NULL, " +
                    "`last_active_book_id` TEXT, `last_active_chapter_id` TEXT, " +
                    "`updated_at` TEXT NOT NULL, PRIMARY KEY(`space_id`), " +
                    "FOREIGN KEY(`space_id`) REFERENCES `spaces`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE RESTRICT )",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `book_active` (`book_id` TEXT NOT NULL, " +
                    "`last_active_chapter_id` TEXT NOT NULL, `updated_at` TEXT NOT NULL, " +
                    "PRIMARY KEY(`book_id`), FOREIGN KEY(`book_id`) REFERENCES `books`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE RESTRICT )",
            )
        }
    }

    /** 全部迁移,按版本顺序;DatabaseBuilder addMigrations(*ALL) 注册。 */
    val MIGRATION_3_4: Migration = object : Migration(3,4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `chapter_details` (`chapter_id` TEXT NOT NULL, `completed` INTEGER NOT NULL, PRIMARY KEY(`chapter_id`), FOREIGN KEY(`chapter_id`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE TABLE IF NOT EXISTS `writing_days` (`day` TEXT NOT NULL, `net_words` INTEGER NOT NULL, `edits` INTEGER NOT NULL, PRIMARY KEY(`day`))")
        }
    }
    val MIGRATION_4_5: Migration = object : Migration(4,5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `planning_entries` (`id` TEXT NOT NULL, `book_id` TEXT NOT NULL, `chapter_id` TEXT, `volume_id` TEXT, `kind` TEXT NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, `updated_at` TEXT NOT NULL, `version` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`book_id`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`chapter_id`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`volume_id`) REFERENCES `volumes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_planning_entries_book_id` ON `planning_entries` (`book_id`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_planning_entries_chapter_id` ON `planning_entries` (`chapter_id`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_planning_entries_volume_id` ON `planning_entries` (`volume_id`)")
        }
    }
    val MIGRATION_5_6: Migration = object : Migration(5,6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `book_goals` (`book_id` TEXT NOT NULL, `target_words` INTEGER NOT NULL, PRIMARY KEY(`book_id`), FOREIGN KEY(`book_id`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE TABLE IF NOT EXISTS `book_writing_days` (`book_id` TEXT NOT NULL, `day` TEXT NOT NULL, `net_words` INTEGER NOT NULL, `edits` INTEGER NOT NULL, PRIMARY KEY(`book_id`, `day`), FOREIGN KEY(`book_id`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        }
    }
    val MIGRATION_6_7: Migration = object: Migration(6,7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `input_metric_days` (`day` TEXT NOT NULL, `inserted_words` INTEGER NOT NULL, `active_millis` INTEGER NOT NULL, PRIMARY KEY(`day`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `input_metric_cursors` (`chapter_id` TEXT NOT NULL, `base_seq` INTEGER NOT NULL, `inserted_words` INTEGER NOT NULL, `active_millis` INTEGER NOT NULL, PRIMARY KEY(`chapter_id`), FOREIGN KEY(`chapter_id`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        }
    }
    val MIGRATION_7_8: Migration = object: Migration(7,8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `shelf_flags` (`book_id` TEXT NOT NULL, `pinned` INTEGER NOT NULL, `archived` INTEGER NOT NULL, PRIMARY KEY(`book_id`), FOREIGN KEY(`book_id`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        }
    }
    val MIGRATION_8_9: Migration = object: Migration(8,9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE planning_new (id TEXT NOT NULL, book_id TEXT, chapter_id TEXT, volume_id TEXT, kind TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, updated_at TEXT NOT NULL, version INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(book_id) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(chapter_id) REFERENCES chapters(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(volume_id) REFERENCES volumes(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("INSERT INTO planning_new SELECT * FROM planning_entries")
            // Recover inspiration previously hidden under a recycled book into the general inbox.
            db.execSQL("UPDATE planning_new SET book_id=NULL,version=version+1 WHERE kind='INSPIRATION' AND book_id IN (SELECT id FROM books WHERE deleted_at IS NOT NULL)")
            db.execSQL("DROP TABLE planning_entries")
            db.execSQL("ALTER TABLE planning_new RENAME TO planning_entries")
            db.execSQL("CREATE INDEX index_planning_entries_book_id ON planning_entries(book_id)")
            db.execSQL("CREATE UNIQUE INDEX index_planning_entries_chapter_id ON planning_entries(chapter_id)")
            db.execSQL("CREATE UNIQUE INDEX index_planning_entries_volume_id ON planning_entries(volume_id)")
        }
    }
    val MIGRATION_9_10: Migration = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE books ADD COLUMN chapter_number_counter INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE chapters ADD COLUMN automatic_number INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE chapters ADD COLUMN title_locked INTEGER NOT NULL DEFAULT 1")
            // Includes recycled chapters. Existing titles are locked and are never reinterpreted as automatic.
            db.execSQL("UPDATE books SET chapter_number_counter = (SELECT count(*) FROM chapters WHERE book_id=books.id)")
        }
    }
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,MIGRATION_6_7,MIGRATION_7_8,MIGRATION_8_9,MIGRATION_9_10)
}
