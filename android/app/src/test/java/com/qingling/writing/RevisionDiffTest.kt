package com.qingling.writing
import com.qingling.writing.core.model.*
import org.junit.Assert.*
import org.junit.Test

class RevisionDiffTest {
    private fun verify(a: String,b: String) {
        val diff = RevisionDiff.compare(a,b)
        assertEquals(a,diff.filter { it.kind != DiffKind.ADDED }.joinToString("\n") { it.text })
        assertEquals(b,diff.filter { it.kind != DiffKind.REMOVED }.joinToString("\n") { it.text })
    }
    @Test fun reconstructsBothVersionsIncludingBlankLinesAndUnicode() {
        listOf("" to "", "" to "新增", "删除" to "", "甲\n😀\n\n乙" to "甲\n新\n\n乙", "甲\n甲" to "甲").forEach { verify(it.first,it.second) }
    }
    @Test fun largeManuscriptHasBoundedMatrixAndPreservesEveryLine() {
        verify("前\n"+(1..1000).joinToString("\n") { "旧$it" }+"\n后", "前\n"+(1..1000).joinToString("\n") { "新$it" }+"\n后")
    }
}
