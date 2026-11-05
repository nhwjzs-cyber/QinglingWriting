package com.qingling.writing

import com.qingling.writing.core.model.*
import org.junit.Assert.*
import org.junit.Test

class ChapterStructureTest {
    @Test fun splitPreservesAllOriginalWhitespaceAndUnicode() {
        val body = "　甲😀\r\n\n  乙👩‍🚀  "
        for (offset in 1 until body.length) {
            if (body[offset-1].isHighSurrogate() && body[offset].isLowSurrogate() || body[offset-1] == '\r' && body[offset] == '\n') continue
            val (a,b) = ChapterStructure.split(body,offset)
            assertEquals(body,a+b);assertEquals(offset,a.length)
        }
    }
    @Test fun invalidBoundariesAndOversizeAreRejected() {
        listOf(0,4,-1,5,2).forEach { offset -> assertThrows(IllegalArgumentException::class.java) { ChapterStructure.split("甲😀乙",offset) } }
        assertThrows(IllegalArgumentException::class.java) { ChapterStructure.split("甲\r\n乙",2) }
        assertThrows(IllegalArgumentException::class.java) { ChapterStructure.split("a".repeat(ChapterStructure.MAX_BYTES+1),1) }
    }
    private fun row(id: String,body: String) = BatchDocument(id,"v","卷",0,"标题$id",0,1,body)
    @Test fun mergeKeepsSuppliedDirectoryOrderAndOriginalContent() {
        val rows = listOf(row("b","甲\n  "),row("a","😀\r\n乙"),row("c",""))
        assertEquals("甲\n  \n\n😀\r\n乙\n\n",ChapterStructure.merge(rows,false))
        assertEquals(rows.joinToString("\n\n") { it.title+"\n"+it.body },ChapterStructure.merge(rows,true))
    }
    @Test fun mergeRequiresBoundedMultipleChapters() {
        assertThrows(IllegalArgumentException::class.java) { ChapterStructure.merge(listOf(row("a","")),false) }
        assertThrows(IllegalArgumentException::class.java) { ChapterStructure.merge((1..201).map { row("$it","") },false) }
        assertThrows(IllegalArgumentException::class.java) { ChapterStructure.merge(listOf(row("a","a".repeat(ChapterStructure.MAX_BYTES)),row("b","")),false) }
    }
}
