package com.qingling.writing.feature.editor

import org.junit.Assert.*
import org.junit.Test

class ProofreaderTest {
    @Test fun balancedPunctuationAndUnmatchedOffsets() {
        assertTrue(Proofreader.scan("他说：“你好（朋友）。”", emptyList()).issues.isEmpty())
        val issue = Proofreader.scan("😀你好）", emptyList()).issues.single()
        assertEquals(4, issue.start)
        assertEquals(5, issue.end)
    }

    @Test fun detectsRepetitionAndCustomTerms() {
        val issues = Proofreader.scan("日日天气天气，BAD", listOf("bad")).issues
        assertTrue(issues.any { it.message.contains("重复字") && it.start == 0 })
        assertTrue(issues.any { it.message.contains("重复词") && it.start == 2 })
        assertTrue(issues.any { it.message.contains("词表命中") && it.start == 7 })
    }

    @Test fun longParagraphUsesCodePointsAndBoundedExcerpt() {
        val result = Proofreader.scan("😀".repeat(301), emptyList())
        assertTrue(result.issues.single().message.contains("301"))
        assertTrue(result.issues.single().excerpt.length <= 80)
    }

    @Test fun excessiveUnmatchedBracketsAreBounded() {
        val result = Proofreader.scan("（".repeat(2000), emptyList())
        assertTrue(result.truncated)
        assertTrue(result.issues.size <= 1000)
    }
}
