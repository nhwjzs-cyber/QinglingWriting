package com.qingling.writing.core.files

import com.qingling.writing.core.model.ExportSnapshot
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Minimal macro-free OOXML with no external links or embedded assets. */
object DocxWriter {
    const val MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    const val MAX_PARAGRAPHS = 100_000
    fun write(snapshot: ExportSnapshot,book: Boolean,volumes: Boolean,chapters: Boolean): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            fun part(name: String,text: String) { zip.putNextEntry(ZipEntry(name));zip.write(text.toByteArray(Charsets.UTF_8));zip.closeEntry() }
            part("[Content_Types].xml","""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/></Types>""")
            part("_rels/.rels","""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""")
            part("word/_rels/document.xml.rels","""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rIdStyles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            part("word/styles.xml","""<?xml version="1.0" encoding="UTF-8"?><w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri" w:eastAsia="宋体"/><w:sz w:val="22"/><w:lang w:val="zh-CN" w:eastAsia="zh-CN"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after="0" w:line="360" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style><w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:pPr><w:keepNext/><w:outlineLvl w:val="0"/></w:pPr><w:rPr><w:b/><w:sz w:val="36"/></w:rPr></w:style><w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:basedOn w:val="Normal"/><w:pPr><w:keepNext/><w:outlineLvl w:val="1"/></w:pPr><w:rPr><w:b/><w:sz w:val="30"/></w:rPr></w:style><w:style w:type="paragraph" w:styleId="Heading3"><w:name w:val="heading 3"/><w:basedOn w:val="Normal"/><w:pPr><w:keepNext/><w:outlineLvl w:val="2"/></w:pPr><w:rPr><w:b/><w:sz w:val="26"/></w:rPr></w:style></w:styles>""")
            zip.putNextEntry(ZipEntry("word/document.xml"))
            val writer = OutputStreamWriter(zip,Charsets.UTF_8)
            writer.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>""")
            var paragraphs = 0
            fun escaped(text: String) {
                var i = 0
                while (i < text.length) {
                    val c = text[i]
                    when {
                        c.isHighSurrogate() -> { require(i+1 < text.length && text[i+1].isLowSurrogate()) { "DOCX 无法表示异常字符，请使用 TXT 导出" };writer.write(text.substring(i,i+2));i++ }
                        c.isLowSurrogate() || c.code < 32 || c == '\uFFFE' || c == '\uFFFF' -> error("DOCX 无法表示异常控制字符，请使用 TXT 导出")
                        c == '&' -> writer.write("&amp;")
                        c == '<' -> writer.write("&lt;")
                        c == '>' -> writer.write("&gt;")
                        else -> writer.write(c.code)
                    };i++
                }
            }
            fun paragraph(text: String,style: String? = null) {
                require(++paragraphs <= MAX_PARAGRAPHS) { "DOCX 最多支持 10 万段，请按卷或选章导出" }
                writer.write("<w:p>")
                if(style != null) writer.write("<w:pPr><w:pStyle w:val=\"$style\"/></w:pPr>")
                writer.write("<w:r>")
                text.splitToSequence('\t').forEachIndexed { index,fragment ->
                    if(index > 0) writer.write("<w:tab/>")
                    writer.write("<w:t xml:space=\"preserve\">");escaped(fragment);writer.write("</w:t>")
                }
                writer.write("</w:r></w:p>")
            }
            if(book) paragraph(snapshot.bookTitle,"Heading1")
            snapshot.volumes.forEach { volume ->
                if(volumes) paragraph(volume.title,"Heading2")
                volume.chapters.forEach { chapter ->
                    if(chapters) paragraph(chapter.title,"Heading3")
                    chapter.body.lineSequence().forEach { paragraph(it) }
                }
            }
            writer.write("""<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134" w:header="720" w:footer="720" w:gutter="0"/></w:sectPr></w:body></w:document>""")
            writer.flush();zip.closeEntry()
        }
        return bytes.toByteArray()
    }
}
