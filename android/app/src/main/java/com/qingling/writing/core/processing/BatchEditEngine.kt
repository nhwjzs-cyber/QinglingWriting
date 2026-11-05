package com.qingling.writing.core.processing

import com.qingling.writing.core.model.*

object BatchEditEngine {
    const val MAX_BYTES = 8 * 1024 * 1024
    const val MAX_CHAPTERS = 1000
    fun prepare(source: BatchSource, operation: BatchOperation): BatchPlan {
        require(source.documents.size <= MAX_CHAPTERS) { "一次最多处理 1000 章，请按卷分批" }
        require(source.documents.sumOf { it.body.toByteArray(Charsets.UTF_8).size.toLong() } <= MAX_BYTES) { "原文超过 8 MB，请按卷分批" }
        if (operation is BatchOperation.Replace) require(operation.find.isNotEmpty()) { "查找内容不能为空" }
        if (operation is BatchOperation.Format) require(source.documents.sumOf { d ->
            d.body.count { it == '\n' || it == '\r' }.toLong()
        } <= 50_000) { "段落过多，请按卷分批排版" }
        var totalBytes = 0L
        val changes = source.documents.mapNotNull { doc ->
            var hits = 0
            val after = when (operation) {
                is BatchOperation.Format -> ChapterFormatter.format(doc.body, operation.options)
                is BatchOperation.Replace -> {
                    var from = 0
                    while (from <= doc.body.length - operation.find.length) {
                        val at = doc.body.indexOf(operation.find, from)
                        if (at < 0) break
                        hits++; from = at + operation.find.length
                    }
                    val length = doc.body.length.toLong() + hits.toLong() * (operation.replacement.length - operation.find.length)
                    require(length <= MAX_BYTES) { "替换结果过大，请按卷分批" }
                    doc.body.replace(operation.find, operation.replacement)
                }
            }
            totalBytes += after.toByteArray(Charsets.UTF_8).size
            require(totalBytes <= MAX_BYTES) { "处理后正文超过 8 MB，请按卷分批" }
            if (after == doc.body) null else BatchChange(doc.id, after, hits)
        }
        return BatchPlan(source, operation, changes)
    }
}
