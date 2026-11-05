package com.qingling.writing.core.model

enum class PlanningKind(val label: String) {
    CHAPTER_OUTLINE("章节细纲"), VOLUME_SUMMARY("分卷概要"), CHARACTER("人物"), PLACE("地点"),
    FACTION("势力"), ITEM("物品"), TERM("术语"), INSPIRATION("灵感便签")
}

data class PlanningEntry(val id: String, val bookId: String, val chapterId: String?, val volumeId: String?,
    val kind: String, val title: String, val body: String, val updatedAt: String, val version: Long = 0)

/** Readable portable character fields; old free-form content remains the introduction. */
data class CharacterCard(val aliases: String = "",val identity: String = "",val introduction: String = "") {
    fun encode(): String {
        require('\n' !in aliases && '\r' !in aliases && '\n' !in identity && '\r' !in identity)
        return "别名：$aliases\n身份：$identity\n\n简介：\n$introduction"
    }
    companion object {
        fun decode(body: String): CharacterCard {
            val lines = body.split('\n',limit = 5)
            if (lines.size == 5 && lines[0].startsWith("别名：") && lines[1].startsWith("身份：") &&
                lines[2].isEmpty() && lines[3] == "简介：")
                return CharacterCard(lines[0].removePrefix("别名："),lines[1].removePrefix("身份："),lines[4])
            return CharacterCard(introduction = body)
        }
    }
}

object PlanningRules {
    const val MAX_BODY = 100_000
    const val MAX_ENCODED_NOTE = 300_000
    const val MAX_NOTE_CONTENT = 50_000
    fun validate(entry: PlanningEntry) {
        require(entry.id.isNotBlank() && entry.id.length <= 128 && (entry.bookId.isNotBlank() || entry.kind==PlanningKind.INSPIRATION.name))
        require(com.qingling.writing.domain.validation.TitleValidator.checkSectionTitle(entry.title).error == null) { "标题不能为空，最多 120 字，不能换行" }
        val quotedNote = if(entry.kind == PlanningKind.INSPIRATION.name && entry.body.startsWith("〔轻灵速记·1〕")) QuickNote.decode(entry.body) else null
        val validLargeQuote = quotedNote != null && quotedNote.sourceQuote.isNotEmpty() &&
            quotedNote.text.length.toLong()+quotedNote.sourceQuote.length <= MAX_NOTE_CONTENT && entry.body.length <= MAX_ENCODED_NOTE
        require(entry.body.length <= MAX_BODY || validLargeQuote) { "单条内容超出容量，请拆成多条保存" }
        val kind = PlanningKind.valueOf(entry.kind)
        require(when(kind) {
            PlanningKind.CHAPTER_OUTLINE -> entry.chapterId != null && entry.volumeId == null
            PlanningKind.VOLUME_SUMMARY -> entry.volumeId != null && entry.chapterId == null
            else -> entry.chapterId == null && entry.volumeId == null
        }) { "条目归属不正确" }
        require(entry.version >= 0)
    }
}
