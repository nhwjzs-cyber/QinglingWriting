package com.qingling.writing
import com.qingling.writing.core.files.BackupCodec
import com.qingling.writing.core.model.*
import com.qingling.writing.domain.validation.BackupValidator
import org.junit.Assert.*
import org.junit.Test
import java.io.*
class QuotedNoteCapacity044Test {
    private fun entry(quote:String,memo:String="备注")=PlanningEntry("quoted044","b",null,null,PlanningKind.INSPIRATION.name,"引用测试",QuickNote(memo,chapterId="c",sourceQuote=quote,sourceEnd=quote.length,sourceKind="QUOTE").encode(),BackupFixture.TIME)
    @Test fun thirtyThousandChineseQuoteRoundTripsThroughValidatedBackup() {
        val p=entry("中".repeat(30_000))
        assertTrue(p.body.length>PlanningRules.MAX_BODY)
        PlanningRules.validate(p)
        val a=BackupFixture.sample().copy(planning=listOf(p));BackupValidator.validate(a)
        val bytes=ByteArrayOutputStream();BackupCodec().write(a,bytes)
        val restored=BackupCodec().read(ByteArrayInputStream(bytes.toByteArray()));BackupValidator.validate(restored)
        assertEquals(p.body,restored.planning.single().body)
    }
    @Test fun fiveThousandLessThanFiftyThousandUtf16WithUnicodeStillFits() {
        PlanningRules.validate(entry("😀中".repeat(15_000),"备注"))
        PlanningRules.validate(entry("中".repeat(49_998)))
        assertThrows(IllegalArgumentException::class.java){PlanningRules.validate(entry("中".repeat(50_001)))}
    }
    @Test fun ordinaryAndMalformedLargeEntriesDoNotBypassLimit() {
        val p=entry("中".repeat(30_000))
        assertThrows(IllegalArgumentException::class.java){PlanningRules.validate(p.copy(kind=PlanningKind.TERM.name))}
        assertThrows(IllegalArgumentException::class.java){PlanningRules.validate(p.copy(body="〔轻灵速记·1〕"+"x".repeat(100_001)))}
    }
}
