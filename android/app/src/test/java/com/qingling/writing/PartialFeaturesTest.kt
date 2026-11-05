package com.qingling.writing
import com.qingling.writing.core.model.*
import org.junit.Assert.*
import org.junit.Test

class PartialFeaturesTest {
    @Test fun customHeadingsPreserveProsePrefaceAndBlankLines() {
        val result=TxtImport.split("序言\nChapter 1 开端\n甲\n\nChapter 2\n乙\n","稿",true,"Chapter [0-9]+.*")
        assertEquals(listOf("前言","Chapter 1 开端","Chapter 2"),result.map {it.title})
        assertEquals("甲\n\n",result[1].body);assertEquals("乙\n",result[2].body)
        assertEquals("正文不匹配",TxtImport.split("正文不匹配","稿",true,"Chapter [0-9]+").single().body)
    }
    @Test fun emptyInvalidAndDangerousRegexAreRejectedWithoutImport() {
        for(pattern in listOf("","[","(".repeat(17)+")".repeat(17))) {
            try {TxtImport.split("正文","稿",true,pattern);fail(pattern)}catch(_:IllegalArgumentException){}
        }
        try {TxtImport.split(("a".repeat(500)+"!\n").repeat(40),"稿",true,"(a+)+");fail("budget")}catch(_:IllegalArgumentException){}
        assertEquals(1,TxtImport.split("\n\n正文","稿",true,".*").size) // Blank matches never create empty chapter titles.
    }
    @Test fun overlongHeadingCandidateIsKeptAsProse() {
        val body="x".repeat(600)
        assertEquals(body,TxtImport.split(body,"稿",true,".*").single().body)
    }
    @Test fun metricsHandleUnicodeReplacementDeletionAndWhitespace() {
        assertEquals(1L,insertedCodePoints("甲😀乙","甲😁乙"))
        assertEquals(0L,insertedCodePoints("甲乙","甲"))
        assertEquals(2L,insertedCodePoints("甲乙","甲\n　 丙😀乙"))
    }
    @Test fun compositionAndProgramChangesDoNotInflateInput() {
        var now=0L;val meter=InputMeter(7,"",{now})
        meter.observe("ni",true);now=1000;meter.observe("你",false)
        assertEquals(1L,meter.snapshot().inserted)
        now=3000;meter.observe("你好",false)
        assertEquals(InputMetrics(7,2,2000),meter.snapshot())
        meter.ignore("自动排版");meter.observe("自动排版",false)
        assertEquals(2L,meter.snapshot().inserted)
        meter.setActive(false);now=4000;meter.observe("后台变化",false)
        meter.setActive(true);now=8000;meter.observe("后台变化甲",false)
        assertEquals(InputMetrics(7,3,2000),meter.snapshot())
    }
    @Test fun longIdleAndClockRewindDoNotCountAsWritingTime() {
        var now=0L;val meter=InputMeter(0,"",{now})
        meter.observe("甲",false);now=31000;meter.observe("甲乙",false)
        now=30000;meter.observe("甲乙丙",false)
        assertEquals(0L,meter.snapshot().activeMillis)
    }
}
