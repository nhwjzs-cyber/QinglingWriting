package com.qingling.writing.core.model

internal data class OutlineSceneForm(val text:String,val status:SceneStatus,
    private val raw:String?=null,private val originalText:String=text,private val originalStatus:SceneStatus=status){
    fun render():String=if(raw!=null && text==originalText && status==originalStatus)raw
        else "- [${status.marker}] $text"+if(raw?.endsWith('\r')==true)"\r"else""
}

/** Opening/closing a form never normalizes an existing draft. Untouched regions stay verbatim. */
internal data class ChapterOutlineForm(val goal:String,val scenes:List<OutlineSceneForm>,
    private val original:String?=null,private val originalGoal:String=goal,
    private val originalScenes:List<OutlineSceneForm> = scenes,private val goalRaw:String="",private val header:String?=null){
    fun toBody():String {
        if(original!=null && goal==originalGoal && scenes==originalScenes)return original
        val prefix=if(goalRaw.isEmpty())"目标：" else listOf("本章目标：","目标：","目标:").firstOrNull{goalRaw.startsWith(it)}.orEmpty()
        val before=if(original!=null && goal==originalGoal)goalRaw else if(goal.isEmpty())""else prefix+goal
        if(scenes.isEmpty() && header==null)return before
        return before+(if(before.isEmpty()||before.endsWith('\n'))""else"\n\n")+(header?:"分场：")+
            if(scenes.isEmpty())""else"\n"+scenes.joinToString("\n"){it.render()}
    }
    fun cycle(index:Int):ChapterOutlineForm {
        val scene=scenes[index]
        val next=when(scene.status){SceneStatus.TODO->SceneStatus.CURRENT;SceneStatus.CURRENT->SceneStatus.DONE;SceneStatus.DONE->SceneStatus.TODO}
        return copy(scenes=scenes.mapIndexed{i,s->when{ i==index->s.copy(status=next);next==SceneStatus.CURRENT&&s.status==SceneStatus.CURRENT->s.copy(status=SceneStatus.TODO);else->s }})
    }
    companion object {
        private val section=Regex("^[ \\t#]*分场[：:]?[ \\t]*$")
        private val marker=Regex("^[ \\t]*(?:[-*][ \\t]*)?\\[([ xX>])\\][ \\t]?(.*)$")
        fun fromBody(body:String):ChapterOutlineForm {
            val all=body.split('\n')
            val index=all.indexOfFirst{section.matches(it.removeSuffix("\r"))}
            val before=if(index<0)body else all.take(index).joinToString("\n")+if(index>0)"\n"else""
            val prefix=listOf("本章目标：","目标：","目标:").firstOrNull{before.startsWith(it)}.orEmpty()
            val goal=before.removePrefix(prefix)
            val lines=if(index<0)emptyList()else all.drop(index+1)
            val scenes=lines.map{raw->val line=raw.removeSuffix("\r");val marked=marker.matchEntire(line)
                OutlineSceneForm(marked?.groupValues?.get(2)?:line,when(marked?.groupValues?.get(1)?.lowercase()){"x"->SceneStatus.DONE;">"->SceneStatus.CURRENT;else->SceneStatus.TODO},raw)}
            return ChapterOutlineForm(goal,scenes,body,goal,scenes,before,all.getOrNull(index))
        }
    }
}
