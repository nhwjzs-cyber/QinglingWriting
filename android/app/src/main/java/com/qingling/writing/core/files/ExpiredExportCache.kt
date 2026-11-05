package com.qingling.writing.core.files

import java.io.File
import java.nio.file.Files

/** Only old, flat, app-created export cache files are eligible. Never follows links. */
internal class ExpiredExportCache(private val root: File) {
    data class Item(val directory: String, val name: String, val size: Long, val modified: Long)
    data class Preview(val items: List<Item>) {
        val bytes: Long get() = items.sumOf { it.size }
    }

    fun preview(now: Long = System.currentTimeMillis()): Preview {
        if (!root.exists()) return Preview(emptyList())
        require(root.isDirectory && !Files.isSymbolicLink(root.toPath()))
        val result = mutableListOf<Item>()
        for (dir in root.listFiles() ?: error("无法读取导出缓存")) {
            if (Files.isSymbolicLink(dir.toPath()) || !dir.isDirectory || !UUID.matches(dir.name) ||
                dir.canonicalFile.parentFile != root.canonicalFile || now - dir.lastModified() <= AGE) continue
            for (file in dir.listFiles() ?: error("无法读取导出缓存目录")) {
                if (Files.isSymbolicLink(file.toPath()) || !file.isFile || file.extension !in EXTENSIONS ||
                    file.canonicalFile.parentFile != dir.canonicalFile || now - file.lastModified() <= AGE) continue
                result.add(Item(dir.name, file.name, file.length(), file.lastModified()))
                require(result.size <= 5000) { "导出缓存文件过多，请分批管理" }
            }
        }
        return Preview(result.sortedWith(compareBy({ it.directory }, { it.name })))
    }

    fun clear(expected: Preview, now: Long = System.currentTimeMillis()): Int {
        check(preview(now) == expected) { "缓存范围已变化，请重新预览" }
        var removed = 0
        for (item in expected.items) {
            val dir = File(root, item.directory)
            val file = File(dir, item.name)
            check(!Files.isSymbolicLink(dir.toPath()) && !Files.isSymbolicLink(file.toPath()) &&
                dir.canonicalFile.parentFile == root.canonicalFile && file.canonicalFile.parentFile == dir.canonicalFile &&
                file.isFile && file.length() == item.size && file.lastModified() == item.modified) {
                "缓存已变化，已清理的文件不受影响；请重新预览"
            }
            check(file.delete()) { "部分缓存未能删除，请刷新后重试" }
            removed++
            dir.delete() // Only succeeds for empty directories.
        }
        return removed
    }

    companion object {
        private const val AGE = 7L * 24 * 60 * 60 * 1000
        private val UUID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        private val EXTENSIONS = setOf("txt", "docx")
    }
}
