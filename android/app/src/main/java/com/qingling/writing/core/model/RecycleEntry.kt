package com.qingling.writing.core.model

/**
 * 回收站条目(M1;docs/01 P06:删除项保留 30 天,无自动永久清理)。
 * 一个 [batchId] 对应一次级联删除(docs/01 §4:恢复父项不复活此前已单独删除的子项,
 * 以同一 deletionBatchId 识别级联范围);恢复按批次整体进行。
 */
data class RecycleEntry(
    val batchId: String,
    val kind: Kind,
    val title: String,
    /** 卷/章墓碑的所属书 id;整书条目即其自身 id(恢复接口需要 bookId)。 */
    val bookId: String,
    /** 卷删除/章删除时所属书名,便于区分;整书删除为 null。 */
    val bookTitle: String?,
    /** 整书删除时的范围统计;其余为 null。 */
    val volumeCount: Int?,
    val chapterCount: Int?,
    val deletedAt: String,
    val itemId: String = bookId,
) {
    enum class Kind { BOOK, VOLUME, CHAPTER }
}

data class PurgePlan(val spaceId: String, val entries: List<RecycleEntry>, val bookIds: List<String>,
    val volumeIds: List<String>, val chapterIds: List<String>, val revisionCount: Int,
    val planningIds: List<String> = emptyList())
