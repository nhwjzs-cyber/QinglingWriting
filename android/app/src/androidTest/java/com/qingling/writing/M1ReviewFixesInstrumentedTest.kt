package com.qingling.writing

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qingling.writing.core.database.WritingDatabase
import com.qingling.writing.core.database.entities.SpaceEntity
import com.qingling.writing.core.model.GuestSpace
import com.qingling.writing.data.RoomOutlineRepository
import com.qingling.writing.data.RoomRecycleBinRepository
import com.qingling.writing.data.RoomWritingRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M1 审核修复的针对性仪器测试:
 * 1. P1-1 回收站去重——随卷级联删除的章不再与卷共享批次条目(过滤由 SQL 保证);
 * 2. P1-2 恢复章批次时父卷/父书的祖先链补救(不产生「已恢复却找不到」的孤儿);
 * 3. 跨卷移动后原卷 position 无空洞;
 * 4. 结构性写操作触碰书 updated_at(书架最近编辑倒序准确)。
 */
@RunWith(AndroidJUnit4::class)
class M1ReviewFixesInstrumentedTest {

    private lateinit var context: Context
    private lateinit var db: WritingDatabase
    private lateinit var writing: RoomWritingRepository
    private lateinit var outline: RoomOutlineRepository
    private lateinit var recycleBin: RoomRecycleBinRepository
    private var idCounter: Int = 0
    private var clockTick: Long = 0L

    private fun nextId(): String = "id-${idCounter++}"

