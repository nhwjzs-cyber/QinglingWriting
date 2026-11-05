package com.qingling.writing.feature.editor

import org.junit.Assert.*
import org.junit.Test

class TextSearchTest {
    @Test fun literalSearchDoesNotInterpretSqlWildcardsOrRegex() {
        assertEquals(listOf(SearchMatch(1, 4)), TextSearch.find("甲%_.乙", "%_.").ranges)
        assertEquals(0, TextSearch.find("Aa", "aa").total)
        assertEquals(0, TextSearch.find("abc", "").total)
    }
    @Test fun matchesDoNotOverlapAndUseEditorOffsetsForEmoji() {
        assertEquals(listOf(SearchMatch(1, 3), SearchMatch(4, 6)), TextSearch.find("甲😀乙😀", "😀").ranges)
        assertEquals(2, TextSearch.find("aaaaa", "aa").total)
    }
    @Test fun navigationCapDoesNotLoseCountOrLimitReplaceAll() {
        val text = "a".repeat(6001)
        assertEquals(6001, TextSearch.find(text, "a").total)
        assertEquals(5000, TextSearch.find(text, "a").ranges.size)
        assertEquals("b".repeat(6001), TextSearch.replace(text, "a", "b"))
    }
    @Test fun replacementIsLiteralAndCanDeleteOrReplaceOnlyOne() {
        assertEquals("$1.$1.", TextSearch.replace("甲甲", "甲", "$1."))
        assertEquals("乙", TextSearch.replace("甲乙甲", "甲", ""))
        assertEquals("甲乙丙", TextSearch.replace("甲乙甲", "甲", "丙", SearchMatch(2, 3)))
        assertEquals("甲", TextSearch.replace("甲", "甲", "甲"))
    }
    @Test fun emptyAndStaleMatchAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { TextSearch.replace("甲", "", "乙") }
        assertThrows(IllegalArgumentException::class.java) { TextSearch.replace("甲乙", "甲", "丙", SearchMatch(1, 2)) }
    }
    @Test fun oversizedResultIsRejectedBeforeAllocation() {
        assertThrows(IllegalArgumentException::class.java) {
            TextSearch.replace("a".repeat(10000), "a", "b".repeat(1000))
        }
    }
}
