package com.qingling.writing.core.model

data class StructureSnapshot(val bookId: String,val bookSequence: Long,val documents: List<BatchDocument>)
data class StructurePlan(val source: StructureSnapshot,val splitOffset: Int? = null,val includeTitles: Boolean = false)

object ChapterStructure {
    const val MAX_CHAPTERS = 200
    const val MAX_BYTES = 8 * 1024 * 1024
    fun split(body: String,offset: Int): Pair<String,String> {
        require(offset > 0 && offset < body.length) { "请把光标放在正文中间，不能在开头或末尾拆分" }
        require(!(body[offset-1].isHighSurrogate() && body[offset].isLowSurrogate())) { "拆分位置不能切开一个字符" }
        require(!(body[offset-1] == '\r' && body[offset] == '\n')) { "请将光标移到完整换行之后" }
        require(body.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "单次处理正文不超过 8 MB" }
        return body.substring(0,offset) to body.substring(offset)
    }
    fun merge(documents: List<BatchDocument>,includeTitles: Boolean): String {
        require(documents.size in 2..MAX_CHAPTERS) { "请选择 2 到 200 章" }
        require(documents.sumOf { it.body.toByteArray(Charsets.UTF_8).size.toLong() } <= MAX_BYTES) { "单次处理正文不超过 8 MB" }
        val body = documents.joinToString("\n\n") { if(includeTitles) it.title+"\n"+it.body else it.body }
        require(body.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "合并后正文超过 8 MB，请减少选择范围" }
        return body
    }
}
