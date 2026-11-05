package com.qingling.writing.core.model

enum class ExportFormat(val extension: String,val mime: String) {
    TXT("txt","text/plain"), DOCX("docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document")
}

/** 导出快照(contracts/WritingContracts.kt;卷和章按目录顺序,一个快照内不可变)。 */
data class ExportSnapshot(
    val bookTitle: String,
    val volumes: List<ExportVolume>,
    val bookLocalSeq: Long,
)

data class ExportVolume(
    val id: String,
    val title: String,
    val chapters: List<ExportChapter>,
)

data class ExportChapter(
    val id: String,
    val volumeId: String,
    val title: String,
    val body: String,
)

/** 导出选择(contracts);chapterIds 为用户勾选的存活章 UUID 集合。 */
data class ExportSelection(
    val bookId: String,
    val chapterIds: Set<String>,
    val includeBookTitle: Boolean = true,
    val includeVolumeTitles: Boolean = true,
    val includeChapterTitles: Boolean = true,
)
