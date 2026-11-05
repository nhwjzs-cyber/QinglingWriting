package com.qingling.writing.core.model

/**
 * 章节文档(contracts/WritingContracts.kt 的 ChapterDocument)。
 * M1 仅读取展示;正文写入与 editSeq 防旧写在 M2 由 SaveCoordinator 接管。
 */
data class ChapterDocument(
    val id: String,
    val bookId: String,
    val volumeId: String,
    val title: String,
    val body: String,
    val editSeq: Long,
)
