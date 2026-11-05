package com.qingling.writing.feature.editor

/** Literal, case-sensitive, non-overlapping matches. Offsets are UTF-16, like TextFieldState. */
internal data class SearchMatch(val start: Int, val end: Int)
internal data class SearchMatches(val ranges: List<SearchMatch>, val total: Int)

internal object TextSearch {
    const val NAVIGATION_LIMIT = 5000
    const val MAX_REPLACED_LENGTH = 8_000_000

    fun find(text: String, query: String, limit: Int = NAVIGATION_LIMIT): SearchMatches {
        if (query.isEmpty()) return SearchMatches(emptyList(), 0)
        val ranges = ArrayList<SearchMatch>()
        var offset = 0
        var total = 0
        while (offset <= text.length - query.length) {
            val at = text.indexOf(query, offset)
            if (at < 0) break
            total++
            if (ranges.size < limit) ranges.add(SearchMatch(at, at + query.length))
            offset = at + query.length
        }
        return SearchMatches(ranges, total)
    }

    fun replace(text: String, query: String, replacement: String, match: SearchMatch? = null): String {
        require(query.isNotEmpty())
        val count = if (match == null) find(text, query, 0).total else {
            require(match.start >= 0 && match.end <= text.length && match.end - match.start == query.length &&
                text.regionMatches(match.start, query, 0, query.length))
            1
        }
        require(text.length.toLong() + count.toLong() * (replacement.length - query.length) <= MAX_REPLACED_LENGTH)
        return if (match == null) text.replace(query, replacement)
        else text.replaceRange(match.start, match.end, replacement)
    }
}
