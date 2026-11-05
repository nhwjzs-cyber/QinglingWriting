package com.qingling.writing.feature.export

import com.qingling.writing.core.files.ExpiredExportCache
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExpiredExportCacheTest {
    @get:Rule val temp = TemporaryFolder()
    private val now = 1_800_000_000_000L
    private val old = now - 8L * 24 * 60 * 60 * 1000
    private fun oldFile(root: File, name: String = "work.txt"): File {
        val dir = File(root, "00000000-0000-0000-0000-000000000001").apply { mkdirs() }
        return File(dir, name).apply { writeText("text"); setLastModified(old); dir.setLastModified(old) }
    }

    @Test fun removesOnlyOldExportFiles() {
        val root = temp.newFolder()
        val oldFile = oldFile(root)
        val protected = File(oldFile.parentFile, "backup.qlbackup").apply { writeText("backup"); setLastModified(old) }
        oldFile.parentFile.setLastModified(old)
        val unrelated = File(root, "unrelated").apply { mkdir() }
        val store = ExpiredExportCache(root)
        val preview = store.preview(now)
        assertEquals(1, preview.items.size)
        assertEquals(4, preview.bytes)
        assertEquals(1, store.clear(preview, now))
        assertFalse(oldFile.exists())
        assertTrue(protected.exists())
        assertTrue(unrelated.exists())
    }

    @Test fun recentFilesAreNeverEligible() {
        val root = temp.newFolder()
        val file = oldFile(root)
        file.setLastModified(now)
        assertTrue(ExpiredExportCache(root).preview(now).items.isEmpty())
        assertTrue(file.exists())
    }

    @Test fun changedFileInvalidatesPreviewWithoutDeletion() {
        val root = temp.newFolder()
        val file = oldFile(root)
        val store = ExpiredExportCache(root)
        val preview = store.preview(now)
        file.appendText("changed")
        file.setLastModified(old)
        try { store.clear(preview, now); fail("Expected invalidated preview") }
        catch (_: IllegalStateException) { }
        assertTrue(file.exists())
    }

    @Test fun missingRootIsAnEmptyPreview() {
        assertTrue(ExpiredExportCache(File(temp.root, "missing")).preview(now).items.isEmpty())
    }
}
