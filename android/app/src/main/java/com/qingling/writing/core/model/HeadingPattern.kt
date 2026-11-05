package com.qingling.writing.core.model

/** Custom headings match complete individual lines. Limit engine work, including pathological backtracking. */
object HeadingPattern {
    private class Budget {
        var remaining = 2_000_000
        val deadline = System.nanoTime() + 750_000_000L
        fun check() { require(--remaining > 0 && System.nanoTime() < deadline) { "分章规则过于复杂，请简化表达式" } }
    }
    private class Guarded(private val value: String,private val from: Int,override val length: Int,private val budget: Budget): CharSequence {
        override fun get(index: Int): Char { budget.check();return value[from+index] }
        override fun subSequence(startIndex: Int,endIndex: Int): CharSequence = Guarded(value,from+startIndex,endIndex-startIndex,budget)
        override fun toString() = value.substring(from,from+length)
    }
    fun matches(text: String,expression: String): List<MatchResult> {
        require(expression.isNotBlank() && expression.length <= 256) { "请输入 1–256 字符的分章正则" }
        require(expression.count { it == '(' } <= 16) { "分章规则分组过多" }
        val regex = try { Regex(expression) } catch (_: IllegalArgumentException) { throw IllegalArgumentException("正则格式不正确，请检查括号和转义") }
        val budget=Budget();val result=mutableListOf<MatchResult>();var start=0
        try {
            while(start < text.length) {
                budget.check()
                val end=text.indexOf('\n',start).takeIf {it>=0} ?: text.length
                val length=end-start
                if(length in 1..512) {
                    val match=regex.matchEntire(Guarded(text,start,length,budget))
                    if(match!=null && match.value.isNotBlank()) {
                        // Return a regular result with absolute offsets, so shared splitting preserves all prose.
                        val absolute=Regex(Regex.escape(text.substring(start,end))).find(text,start)!!
                        result.add(absolute)
                        require(result.size <= TxtImport.MAX_CHAPTERS) { "最多支持 1000 章" }
                    }
                }
                start=end+1
            }
        } catch (_: StackOverflowError) { throw IllegalArgumentException("分章规则过于复杂，请简化表达式") }
        return result
    }
}
