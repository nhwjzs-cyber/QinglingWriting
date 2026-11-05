package com.qingling.writing
import com.qingling.writing.core.model.*
import com.qingling.writing.core.files.*
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.nio.ByteBuffer

class ShelfFontsTest {
    private val a=BookSummary("a","Alpha",100,"2026-10-01",1,"ca",author="C",createdAt="2026-01-01")
    private val b=a.copy(id="b",title="Beta",wordCount=200,updatedAt="2026-11-01",createdAt="2025-01-01",pinned=true)
    private val c=a.copy(id="c",title="Gamma",archived=true,updatedAt="2026-11-02")
    @Test fun scopeSearchAndPinnedPriorityAreCombined() {
        val all=listOf(a,b,c)
        assertEquals(listOf("b","a"),queryShelf(all,"",ShelfScope.ACTIVE,ShelfSort.UPDATED).map{it.id})
        assertEquals(listOf("c"),queryShelf(all,"",ShelfScope.ARCHIVED,ShelfSort.UPDATED).map{it.id})
        assertEquals(listOf("b","c","a"),queryShelf(all,"",ShelfScope.ALL,ShelfSort.UPDATED).map{it.id})
        assertEquals(listOf("a"),queryShelf(all,"  ALP c ",ShelfScope.ACTIVE,ShelfSort.TITLE).map{it.id})
        assertTrue(queryShelf(all,"missing",ShelfScope.ALL,ShelfSort.TITLE).isEmpty())
    }
    @Test fun everySortIsStableAndDoesNotMutateSource() {
        val items=listOf(a,b.copy(pinned=false))
        assertEquals(listOf("b","a"),queryShelf(items,"",ShelfScope.ALL,ShelfSort.WORDS).map{it.id})
        assertEquals(listOf("a","b"),queryShelf(items,"",ShelfScope.ALL,ShelfSort.CREATED).map{it.id})
        assertEquals(listOf("a","b"),queryShelf(items,"",ShelfScope.ALL,ShelfSort.TITLE).map{it.id})
        assertEquals(listOf("a","b"),items.map{it.id})
        assertEquals(listOf("a","z"),queryShelf(listOf(a.copy(id="z"),a),"",ShelfScope.ALL,ShelfSort.UPDATED).map{it.id})
    }
    @Test fun backupFiveKeepsShelfStateAndOlderVersionsReadWithDefaults() {
        val sample=BackupFixture.sample()
        val flagged=sample.copy(books=sample.books.map{it.copy(pinned=true,archived=true)})
        val out=ByteArrayOutputStream();BackupCodec().write(flagged,out)
        assertEquals(flagged,BackupCodec().read(ByteArrayInputStream(out.toByteArray())))
        for(v in 1..4) {
            val old=ByteArrayOutputStream();BackupCodec(writeVersion=v).write(sample,old)
            assertTrue(BackupCodec().read(ByteArrayInputStream(old.toByteArray())).books.none{it.pinned || it.archived})
            try{BackupCodec(writeVersion=v).write(flagged,ByteArrayOutputStream());fail("loss")}catch(_:IllegalArgumentException){}
        }
    }
    @Test fun invalidFontContainersAndOversizeAreRejected() {
        for(bytes in listOf(ByteArray(0),ByteArray(30),ByteArray(FontFileValidator.MAX_BYTES+1),ByteArray(40).also{ByteBuffer.wrap(it).putInt(0x74746366)})) {
            try {FontFileValidator.validate(bytes);fail("font")}catch(_:IllegalArgumentException){}
        }
    }
    @Test fun fontDirectoryBoundsAndUnsignedOffsetsAreChecked() {
        fun value(offset: Int,length: Int): ByteArray=ByteArray(64).also {data->ByteBuffer.wrap(data).apply {
            putInt(0x00010000);putShort(1);position(12);putInt(0x636d6170);putInt(0);putInt(offset);putInt(length)
        }}
        for(bytes in listOf(value(-1,20),value(60,20),value(0,10),value(28,10))) {
            try{FontFileValidator.validate(bytes);fail("incomplete font")}catch(_:IllegalArgumentException){}
        }
    }
}
