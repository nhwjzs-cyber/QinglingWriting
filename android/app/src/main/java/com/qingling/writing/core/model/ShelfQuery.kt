package com.qingling.writing.core.model
import java.text.Collator
import java.util.Locale

enum class ShelfScope(val label: String) { ACTIVE("写作中"),ARCHIVED("已归档"),ALL("全部") }
enum class ShelfSort(val label: String) { UPDATED("最近修改"),CREATED("最近创建"),TITLE("作品名称"),WORDS("字数最多") }
fun queryShelf(books: List<BookSummary>,query: String,scope: ShelfScope,sort: ShelfSort): List<BookSummary> {
    val words=query.trim().split(Regex("\\s+")).filter{it.isNotEmpty()}
    val collator=Collator.getInstance(Locale.CHINA)
    val order=when(sort) {
        ShelfSort.UPDATED -> compareByDescending<BookSummary>{it.updatedAt}
        ShelfSort.CREATED -> compareByDescending<BookSummary>{it.createdAt}
        ShelfSort.TITLE -> Comparator<BookSummary>{a,b->collator.compare(a.title,b.title)}
        ShelfSort.WORDS -> compareByDescending<BookSummary>{it.wordCount}
    }
    return books.filter { b->(scope==ShelfScope.ALL || b.archived==(scope==ShelfScope.ARCHIVED)) && words.all {b.title.contains(it,true)||b.author.contains(it,true)} }
        .sortedWith(compareByDescending<BookSummary>{it.pinned}.then(order).thenBy{it.id})
}
