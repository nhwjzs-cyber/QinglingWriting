package com.qingling.writing.data
import android.util.AtomicFile
import com.qingling.writing.core.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
/** Mirrored, checksummed snapshots. Tombstones stop an older replica reviving discarded text. */
class RecoveryDraftStore(private val directory:File) {
    private val mirror=File(directory,"mirror")
    private val damaged=mutableSetOf<String>()
    private val generations=mutableMapOf<String,Long>()
    private val _problem=MutableStateFlow<String?>(null)
    val problem=_problem.asStateFlow()
    private val _entries=MutableStateFlow(loadAll())
    val entries=_entries.asStateFlow()
    private fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it.toInt() and 255)}
    private fun filename(key:String)=hash(key.toByteArray(Charsets.UTF_8))+".json"
    private fun version(j:JSONObject)=j.optLong("_generation",j.optLong("updated"))
    private fun warning(extra:String?=null){_problem.value=extra ?: if(damaged.isNotEmpty())"保护副本不完整或不一致；可读取的副本已保留，请先导出备份并检查存储。" else null}
    private fun decode(file:File):JSONObject {
        val outer=JSONObject(String(AtomicFile(file).readFully(),Charsets.UTF_8))
        val record=if(outer.has("_qlFormat")){
            check(outer.getInt("_qlFormat")==1)
            val payload=outer.getString("payload")
            check(hash(payload.toByteArray(Charsets.UTF_8))==outer.getString("sha256"))
            JSONObject(payload)
        }else outer
        val key=record.getString("key");check(key.isNotBlank() && filename(key)==file.name)
        if(!record.optBoolean("_deleted")){
            check(record.optString("kind") in setOf("editor","editorConflict","quick","planning"))
            record.getString("text")
            if(record.optString("kind") in setOf("editor","editorConflict")){
                record.getString("chapterId");record.getString("title");record.getLong("seq");record.getLong("base")
            }
            if(record.optString("kind")=="planning"){
                record.getString("entryId");record.getString("bookId");record.getString("entryKind");record.getString("title");record.getLong("version")
            }
        }
        return record
    }
    private fun loadAll():List<JSONObject> {
        val names=listOf(directory,mirror).flatMap{dir->dir.listFiles().orEmpty().filter{it.isFile}.map{it.name.removeSuffix(".bak").removeSuffix(".new")}}.filter{it.endsWith(".json")}.distinct()
        val loaded=names.mapNotNull load@{name->
            val candidates=listOf(File(directory,name),File(mirror,name)).mapNotNull{file->
                if(!file.exists() && !File(file.path+".bak").exists() && !File(file.path+".new").exists())null
                else try{decode(file)}catch(_:Exception){damaged.add(file.absolutePath);null}
            }
            val latest=candidates.maxByOrNull{version(it)} ?: return@load null
            if(candidates.any{version(it)!=version(latest)})damaged.add(File(directory,name).absolutePath)
            generations[latest.getString("key")]=version(latest)
            latest.takeUnless{it.optBoolean("_deleted")}
        }.sortedByDescending{it.optLong("updated")}
        warning();return loaded
    }
    @Synchronized fun read(key:String):JSONObject?=_entries.value.firstOrNull{it.optString("key")==key}?.let{JSONObject(it.toString())}
    private fun store(key:String,value:JSONObject,deleted:Boolean):Boolean {
        val gen=maxOf(System.currentTimeMillis(),(generations[key] ?: 0L)+1)
        val j=JSONObject(value.toString()).put("key",key).put("updated",gen).put("_generation",gen)
        if(deleted)j.put("_deleted",true) else j.remove("_deleted")
        val payload=j.toString()
        val bytes=JSONObject().put("_qlFormat",1).put("payload",payload).put("sha256",hash(payload.toByteArray(Charsets.UTF_8))).toString().toByteArray(Charsets.UTF_8)
        var successes=0
        for(dir in listOf(mirror,directory)){
            val file=File(dir,filename(key))
            try{DurableFiles.write(file,bytes);successes++;damaged.remove(file.absolutePath)}
            catch(_:Exception){damaged.add(file.absolutePath)}
        }
        if(successes>0){
            generations[key]=gen
            _entries.value=(if(deleted)emptyList() else listOf(j))+_entries.value.filterNot{it.optString("key")==key}
        }
        warning(when(successes){0->"保护草稿暂未写入，请先处理保存提示，勿强行退出";1->"一份保护副本暂未写入，另一份已保留，请检查存储并导出备份。";else->null})
        return successes>0
    }
    @Synchronized fun put(key:String,value:JSONObject)=store(key,value,false)
    fun upgradeLegacyCopies(){
        val legacy=entries.value.filterNot{it.has("_generation")}
        for(record in legacy)synchronized(this){
            val key=record.getString("key")
            val current=read(key)
            // Do not replace input or a discard that happened after this scan started.
            if(current!=null && !current.has("_generation"))put(key,current)
        }
    }
    @Synchronized fun remove(key:String):Boolean=store(key,JSONObject(),true)
    @Synchronized fun removeIfUnchanged(key:String,expected:JSONObject):Boolean {
        val current=read(key) ?: return true
        if(version(current)!=version(expected))return false
        return remove(key)
    }
    fun putEditor(d:ChapterDraft,base:Long,baseTitle:String?=null)=put("editor:"+d.chapterId,JSONObject().put("kind","editor").put("chapterId",d.chapterId).put("title",d.title).put("text",d.body).put("seq",d.editSeq).put("base",base).put("titleEdited",d.titleEdited).put("count",d.countAsWriting).also{j->if(baseTitle!=null)j.put("baseTitle",baseTitle);d.inputMetrics?.let{j.put("metricBase",it.baseSeq).put("inserted",it.inserted).put("activeMs",it.activeMillis)}})
    fun editor(id:String):ChapterDraft?=read("editor:"+id)?.takeIf{it.optString("kind")=="editor"}?.let {j->
        ChapterDraft(id,j.getString("title"),j.getString("text"),j.getLong("seq"),j.optBoolean("count"),if(j.has("metricBase"))InputMetrics(j.getLong("metricBase"),j.getLong("inserted"),j.getLong("activeMs"))else null,j.optBoolean("titleEdited"))
    }
    @Synchronized fun editorCommitted(id:String,seq:Long,committedTitle:String?=null){val key="editor:"+id;val j=read(key)?:return
        if(j.optLong("seq")<=seq)remove(key) else if(j.optString("kind")=="editor") {j.put("base",seq);if(committedTitle!=null)j.put("baseTitle",committedTitle);put(key,j)}
    }
    @Synchronized fun preserveEditorConflict(id:String,record:JSONObject):Boolean {
        val key="editor:"+id
        val conflict="conflict:"+id+":"+record.optLong("seq")+":"+record.optLong("updated")
        if(!put(conflict,JSONObject(record.toString()).put("kind","editorConflict")))return false
        removeIfUnchanged(key,record)
        return true
    }
    fun putPlanning(e:PlanningEntry)=put("planning:"+e.id,JSONObject().put("kind","planning").put("entryId",e.id).put("bookId",e.bookId).put("chapterId",e.chapterId.orEmpty()).put("volumeId",e.volumeId.orEmpty()).put("entryKind",e.kind).put("title",e.title).put("text",e.body).put("version",e.version))
    @Synchronized fun planningCommitted(e:PlanningEntry){val key="planning:"+e.id;val j=read(key)?:return
        if(j.optString("text")==e.body && j.optString("title")==e.title)remove(key)
    }
}
