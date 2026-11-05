package com.qingling.writing.data

import java.io.File
import java.nio.file.Files

internal data class CacheUsage(val exports: Long, val backupStaging: Long, val other: Long) {
    val total: Long get() = exports + backupStaging + other
}

/** Count each cache file once and never follow symbolic links outside app storage. */
internal fun cacheUsage(root: File): CacheUsage {
    var exports = 0L
    var backup = 0L
    var other = 0L
    root.walkTopDown().onEnter { !Files.isSymbolicLink(it.toPath()) }
        .filter { it.isFile && !Files.isSymbolicLink(it.toPath()) }.forEach { file ->
            val first = file.relativeTo(root).invariantSeparatorsPath.substringBefore('/')
            when (first) {
                "exports" -> exports += file.length()
                "backup-exports", "auto-backups", "idle-staging" -> backup += file.length()
                else -> other += file.length()
            }
        }
    return CacheUsage(exports, backup, other)
}
