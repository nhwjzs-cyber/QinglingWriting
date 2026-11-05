package com.qingling.writing.domain.validation

/**
 * 字数口径(docs/01 §7):按 Unicode 码点遍历,不计 Unicode 空白,包含标点和 emoji 组成码点。
 * 纯 JVM 逻辑;JVM 单测覆盖中文、emoji、空白、CRLF(docs/05 §6)。口径变更必须显式改此文件并同步文档,
 * 不得悄悄改口径。
 */
object WordCounter {

    private fun counted(codePoint: Int) = !Character.isWhitespace(codePoint)

    /** Immutable offsets allow cursor/selection changes to count in O(log n), using the same rule as count. */
    class Index internal constructor(text: String) {
        private val ends: IntArray
        init {
            val buffer = IntArray(text.length); var size = 0; var offset = 0
            while (offset < text.length) {
                val point = Character.codePointAt(text, offset)
                offset += Character.charCount(point)
                if (counted(point)) buffer[size++] = offset
            }
            ends = buffer.copyOf(size)
        }
        val total: Long get() = ends.size.toLong()
        fun before(utf16Offset: Int): Long {
            var low = 0; var high = ends.size
            while (low < high) { val mid = (low + high) ushr 1; if (ends[mid] <= utf16Offset) low = mid + 1 else high = mid }
            return low.toLong()
        }
    }
    fun index(text: String) = Index(text)

    /** 供 DB 字段与 UI 共用;按码点计,白名单外的空白一律不计。 */
    fun count(text: String): Long {
        var n = 0L
        var i = 0
        val len = text.length
        while (i < len) {
            val codePoint = Character.codePointAt(text, i)
            i += Character.charCount(codePoint)
            if (counted(codePoint)) n++
        }
        return n
    }
}
