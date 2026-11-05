package com.qingling.writing.core.files
import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ImportedFont(val id: String,val name: String,val bytes: Long)
class ImportedFontStore(private val context: Context,private val directory: File=File(context.filesDir,"imported-fonts")) {
    private val entries=MutableStateFlow<List<ImportedFont>>(emptyList())
    val fonts=entries.asStateFlow()
    private val cache=object: LinkedHashMap<String,Typeface>(4,.75f,true) {override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String,Typeface>?)=size>3}
    private fun file(id: String): File {require(id.matches(Regex("[a-f0-9]{64}")));return File(directory,"$id.font")}
    suspend fun refresh()=withContext(Dispatchers.IO) {
        entries.value=directory.listFiles().orEmpty().filter {it.name.matches(Regex("[a-f0-9]{64}\\.font"))}.map {f->
            val id=f.name.removeSuffix(".font");val name=File(directory,"$id.name").takeIf {it.length()<=1024}?.let {runCatching {it.readText()}.getOrNull()} ?: "本机字体"
            ImportedFont(id,name.take(150),f.length())
        }.sortedBy {it.name}
    }
    suspend fun import(uri: Uri): String=withContext(Dispatchers.IO) {
        directory.mkdirs()
        val resolver=context.contentResolver
        val name=resolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {if(it.moveToFirst()) it.getString(0) else null} ?: "导入字体"
        val bytes=resolver.openInputStream(uri)?.use {input->
            val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true) {val count=input.read(buffer);if(count<0)break;require(output.size()+count<=FontFileValidator.MAX_BYTES){"字体不能超过 20 MB"};output.write(buffer,0,count)}
            output.toByteArray()
        } ?: error("无法读取字体文件")
        FontFileValidator.validate(bytes)
        val id=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it.toInt() and 255)}
        val target=file(id)
        if(!target.exists()) {
            require(directory.listFiles().orEmpty().count {it.extension=="font"}<10) {"最多保留 10 款字体，请先移除不用的字体"}
            val temp=File(directory,"pending-${UUID.randomUUID()}")
            try {
                java.io.FileOutputStream(temp).use {it.write(bytes);it.fd.sync()}
                val face=Typeface.createFromFile(temp)
                check(temp.renameTo(target)) {"字体保存失败，请检查存储空间"}
                synchronized(cache){cache[id]=face}
            } finally {temp.delete()}
        }
        File(directory,"$id.name").writeText(name.replace('\n',' ').replace('\r',' ').take(150))
        refresh();id
    }
    suspend fun load(id: String): Typeface?=withContext(Dispatchers.IO) {
        if(id.isBlank()) return@withContext null
        synchronized(cache){cache[id]}?.let {return@withContext it}
        runCatching {val f=file(id);check(f.exists());Typeface.createFromFile(f).also {synchronized(cache){cache[id]=it}}}.getOrNull()
    }
    suspend fun remove(id: String)=withContext(Dispatchers.IO) {
        val f=file(id);check(!f.exists() || f.delete()) {"字体移除失败"}
        File(directory,"$id.name").delete();synchronized(cache){cache.remove(id)};refresh()
    }
}
