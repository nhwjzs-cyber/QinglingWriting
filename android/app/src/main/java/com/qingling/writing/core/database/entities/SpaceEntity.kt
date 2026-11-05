package com.qingling.writing.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 空间(local-schema.sql 的 spaces;kind 仅 GUEST/ACCOUNT,其 CHECK 约束由数据层
 * GuestSpace 常量与 M5 账号逻辑保证——Room 实体无法声明任意 CHECK,差异在 M1 验收记录说明)。
 */
@Entity(tableName = "spaces")
data class SpaceEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "account_id") val accountId: String?,
)
