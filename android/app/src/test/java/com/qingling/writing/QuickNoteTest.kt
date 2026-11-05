package com.qingling.writing
import com.qingling.writing.core.model.QuickNote
import org.junit.Assert.*
import org.junit.Test
class QuickNoteTest {
    @Test fun roundTripPreservesUnicodeWhitespaceAndAnchor() {
        val note=QuickNote("银簪😀\n\n原文  ","chapter|id","书 · 章\n标题",120,true,false)
        assertEquals(note,QuickNote.decode(note.encode()))
    }
    @Test fun oldNotesAndMalformedMetadataNeverLoseText() {
        listOf("旧便签\n😀  ","〔轻灵速记·1〕broken","〔轻灵速记·1〕a|b\n原文").forEach {
            assertEquals(it,QuickNote.decode(it).text)
        }
    }
    @Test fun recoveryAndDeletionKeepOriginalContent() {
        val note=QuickNote("未寄出的信",foreshadow=true)
        val resolved=note.copy(resolved=true)
        assertEquals(note.text,QuickNote.decode(resolved.encode()).text)
        assertTrue(QuickNote.decode(resolved.copy(deleted=true).encode()).deleted)
        assertEquals(note,QuickNote.decode(resolved.copy(resolved=false).encode()))
    }
}
