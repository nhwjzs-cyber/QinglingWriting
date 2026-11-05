package com.qingling.writing.domain.validation

/**
 * 标题校验(docs/01 §3:书名去两端空白后不能为空,最多 80 个 Unicode 码点;
 * 章节标题最长 120 码点;local-schema.sql 的 CHECK:trim 后非空、不含 CR/LF)。
 * 卷名规格文档未单列,与章节标题同用 120 码点上限(在 M1 验收记录中说明)。
 *
 * 纯 JVM 逻辑,不依赖 Android;UI 与数据层共用同一实现,防止两套口径。
 */
object TitleValidator {

    const val BOOK_MAX_CODE_POINTS = 80
    const val SECTION_MAX_CODE_POINTS = 120

    /** 校验结果:[error] 为 null 时 [value] 即清洗后的合法标题(trim 结果)。 */
    data class Result(val value: String, val error: TitleError?)

    fun checkBookTitle(raw: String): Result = check(raw, BOOK_MAX_CODE_POINTS)

    fun checkSectionTitle(raw: String): Result = check(raw, SECTION_MAX_CODE_POINTS)

    private fun check(raw: String, maxCodePoints: Int): Result {
        val trimmed = raw.trim()
        val error = when {
            trimmed.isEmpty() -> TitleError.EMPTY
            trimmed.any { it == '\n' || it == '\r' } -> TitleError.CONTAINS_LINE_BREAK
            trimmed.codePointCount(0, trimmed.length) > maxCodePoints -> TitleError.TOO_LONG
            else -> null
        }
        return Result(trimmed, error)
    }
}

enum class TitleError {
    /** trim 后为空(local-schema.sql: length(trim(title)) > 0)。 */
    EMPTY,

    /** 含换行/回车(local-schema.sql: instr(title, char(10)) = 0 等)。 */
    CONTAINS_LINE_BREAK,

    /** 超出码点上限(docs/01 §3)。 */
    TOO_LONG,
}
