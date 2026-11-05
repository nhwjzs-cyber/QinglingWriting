/**
 * Room 数据层(docs/03 §2:entities, dao, migrations, WritingDatabase)。
 * M0 留空:Room 2.8.5 与 KSP 2.3.13 已锁定在 gradle/libs.versions.toml,M1 引入;
 * 正文仅保存在 Room 管理的本地数据库,不使用 fallbackToDestructiveMigration(docs/05 §3)。
 */
package com.qingling.writing.core.database
