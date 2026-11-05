package com.qingling.writing.core.model

/**
 * 历史版本(contracts/WritingContracts.kt 参考模型;M0 复核 F4 要求 M1 补齐建模)。
 * 对应 local-schema.sql 的 revisions 表;表结构在 M2 随 schema v2 迁移落地,
 * 历史快照(AUTO/MANUAL/BEFORE_RESTORE)在 M2 生效,此处先行锁定领域形状。
 */
data class Revision(
    val id: String,
    val chapterId: String,
    val title: String,
    val body: String,
    /** 正文 SHA-256,十六进制小写;备份与历史校验共用同一口径。 */
    val contentSha256: String,
    val source: Source,
    val createdAt: String,
) {
    /** 取值域与 local-schema.sql 的 CHECK 一致。 */
    enum class Source { AUTO, MANUAL, BEFORE_RESTORE, BEFORE_CHANGE, IMPORTED }
}
