package com.qingling.writing.core.model

enum class DiffKind { SAME, REMOVED, ADDED }
data class DiffLine(val kind: DiffKind, val text: String)

/** 按行比较历史→当前；限制矩阵内存，大稿退化为保留共同前后缀的替换块。 */
object RevisionDiff {
    fun compare(old: String, current: String): List<DiffLine> {
        val a = old.split('\n'); val b = current.split('\n')
        var prefix = 0
        while (prefix < minOf(a.size,b.size) && a[prefix] == b[prefix]) prefix++
        var suffix = 0
        while (suffix < minOf(a.size,b.size)-prefix && a[a.lastIndex-suffix] == b[b.lastIndex-suffix]) suffix++
        val x = a.subList(prefix,a.size-suffix); val y = b.subList(prefix,b.size-suffix)
        val result = a.take(prefix).map { DiffLine(DiffKind.SAME,it) }.toMutableList()
        if ((x.size.toLong()+1)*(y.size.toLong()+1) <= 200_000) {
            val width = y.size+1
            val dp = IntArray((x.size+1)*width)
            for (i in x.indices.reversed()) for (j in y.indices.reversed()) {
                dp[i*width+j] = if(x[i] == y[j]) 1+dp[(i+1)*width+j+1]
                    else maxOf(dp[(i+1)*width+j],dp[i*width+j+1])
            }
            var i = 0; var j = 0
            while (i < x.size || j < y.size) {
                when {
                    i < x.size && j < y.size && x[i] == y[j] -> { result += DiffLine(DiffKind.SAME,x[i++]); j++ }
                    i < x.size && (j == y.size || dp[(i+1)*width+j] >= dp[i*width+j+1]) -> result += DiffLine(DiffKind.REMOVED,x[i++])
                    else -> result += DiffLine(DiffKind.ADDED,y[j++])
                }
            }
        } else {
            result += x.map { DiffLine(DiffKind.REMOVED,it) }
            result += y.map { DiffLine(DiffKind.ADDED,it) }
        }
        result += a.takeLast(suffix).map { DiffLine(DiffKind.SAME,it) }
        return result
    }
}
