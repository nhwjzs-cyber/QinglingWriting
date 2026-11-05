package com.qingling.writing

import com.qingling.writing.core.files.BackupCodec
import com.qingling.writing.core.model.*
import com.qingling.writing.domain.validation.BackupValidator
import org.junit.Assert.*
import org.junit.Test
import java.io.*

class PlanningBackupTest {
    @Test fun characterFieldsRoundTripWithoutLosingBlankLinesOrLegacyText() {
        val c = CharacterCard("名字😀、小名","身份：旅人","首行\n\n身份：原文标签\n  尾部  ")
        assertEquals(c,CharacterCard.decode(c.encode()))
        assertEquals("旧简介\n\n保留",CharacterCard.decode("旧简介\n\n保留").introduction)
        assertThrows(IllegalArgumentException::class.java) { c.copy(aliases = "姓名\n污染").encode() }
    }
    private fun sample() = BackupFixture.sample().copy(planning = PlanningKind.entries.mapIndexed { i,k ->
        PlanningEntry("p$i","b",if(k == PlanningKind.CHAPTER_OUTLINE) "c" else null,
            if(k == PlanningKind.VOLUME_SUMMARY) "v" else null,k.name,"标题😀$i","  内容\n\n保留  ",BackupFixture.TIME,3)
    })
    @Test fun newBackupPreservesAllPlanningTypesAndOldFormatsStayReadable() {
        val a = sample()
        val out = ByteArrayOutputStream(); BackupCodec().write(a,out)
        assertEquals(a,BackupCodec().read(ByteArrayInputStream(out.toByteArray())))
        for (v in 1..2) {
            val old = ByteArrayOutputStream(); BackupCodec(writeVersion = v).write(BackupFixture.sample(),old)
            assertEquals(BackupFixture.sample(),BackupCodec().read(ByteArrayInputStream(old.toByteArray())))
            assertThrows(IllegalArgumentException::class.java) { BackupCodec(writeVersion = v).write(a,ByteArrayOutputStream()) }
        }
    }
    @Test fun malformedPlanningCannotCrossBooksOrDuplicateScopedEntries() {
        val a = sample(); val first = a.planning.first()
        val invalid = listOf(
            a.copy(planning = listOf(first.copy(bookId = "missing"))),
            a.copy(planning = listOf(first.copy(chapterId = "missing"))),
            a.copy(planning = a.planning+first.copy(id = "another")),
            a.copy(planning = listOf(first.copy(volumeId = "v"))),
            a.copy(planning = listOf(first.copy(kind = "UNKNOWN"))),
            a.copy(planning = listOf(first.copy(body = "a".repeat(PlanningRules.MAX_BODY+1)))),
            a.copy(planning = listOf(first.copy(title = " \n")))
        )
        invalid.forEach { bad -> assertThrows(IllegalArgumentException::class.java) { BackupValidator.validate(bad) } }
    }
}
