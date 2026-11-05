package com.qingling.writing

import com.qingling.writing.core.files.DocxWriter
import com.qingling.writing.core.files.ExportFileStore
import com.qingling.writing.core.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.*
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Node

class DocxWriterTest {
    @get:Rule val temp = TemporaryFolder()
    private fun snapshot(body: String) = ExportSnapshot("书<&😀",listOf(ExportVolume("v","卷",listOf(
        ExportChapter("a","v","章甲",body),ExportChapter("b","v","空章","")))),1)
    private fun entries(bytes: ByteArray): Map<String,ByteArray> {
        val map = linkedMapOf<String,ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip -> while(true) {
            val e = zip.nextEntry ?: break;assertFalse(map.containsKey(e.name));map[e.name] = zip.readBytes()
        } };return map
    }
    private fun xml(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true;setFeature("http://apache.org/xml/features/disallow-doctype-decl",true)
    }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    private fun lines(bytes: ByteArray): List<String> {
        val p = xml(entries(bytes).getValue("word/document.xml")).getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main","p")
        fun text(n: Node): String = when(n.localName) {
            "t" -> n.textContent;"tab" -> "\t"
            else -> (0 until n.childNodes.length).joinToString("") { text(n.childNodes.item(it)) }
        }
        return (0 until p.length).map { text(p.item(it)) }
    }
    @Test fun docxHasValidPartsRelationshipsAndExactTextOrder() {
        val output = DocxWriter.write(snapshot("　　甲&<😀\r\n\n乙\t尾  \n"),true,true,true)
        val parts = entries(output)
        assertEquals(setOf("[Content_Types].xml","_rels/.rels","word/_rels/document.xml.rels","word/styles.xml","word/document.xml"),parts.keys)
        parts.values.forEach { xml(it) }
        assertFalse(parts.values.any { it.toString(Charsets.UTF_8).contains("TargetMode=\"External\"") })
        assertEquals(listOf("书<&😀","卷","章甲","　　甲&<😀","","乙\t尾  ","","空章",""),lines(output))
    }
    @Test fun headingOptionsDoNotIntroduceUnrequestedTitles() {
        assertEquals(listOf("正文",""),lines(DocxWriter.write(snapshot("正文"),false,false,false)))
    }
    @Test fun invalidXmlCharactersAreRejectedInsteadOfSilentlyDroppingText() {
        listOf("a\u0000b","a\uFFFFb","a\uD800b").forEach { bad ->
            assertThrows(Exception::class.java) { DocxWriter.write(snapshot(bad),false,false,false) }
        }
    }
    @Test fun paragraphLimitRejectsPathologicalInput() {
        assertThrows(IllegalArgumentException::class.java) { DocxWriter.write(snapshot("\n".repeat(DocxWriter.MAX_PARAGRAPHS)),false,false,false) }
    }
    @Test fun docxCacheCopiesFrozenBytesAndRejectsUnexpectedExtensionsAndTraversal() {
        val store = ExportFileStore(temp.newFolder())
        val bytes = DocxWriter.write(snapshot("测试😀"),true,true,true)
        val staged = store.stage("../作品",bytes,"docx")
        assertTrue(staged.fileName.endsWith(".docx"));assertEquals(staged.file,store.resolve(staged.token))
        val copied = ByteArrayOutputStream();store.copyTo(staged.token) { copied };assertArrayEquals(bytes,copied.toByteArray())
        assertThrows(IllegalArgumentException::class.java) { store.resolve("../file.docx") }
        assertThrows(IllegalArgumentException::class.java) { store.stage("书",bytes,"exe") }
        assertThrows(IllegalArgumentException::class.java) { store.preview(staged.token) }
    }
}
