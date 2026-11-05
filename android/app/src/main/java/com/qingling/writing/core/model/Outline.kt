package com.qingling.writing.core.model

/**
 * 目录树(docs/01 §4):书 → 卷 → 章 三级;卷与章始终按持久化 position 的目录顺序,
 * 不按编辑时间或标题字典序;同级顺序由数据层 SQL 保证(volumes_book_order / chapters_volume_order)。
 */
data class OutlineVolume(
    val id: String,
    val bookId: String,
    val title: String,
    val position: Long,
    val chapters: List<OutlineChapter>,
    val summary: String = "",
)

data class OutlineChapter(
    val id: String,
    val bookId: String,
    val volumeId: String,
    val title: String,
    val position: Long,
    val wordCount: Long,
    val updatedAt: String = "",
    val completed: Boolean = false,
    val outline: String = "",
    val unnamed: Boolean = false,
)
