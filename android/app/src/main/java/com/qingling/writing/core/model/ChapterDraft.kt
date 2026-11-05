package com.qingling.writing.core.model

/**
 * 待落库草稿(contracts/WritingContracts.kt 的 ChapterDraft)。
 * editSeq 由 EditorSession 单调递增分配;仓储层以 edit_seq 防旧写(旧写永不覆盖新版本)。
 */
data class ChapterDraft(
    val chapterId: String,
    val title: String,
    val body: String,
    val editSeq: Long,
    val countAsWriting: Boolean = false,
    val inputMetrics: InputMetrics? = null,
    val titleEdited: Boolean = false,
)
