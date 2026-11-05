package com.qingling.writing.core.model

import java.util.Base64

/** Stored in INSPIRATION text; database v10 and portable backup v7 remain unchanged. */
data class QuickNote(val text: String, val chapterId: String = "", val sourceTitle: String = "",
    val offset: Int = 0, val foreshadow: Boolean = false, val resolved: Boolean = false,
    val deleted: Boolean = false, val capturedAt:String = "",
    val resolvedChapterId:String = "", val resolvedSourceTitle:String = "", val resolvedAt:String = "",
    val sourceQuote:String="",val sourceBefore:String="",val sourceAfter:String="",val sourceEnd:Int=0,val sourceKind:String="") {
    fun clearResolution() = copy(resolved=false, resolvedChapterId="", resolvedSourceTitle="", resolvedAt="")
    fun resolve(chapter:String="", title:String="", at:String=java.time.Instant.now().toString()) =
        copy(resolved=true, resolvedChapterId=chapter, resolvedSourceTitle=title, resolvedAt=at)
    fun encode(): String = HEADER + listOf(chapterId, sourceTitle, offset.toString(),
        foreshadow.toString(), resolved.toString(), deleted.toString(),capturedAt,resolvedChapterId,resolvedSourceTitle,resolvedAt,sourceQuote,sourceBefore,sourceAfter,sourceEnd.toString(),sourceKind).joinToString("|") {
        Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray(Charsets.UTF_8))
    } + "\n" + text
    companion object {
        private const val HEADER = "〔轻灵速记·1〕"
        fun decode(body: String): QuickNote {
            if (!body.startsWith(HEADER)) return QuickNote(body)
            return try {
                val line = body.indexOf('\n'); require(line >= 0)
                val fields = body.substring(HEADER.length, line).split('|').map {
                    String(Base64.getUrlDecoder().decode(it), Charsets.UTF_8)
                }
                require(fields.size in 6..15)
                QuickNote(body.substring(line + 1), fields[0], fields[1], fields[2].toInt().coerceAtLeast(0),
                    fields[3].toBooleanStrict(), fields[4].toBooleanStrict(), fields[5].toBooleanStrict(),fields.getOrElse(6){""},
                    fields.getOrElse(7){""},fields.getOrElse(8){""},fields.getOrElse(9){""},fields.getOrElse(10){""},fields.getOrElse(11){""},fields.getOrElse(12){""},fields.getOrElse(13){"0"}.toInt().coerceAtLeast(0),fields.getOrElse(14){""})
            } catch (_: Exception) { QuickNote(body) } // Never discard unrecognized or damaged text.
        }
        fun title(text: String): String {
            val line=text.lineSequence().firstOrNull {it.isNotBlank()}?.trim()?.replace('\r',' ') ?: return "未命名便签"
            val points=line.codePoints().limit(60).toArray();return String(points,0,points.size)
        }
    }
}
