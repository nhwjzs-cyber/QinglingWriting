package com.qingling.writing.domain.validation

import com.qingling.writing.core.model.*
import java.time.Instant

object BackupValidator {
    fun validate(a: LibraryBackup) {
        Instant.parse(a.createdAt)
        require(a.books.size <= 500 && a.volumes.size <= 10_000 && a.chapters.size <= 50_000 &&
            a.revisions.size <= 50_000 && a.positions.size <= a.chapters.size && a.planning.size <= 50_000) { "BACKUP_LIMIT" }
        fun <T> index(rows: List<T>, id: (T) -> String): Map<String, T> {
            require(rows.all { id(it).isNotBlank() && id(it).length <= 128 }) { "BACKUP_ID" }
            return rows.associateBy(id).also { require(it.size == rows.size) { "BACKUP_DUPLICATE" } }
        }
        val books = index(a.books) { it.id }
        val volumes = index(a.volumes) { it.id }
        val chapters = index(a.chapters) { it.id }
        index(a.revisions) { it.id }; index(a.positions) { it.chapterId }
        index(a.planning) { it.id }
        require(a.planning.mapNotNull { it.chapterId }.distinct().size == a.planning.count { it.chapterId != null }) { "BACKUP_DUPLICATE" }
        require(a.planning.mapNotNull { it.volumeId }.distinct().size == a.planning.count { it.volumeId != null }) { "BACKUP_DUPLICATE" }
        val batches = mutableMapOf<String, String>()
        fun dates(created: String, updated: String) { Instant.parse(created); Instant.parse(updated) }
        fun deletion(deleted: String?, batch: String?, owner: String) {
            require((deleted == null) == (batch == null)) { "BACKUP_DELETION" }
            if (deleted != null) {
                Instant.parse(deleted)
                require(!batch.isNullOrBlank() && batch.length <= 128) { "BACKUP_DELETION" }
                require(batches.putIfAbsent(batch, owner).let { it == null || it == owner }) { "BACKUP_BATCH_OWNER" }
            }
        }
        a.books.forEach { b ->
            require(b.chapterNumberCounter >= 0) { "BACKUP_CHAPTER_NUMBER" }
            require(b.targetWords in 0..10_000_000) { "BACKUP_GOAL" }
            require(TitleValidator.checkBookTitle(b.title).error == null) { "BACKUP_TITLE" }
            dates(b.createdAt, b.updatedAt); deletion(b.deletedAt, b.batch, b.id)
            b.lastChapter?.let { require(chapters[it]?.bookId == b.id) { "BACKUP_ACTIVE" } }
        }
        a.volumes.forEach { v ->
            val book = requireNotNull(books[v.bookId]) { "BACKUP_BOOK" }
            require(TitleValidator.checkSectionTitle(v.title).error == null && v.position >= 0) { "BACKUP_VOLUME" }
            require(book.deletedAt == null || v.deletedAt != null) { "BACKUP_DELETION" }
            dates(v.createdAt, v.updatedAt); deletion(v.deletedAt, v.batch, v.bookId)
        }
        require(a.chapters.filter { it.automaticNumber > 0 }.groupBy { it.bookId }.values.all { rows -> rows.map { it.automaticNumber }.distinct().size == rows.size }) { "BACKUP_CHAPTER_NUMBER" }
        a.chapters.forEach { c ->
            require(c.automaticNumber >= 0 && (c.titleLocked || c.automaticNumber > 0) && c.automaticNumber <= (books[c.bookId]?.chapterNumberCounter ?: 0)) { "BACKUP_CHAPTER_NUMBER" }
            val v = requireNotNull(volumes[c.volumeId]) { "BACKUP_VOLUME" }
            require(v.bookId == c.bookId && books.containsKey(c.bookId)) { "BACKUP_CROSS_BOOK" }
            require(v.deletedAt == null || c.deletedAt != null) { "BACKUP_DELETION" }
            require(TitleValidator.checkSectionTitle(c.title).error == null && c.position >= 0) { "BACKUP_CHAPTER" }
            dates(c.createdAt, c.updatedAt); deletion(c.deletedAt, c.batch, c.bookId)
        }
        a.revisions.forEach { r ->
            require(chapters.containsKey(r.chapterId)) { "BACKUP_REVISION" }
            require(TitleValidator.checkSectionTitle(r.title).error == null) { "BACKUP_TITLE" }
            require(r.source in setOf("AUTO", "MANUAL", "BEFORE_RESTORE", "BEFORE_CHANGE", "IMPORTED")) { "BACKUP_SOURCE" }
            Instant.parse(r.createdAt)
        }
        a.planning.forEach { p ->
            PlanningRules.validate(p); Instant.parse(p.updatedAt)
            require((p.bookId.isEmpty() && p.kind=="INSPIRATION") || books.containsKey(p.bookId)) { "BACKUP_BOOK" }
            p.chapterId?.let { require(chapters[it]?.bookId == p.bookId) { "BACKUP_CROSS_BOOK" } }
            p.volumeId?.let { require(volumes[it]?.bookId == p.bookId) { "BACKUP_CROSS_BOOK" } }
        }
        a.positions.forEach { p ->
            require(chapters.containsKey(p.chapterId) && p.start >= 0 && p.end >= 0 && p.scroll >= 0) { "BACKUP_POSITION" }
        }
    }
}
