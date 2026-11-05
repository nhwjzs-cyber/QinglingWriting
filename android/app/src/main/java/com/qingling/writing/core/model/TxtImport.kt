package com.qingling.writing.core.model

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

data class ImportedChapter(val title: String, val body: String)
data class DecodedTxt(val text: String, val encoding: String)

object TxtImport {
    const val MAX_BYTES = 5 * 1024 * 1024
    const val MAX_CHAPTERS = 1000
    fun decode(bytes: ByteArray, encoding: String = "AUTO"): DecodedTxt {
        require(bytes.size <= MAX_BYTES) { "文件总大小不能超过 5 MB" }
        fun strict(name: String, skip: Int = 0): String = java.nio.charset.Charset.forName(name).newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes, skip, bytes.size - skip)).toString().removePrefix("\uFEFF")
        val selected = if (encoding != "AUTO") encoding else when {
            bytes.take(3) == listOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) -> "UTF-8"
            bytes.take(2) == listOf(0xFF.toByte(), 0xFE.toByte()) -> "UTF-16LE"
            bytes.take(2) == listOf(0xFE.toByte(), 0xFF.toByte()) -> "UTF-16BE"
            else -> try { strict("UTF-8"); "UTF-8" } catch (_: java.nio.charset.CharacterCodingException) { "GB18030" }
        }
        val text = strict(selected).replace("\r\n", "\n").replace('\r', '\n')
        require('\u0000' !in text) { "文件包含异常空字符，请选择正确编码" }
        require(text.isNotBlank()) { "文件没有可导入的正文" }
        return DecodedTxt(text, selected)
    }

    fun split(text: String, name: String, autoChapters: Boolean, customPattern: String? = null): List<ImportedChapter> {
        require(text.isNotBlank()) { "正文不能为空" }
        if (!autoChapters) return listOf(ImportedChapter(title(name), text))
        val heading = Regex("(?m)^[ \\t　]*第[0-9０-９零〇一二三四五六七八九十百千万两]+[章回节][^\\n]{0,100}$")
        val matches = if (customPattern == null) heading.findAll(text).take(MAX_CHAPTERS + 1).toList()
            else HeadingPattern.matches(text, customPattern)
        require(matches.size <= MAX_CHAPTERS) { "最多支持 1000 章，请拆成多个文件" }
        if (matches.isEmpty()) return listOf(ImportedChapter(title(name), text))
        val result = mutableListOf<ImportedChapter>()
        val prefix = text.substring(0, matches.first().range.first)
        if (prefix.isNotBlank()) result.add(ImportedChapter("前言", prefix))
        matches.forEachIndexed { i, match ->
            val start = match.range.last + 1
            val end = matches.getOrNull(i + 1)?.range?.first ?: text.length
            val originalTitle = match.value.trim()
            val chapterTitle = title(originalTitle, 120)
            val body = text.substring(start, end).removePrefix("\n")
            // Keep the full original heading in the body if it exceeds the title field limit.
            result.add(ImportedChapter(chapterTitle, if (chapterTitle != originalTitle) match.value + "\n" + body else body))
        }
        require(result.size <= MAX_CHAPTERS) { "最多支持 1000 章（含前言）" }
        return result
    }
    fun title(raw: String, maxCodePoints: Int = 80): String {
        val clean = raw.replace('\r', ' ').replace('\n', ' ').trim().ifEmpty { "导入稿件" }
        return clean.substring(0, clean.offsetByCodePoints(0, minOf(maxCodePoints, clean.codePointCount(0, clean.length))))
    }
}
