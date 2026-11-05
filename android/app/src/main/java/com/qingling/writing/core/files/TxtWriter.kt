package com.qingling.writing.core.files

import com.qingling.writing.core.model.ExportSnapshot

/**
 * TXT 合并导出写入器(docs/01 §5 / fixtures/export-expected.txt)。
 *
 * 规则:
 * - UTF-8、无 BOM、LF 换行(正文中 \r\n → \n,不 trim、不改标点)
 * - 块连接规则:所有块(书名/卷名/章名+正文)用 "\n"(空行)分隔,末尾追加一个最终 "\n"
 * - "最后一章本来有一个尾 LF" → 连接后末尾有两个 LF,这是确定性规则
 * - 正文保留原样:前导空格、空章、原有尾 LF 均不修改
 */
internal object TxtWriter {

    fun write(
        snapshot: ExportSnapshot,
        includeBookTitle: Boolean,
        includeVolumeTitles: Boolean,
        includeChapterTitles: Boolean,
    ): ByteArray {
        val blocks = mutableListOf<String>()

        if (includeBookTitle) {
            blocks.add(snapshot.bookTitle + "\n")
        }

        for (volume in snapshot.volumes) {
            if (includeVolumeTitles) {
                blocks.add(volume.title + "\n")
            }
            for (chapter in volume.chapters) {
                // 标题和正文是同一个块(join 分隔符不插入标题与正文之间)。
                var block = ""
                if (includeChapterTitles) {
                    block += chapter.title + "\n"
                }
                val body = normalizeLineEndings(chapter.body)
                block += if (body.endsWith("\n")) body else body + "\n"
                blocks.add(block)
            }
        }

        val content = blocks.joinToString("\n") + "\n"
        return content.toByteArray(Charsets.UTF_8)
    }

    /** \r\n → \n;不 trim、不删除空行。 */
    private fun normalizeLineEndings(body: String): String =
        body.replace("\r\n", "\n")
}
