package com.qingling.writing
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.ViewModelStore
import com.qingling.writing.core.database.WritingDatabase
import com.qingling.writing.core.database.entities.SpaceEntity
import com.qingling.writing.core.model.*
import com.qingling.writing.data.*
import com.qingling.writing.feature.planning.PlanningViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
class Usability044DataTest {
    @Test fun deletionChecksVersionUndoPreservesContentAndDoesNotDeleteScopedEntry()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),WritingDatabase::class.java).build()
        try {
            var seq=0;val ids={"ux044-"+(seq++)};val now={"2026-11-04T00:00:00Z"}
            db.spaceDao().insert(SpaceEntity(GuestSpace.ID,GuestSpace.KIND_GUEST,null))
            val w=RoomWritingRepository(db,ids,now);val o=RoomOutlineRepository(db,ids,now);val repo=PlanningRepository(db,o,w,ids,now)
            val c=w.createBook(GuestSpace.ID,"测试书","","")
            val a=repo.save(repo.scoped(c.bookId,PlanningKind.CHARACTER).copy(title="人物",body="完整简介😀"))
            val b=repo.save(a.copy(body="修改后简介😀"))
            try{repo.deleteUserEntry(a);fail("stale delete")}catch(_:PlanningConflict){}
            assertEquals(b,repo.load(b.id))
            val removed=repo.deleteUserEntry(b);assertNull(repo.load(b.id))
            val restored=repo.undoDeleteUserEntry(removed);assertEquals(b.body,restored.body);assertEquals(b.title,restored.title);assertTrue(restored.version>b.version)
            try{repo.save(a.copy(body="过期覆盖"));fail("stale after undo")}catch(_:PlanningConflict){}
            try{repo.undoDeleteUserEntry(removed);fail("duplicate undo")}catch(_:IllegalStateException){}
            val outline=repo.save(repo.scoped(c.bookId,PlanningKind.CHAPTER_OUTLINE,chapterId=c.id).copy(body="细纲"))
            try{repo.deleteUserEntry(outline);fail("scoped delete")}catch(_:IllegalArgumentException){}
            assertNotNull(repo.load(outline.id));assertEquals(1,o.observeOutline(c.bookId).first().single().chapters.size)
        }finally{db.close()}
    }
    @Test fun failedScopedSaveKeepsDraftUntilExplicitDiscardAndPreservesStoredVersion()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),WritingDatabase::class.java).build()
        val store=ViewModelStore()
        try {
            var seq=0;val ids={"conflict044-"+(seq++)};val now={"2026-11-04T00:00:00Z"}
            db.spaceDao().insert(SpaceEntity(GuestSpace.ID,GuestSpace.KIND_GUEST,null))
            val w=RoomWritingRepository(db,ids,now);val o=RoomOutlineRepository(db,ids,now);val repo=PlanningRepository(db,o,w,ids,now)
            val c=w.createBook(GuestSpace.ID,"测试书","","")
            val initial=repo.save(repo.scoped(c.bookId,PlanningKind.CHAPTER_OUTLINE,chapterId=c.id).copy(body="原细纲"))
            withContext(Dispatchers.Main) {
                val model=PlanningViewModel(repo,o,c.bookId);store.put("scope",model)
                model.edit(initial);model.update(body="本次未保存的细纲")
                repo.save(initial.copy(body="其他位置已保存的细纲"))
                assertFalse(model.finishEdit());assertEquals("本次未保存的细纲",model.draft.value!!.entry.body);assertNotNull(model.error.value)
                model.discard();assertNull(model.draft.value)
                assertEquals("其他位置已保存的细纲",repo.load(initial.id)!!.body)
            }
        }finally{withContext(Dispatchers.Main){store.clear()};db.close()}
    }
}
