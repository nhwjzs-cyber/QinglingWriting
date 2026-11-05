package com.qingling.writing.core.model

/** The dedicated editor title is the source of chapter names; body text is independent. */
object ChapterNaming {
    const val UNNAMED = "未命名章节"
    const val MAX_FIRST_LINE_POINTS = 20 // Kept for reading historical naming documentation.
    fun placeholder(number: Long):String { require(number>0);return UNNAMED }
    fun editorText(title:String):String = if(title==UNNAMED) "" else title
    fun fromEditor(text:String):String = text.trim().ifBlank { UNNAMED }
    /** Legacy helper retained for source compatibility; never used for persisted body saves. */
    fun title(body:String,number:Long):String {
        require(number>0)
        val line=body.substringBefore('\n').trim()
        return if(line.isNotEmpty() && line.codePointCount(0,line.length)<=MAX_FIRST_LINE_POINTS && '\r' !in line) line else placeholder(number)
    }
    fun isPlaceholder(title:String,number:Long,locked:Boolean) = title==UNNAMED || (!locked && number>0 && title=="未命名章节 "+number)
}