    /** 递增时钟:让「结构操作触碰书 updated_at」可断言。 */
    private fun nextTime(): String = java.time.Instant.ofEpochSecond(1_800_000_000L + clockTick++).toString()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, WritingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        writing = RoomWritingRepository(db, ::nextId, ::nextTime)
        outline = RoomOutlineRepository(db, ::nextId, ::nextTime)
        recycleBin = RoomRecycleBinRepository(db)
        runBlocking {
            db.spaceDao().insert(SpaceEntity(GuestSpace.ID, GuestSpace.KIND_GUEST, null))
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun observeRecycle() = recycleBin.observeRecycleBin(GuestSpace.ID).first()

    // ---- P1-1 回收站去重 ----

    @Test
    fun recycleBinDeduplicatesCascadeEntries() = runBlocking {
        val doc = writing.createBook(GuestSpace.ID, "去重之书", "", "")
        val chapterA = doc.id
        val chapterB = outline.createChapter(doc.bookId, doc.volumeId, "第二章")

        outline.softDeleteChapter(chapterA) // 章 A 单独删除(批次一,父卷存活 → 应出现在列表)
        outline.softDeleteVolume(doc.volumeId) // 整卷删除(批次二,级联存活的 B)

        val entries = observeRecycle()
        val batchIds = entries.map { it.batchId }
        assertEquals("批次即条目,不得重复", batchIds.size, batchIds.distinct().size)

        // 随卷级联删除的章(批次二)由卷根条目代表,不得单独成条(共享 batchId 会崩列表)。
        val volumeEntry = entries.first { it.kind == com.qingling.writing.core.model.RecycleEntry.Kind.VOLUME }
        assertEquals(chapterB.id, db.outlineDao().chapterById(chapterB.id)!!.id) // B 确实是墓碑
        assertTrue("章批次(父卷存活)仍在列表", entries.any {
            it.kind == com.qingling.writing.core.model.RecycleEntry.Kind.CHAPTER &&
                it.title == "第一章" && it.batchId != volumeEntry.batchId
        })
        assertTrue("随卷级联的章不得单独成条", entries.none {
            it.kind == com.qingling.writing.core.model.RecycleEntry.Kind.CHAPTER &&
                it.batchId == volumeEntry.batchId
        })
        assertTrue("卷根条目在列表", entries.contains(volumeEntry))
    }

    // ---- P1-2 恢复祖先链 ----

    @Test
    fun restoreChapterBatchRevivesDeletedParentVolume() = runBlocking {
        val doc = writing.createBook(GuestSpace.ID, "父卷之书", "", "")
        val chapterA = doc.id
        val chapterB = outline.createChapter(doc.bookId, doc.volumeId, "第二章")

        outline.softDeleteChapter(chapterA) // 批次一:仅 A
        val batch1 = db.outlineDao().chapterById(chapterA)!!.deletionBatchId!!
        outline.softDeleteVolume(doc.volumeId) // 批次二:卷 + 存活的 B
        val batch2 = db.outlineDao().volumeById(doc.volumeId)!!.deletionBatchId!!

        // 单独恢复批次一:A 应存活,且其父卷被祖先补救复活;B 保持墓碑。
        writing.restoreDeletionBatch(doc.bookId, batch1)

        assertNull("A 已恢复", db.outlineDao().chapterById(chapterA)!!.deletedAt)
        assertNull("父卷被祖先补救复活", db.outlineDao().volumeById(doc.volumeId)!!.deletedAt)
        assertNotNull("B 不得被连带复活(批次不同)", db.outlineDao().chapterById(chapterB.id)!!.deletedAt)

        val tree = outline.observeOutline(doc.bookId).first()
        assertEquals(listOf(chapterA), tree[0].chapters.map { it.id }) // 目录可见,无孤儿
    }

    @Test
    fun restoreChapterBatchRevivesDeletedBook() = runBlocking {
        val doc = writing.createBook(GuestSpace.ID, "父书之书", "", "")
        val chapterA = doc.id
        val extra = outline.createChapter(doc.bookId, doc.volumeId, "第二章")

        outline.softDeleteChapter(chapterA) // 批次一:仅 A
        val batch1 = db.outlineDao().chapterById(chapterA)!!.deletionBatchId!!
        writing.softDeleteBook(doc.bookId) // 批次二:整书级联

        writing.restoreDeletionBatch(doc.bookId, batch1)

        assertNull("A 已恢复", db.outlineDao().chapterById(chapterA)!!.deletedAt)
        assertNull("父书被祖先补救复活", db.bookDao().byId(doc.bookId)!!.deletedAt)
        // V 是 A 的父卷,祖先链补救必须复活它(与 restoreChapterBatchRevivesDeletedParentVolume 同语义);
        // 书删除批次下的非祖先成员(B)保持墓碑。
        assertNull("父卷被祖先补救复活(A 的父链)", db.outlineDao().volumeById(doc.volumeId)!!.deletedAt)
        assertNotNull("其他非祖先成员保持墓碑", db.outlineDao().chapterById(extra.id)!!.deletedAt)
    }

    // ---- 跨卷移动:原卷无洞 ----

    @Test
    fun crossVolumeMoveLeavesNoPositionGapsInSource() = runBlocking {
        val doc = writing.createBook(GuestSpace.ID, "无洞之书", "", "")
        outline.createChapter(doc.bookId, doc.volumeId, "第二章")
        outline.createChapter(doc.bookId, doc.volumeId, "第三章")
        outline.createVolume(doc.bookId, "第二卷")
        val volumeIds = outline.observeOutline(doc.bookId).first().map { it.id }
        val secondVolumeId = volumeIds[1]

        // 把中间章(第二章)移到第二卷,原卷剩 ch1,ch3,position 必须重排为 0,1。
        val second = db.outlineDao().activeChaptersOfVolume(doc.volumeId).first { it.title == "第二章" }
        outline.moveChapter(second.id, secondVolumeId, 0)

        val tree = outline.observeOutline(doc.bookId).first()
        assertEquals(listOf(0L, 1L), tree[0].chapters.map { it.position })
        assertEquals(listOf("第一章", "第三章"), tree[0].chapters.map { it.title })
        assertEquals(listOf(0L), tree[1].chapters.map { it.position })
    }

    // ---- 结构操作触碰书 updatedAt ----

    @Test
    fun structuralOpsTouchBookUpdatedAt() = runBlocking {
        val doc = writing.createBook(GuestSpace.ID, "触碰之书", "", "")
        val before = db.bookDao().byId(doc.bookId)!!.updatedAt

        outline.createChapter(doc.bookId, doc.volumeId, "第二章")
        val afterCreate = db.bookDao().byId(doc.bookId)!!.updatedAt
        assertTrue("createChapter 应触碰书 updated_at", afterCreate > before)

        val second = db.outlineDao().activeChaptersOfVolume(doc.volumeId).first { it.title == "第二章" }
        outline.moveChapterWithinVolume(second.id, -1)
        val afterMove = db.bookDao().byId(doc.bookId)!!.updatedAt
        assertTrue("移动应触碰书 updated_at", afterMove > afterCreate)

        outline.softDeleteChapter(second.id)
        val afterDelete = db.bookDao().byId(doc.bookId)!!.updatedAt
        assertTrue("删除应触碰书 updated_at", afterDelete > afterMove)
    }
}
