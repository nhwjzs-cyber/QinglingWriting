package com.qingling.writing.core.model

data class BatchScope(val bookId: String, val volumeId: String? = null)
data class BatchVolume(val id: String, val title: String)
sealed interface BatchOperation {
    data class Replace(val find: String, val replacement: String) : BatchOperation
    data class Format(val options: FormatOptions) : BatchOperation
}
data class BatchDocument(val id: String, val volumeId: String, val volumeTitle: String,
    val volumePosition: Long, val title: String, val position: Long, val editSeq: Long, val body: String)
data class BatchSource(val scope: BatchScope, val bookTitle: String, val documents: List<BatchDocument>)
data class BatchChange(val chapterId: String, val after: String, val hits: Int)
data class BatchPlan(val source: BatchSource, val operation: BatchOperation, val changes: List<BatchChange>)
