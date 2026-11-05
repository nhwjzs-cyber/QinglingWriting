package com.qingling.writing

import com.qingling.writing.data.cacheUsage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CacheUsageTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun categorizesNestedFilesWithoutDoubleCounting() {
        val root = temp.newFolder()
        fun put(path: String, size: Int) { File(root, path).apply { parentFile!!.mkdirs(); writeBytes(ByteArray(size)) } }
        put("exports/id/book.txt", 11)
        put("backup-exports/a.qlbackup", 13)
        put("auto-backups/b.qlbackup", 17)
        put("idle-staging/c.qlbackup", 19)
        put("other/stuff", 23)
        put("root-file", 29)
        val usage = cacheUsage(root)
        assertEquals(11L, usage.exports)
        assertEquals(49L, usage.backupStaging)
        assertEquals(52L, usage.other)
        assertEquals(112L, usage.total)
    }
}
