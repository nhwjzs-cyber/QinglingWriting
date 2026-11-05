package com.qingling.writing.core.model

/**
 * 书架条目(contracts/WritingContracts.kt 的 BookSummary,附加书架展示所需字段)。
 * 身份一律 UUID;时间字符串为 UTC RFC3339(contracts 头注);书架按最近编辑时间倒序(docs/01 §4),
 * 排序由数据层 SQL 保证。
 */
data class BookSummary(
    val id: String,
    val title: String,
    /** 全书存活章正文按 Unicode 码点(不计空白)的合计字数(docs/01 §7 口径)。 */
    val wordCount: Long,
    val updatedAt: String,
    /** 存活章数量,用于删除范围提示。 */
    val chapterCount: Int,
    /** 「继续写作」入口:本书最近编辑的存活章 id;无章时为 null。 */
    val continueChapterId: String?,
    val completedCount: Int = 0,
    val author: String = "",
    val createdAt: String = "",
    val pinned: Boolean = false,
    val archived: Boolean = false,
) {
    /**
     * 演示数据标记:仅 debug 种子数据使用「演示·」前缀(docs/05 §5:演示数据不进 release),
     * release 书架不会出现该前缀的书籍。据前缀显示「演示数据」角标。
     */
    val isDemo: Boolean get() = title.startsWith(DEMO_TITLE_PREFIX)

    companion object {
        const val DEMO_TITLE_PREFIX = "演示·"
    }
}
