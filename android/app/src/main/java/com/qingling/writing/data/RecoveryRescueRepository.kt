package com.qingling.writing.data

import androidx.room.withTransaction
import com.qingling.writing.core.database.WritingDatabase
import com.qingling.writing.core.model.*
import com.qingling.writing.domain.repository.WritingRepository
import org.json.JSONObject

class RecoveryRescueRepository(private val db:WritingDatabase,private val writing:WritingRepository,private val drafts:RecoveryDraftStore){
    suspend fun restoreAsNewBook(expected:JSONObject):ChapterDocument {
        val key=expected.getString("key")
        val current=drafts.read(key)
        check(current!=null && current.toString()==expected.toString()){ "保护草稿已更新，请重新打开后恢复" }
        check(current.optString("kind") in setOf("editor","editorConflict")){ "请选择正文保护副本" }
        val saved=db.withTransaction {
            db.spaceDao().insert(com.qingling.writing.core.database.entities.SpaceEntity(GuestSpace.ID,GuestSpace.KIND_GUEST,null))
            val chapter=writing.createBook(GuestSpace.ID,"找回的稿件","","")
            val title=current.optString("title").ifBlank{"恢复草稿"}
            check(writing.saveDraft(ChapterDraft(chapter.id,title,current.getString("text"),chapter.editSeq+1,titleEdited=true)) is SaveResult.Committed){ "尚未保存成功，保护副本保留" }
            writing.readChapter(chapter.id)
        }
        // A newer snapshot created during recovery must not be discarded.
        drafts.removeIfUnchanged(key,expected)
        return saved
    }
}
