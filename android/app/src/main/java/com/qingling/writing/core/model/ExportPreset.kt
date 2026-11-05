package com.qingling.writing.core.model

enum class ExportScope { BOOK,VOLUME,CHAPTER }
data class ExportPreset(val scope:ExportScope=ExportScope.BOOK,val id:String=""){
    val label:String get()=when(scope){ExportScope.BOOK->"全书";ExportScope.VOLUME->"本卷";ExportScope.CHAPTER->"本章"}
    fun select(volumes:List<OutlineVolume>):Set<String> = when(scope){
        ExportScope.BOOK->volumes.flatMap{it.chapters}.map{it.id}.toSet()
        ExportScope.VOLUME->volumes.firstOrNull{it.id==id}?.chapters?.map{it.id}?.toSet().orEmpty()
        ExportScope.CHAPTER->volumes.flatMap{it.chapters}.filter{it.id==id}.map{it.id}.toSet()
    }
}
data class ExportPreferences(val format:ExportFormat=ExportFormat.TXT,val book:Boolean=true,val volumes:Boolean=true,val chapters:Boolean=true)
