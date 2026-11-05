package com.qingling.writing

import com.qingling.writing.core.files.BackupCodec
import com.qingling.writing.core.model.*
import com.qingling.writing.domain.validation.BackupValidator
import org.junit.Assert.*
import org.junit.Test
import java.io.*

class SingleBookBackupTest {
    @Test fun bookScopeKeepsDeletedHistoryAndPlanningWithoutOtherBookData() {
        val a = BackupFixture.sample().copy(planning = listOf(PlanningEntry("p","b","c",null,"CHAPTER_OUTLINE","细纲","保留",BackupFixture.TIME,1)))
        val b = a.copy(books = a.books.map { it.copy(id = "other",lastChapter = "oc") },
            volumes = a.volumes.map { it.copy(id = "ov",bookId = "other") },
            chapters = a.chapters.map { it.copy(id = "o"+it.id,bookId = "other",volumeId = "ov",batch = it.batch?.plus("other")) },
            revisions = a.revisions.map { it.copy(id = "or",chapterId = "oc") },positions = a.positions.map { it.copy(chapterId = "oc") },planning = emptyList())
        val all = a.copy(books = a.books+b.books,volumes = a.volumes+b.volumes,chapters = a.chapters+b.chapters,
            revisions = a.revisions+b.revisions,positions = a.positions+b.positions)
        val selected = all.forBook("b");BackupValidator.validate(selected);assertEquals(a,selected)
        val out = ByteArrayOutputStream();BackupCodec().write(selected,out)
        assertEquals(selected,BackupCodec().read(ByteArrayInputStream(out.toByteArray())))
    }
    @Test fun missingBookCannotSilentlyFallBackToWholeLibrary() {
        assertThrows(IllegalStateException::class.java) { BackupFixture.sample().forBook("missing") }
    }
}
