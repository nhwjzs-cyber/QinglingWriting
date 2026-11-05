package com.qingling.writing

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qingling.writing.core.database.WritingDatabase
import com.qingling.writing.core.database.entities.ChapterEntity
import com.qingling.writing.core.database.entities.SpaceEntity
import com.qingling.writing.core.model.GuestSpace
import com.qingling.writing.data.RoomOutlineRepository
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
 * M1 验收的仪器测试(docs/06 M1 通过条件;docs/05 §3/§6):
 * Room 约束/事务回滚、创建书原子性、跨书引用拒绝、同名章可区分、移动后顺序、
 * 删除批次配对(恢复不复活更早单独删除的章)、书级联单批次、文件库重开结构仍在。
 */
@RunWith(AndroidJUnit4::class)
class WritingDatabaseM1InstrumentedTest {

    private lateinit var context: Context
    private lateinit var db: WritingDatabase
    private lateinit var writing: RoomWritingRepository
    private lateinit var outline: RoomOutlineRepository
    private var idCounter: Int = 0

    private fun nextId(): String = "id-${idCounter++}"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, WritingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        writing = RoomWritingRepository(db, ::nextId, { "2026-10-31T00:00:00Z" })
        outline = RoomOutlineRepository(db, ::nextId, { "2026-10-31T00:00:00Z" })
        runBlocking {
            db.spaceDao().insert(SpaceEntity(GuestSpace.ID, GuestSpace.KIND_GUEST, null))
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun observeOutline(bookId: String) = outline.observeOutline(bookId).first()

    // ---- M1 通过条件 1:创建书原子生成第一卷/第一章 ----

    @Test
    fun createBookCreatesFirstVolumeAndChapterAtomically() = runBlocking {
        val doc = writing.createBook(GuestSpace.ID, "测试书", "作者", "简介")

        val book = db.bookDao().byId(doc.bookId)
        assertNotNull("书行应存在", book)
        assertEquals("测试书", book!!.title)
        val volume = db.outlineDao().volumeById(doc.volumeId)
        assertNotNull("第一卷应随书原子生成", volume)
        assertEquals("第一卷", volume!!.title)
        assertEquals(0L, volume.position)
        val chapter = db.outlineDao().chapterById(doc.id)
        assertNotNull("第一章应随书原子生成", chapter)
        assertEquals("第一章", chapter!!.title)
        assertEquals(0L, chapter.position)
    }

    // ---- docs/05 §3:事务失败整体回滚 ----

    @Test
    fun transactionRollbackLeavesNoRows() = runBlocking {
        try {
            db.withTransaction {
                writing.createBook(GuestSpace.ID, "会回滚的书", "", "")
                throw IllegalStateException("boom")
            }
            org.junit.Assert.fail("应当抛出异常")
        } catch (expected: IllegalStateException) {
            assertEquals("boom", expected.message)
        }
        assertEquals("回滚后不应留下任何书行", 0, db.bookDao().countInSpace(GuestSpace.ID))
    }

    // ---- M1 通过条件:跨书卷章引用被拒绝(数据库层复合外键) ----

    @Test
    fun crossBookVolumeReferenceRejectedByForeignKey() = runBlocking {
        val book1 = writing.createBook(GuestSpace.ID, "书一", "", "")
        val book2 = writing.createBook(GuestSpace.ID, "书二", "", "")

        // 章 book_id=书一,却挂在书二的卷上 → 复合外键拒绝。
        val mismatch1 = ChapterEntity(
            id = "bad-1", bookId = book1.bookId, volumeId = book2.volumeId,
            title = "非法章", body = "", position = 0, editSeq = 0, wordCount = 0,
            createdAt = "t", updatedAt = "t", deletedAt = null, deletionBatchId = null,
        )
        try {
            db.outlineDao().insertChapter(mismatch1)
            org.junit.Assert.fail("跨书卷章引用应被拒绝")
        } catch (expected: SQLiteConstraintException) {
            // 预期
        }

        // 反向同理。
        val mismatch2 = mismatch1.copy(id = "bad-2", bookId = book2.bookId, volumeId = book1.volumeId)
        try {
            db.outlineDao().insertChapter(mismatch2)
            org.junit.Assert.fail("跨书卷章引用应被拒绝")
        } catch (expected: SQLiteConstraintException) {
            // 预期
        }
    }

    // ---- M1 通过条件:同名章可区分 ----

    @Test
    fun sameTitleChaptersAreDistinguishableByUuid() = runBlocking {
        val doc = writing.createBook(GuestSpace.ID, "同名章之书", "", "")
        val a = outline.createChapter(doc.bookId, doc.volumeId, "未命名章节")
        val b = outline.createChapter(doc.bookId, doc.volumeId, "未命名章节")

        org.junit.Assert.assertNotEquals("UUID 为身份,同名合法", a.id, b.id)
        val outlineTree = observeOutline(doc.bookId)
        assertEquals(1, outlineTree.size)
        val chapters = outlineTree[0].chapters
        // createBook 已原子生成第一章;加上新建的两章同名章,共 3 章、顺序稳定。
        assertEquals(3, chapters.size)
        assertEquals(listOf(doc.id, a.id, b.id), chapters.map { it.id })
    }

    // ---- M1 通过条件:上移/下移/跨卷移动后顺序正确 ----

    @Test
    fun reorderWithinAndAcrossVolumesKeepsOutlineOrder() = runBlocking {
        val doc = writing.createBook(GuestSpace.ID, "排序之书", "", "")
        val c2 = outline.createChapter(doc.bookId, doc.volumeId, "第二章")
        val c3 = outline.createChapter(doc.bookId, doc.volumeId, "第三章")
        outline.createVolume(doc.bookId, "第二卷")
        val volumeIds = observeOutline(doc.bookId).map { it.id }
        val secondVolumeId = volumeIds[1]

        // 上移:第二章到最前。
        outline.moveChapterWithinVolume(c2.id, -1)
        var tree = observeOutline(doc.bookId)
        assertEquals(listOf(c2.id, doc.id, c3.id), tree[0].chapters.map { it.id })

        // 下移:回到 [第一章, 第二章, 第三章]。
        outline.moveChapterWithinVolume(c2.id, +1)
        tree = observeOutline(doc.bookId)
        assertEquals(listOf(doc.id, c2.id, c3.id), tree[0].chapters.map { it.id })

        // 越界为无操作:第一章再上移应保持不变。
        outline.moveChapterWithinVolume(doc.id, -1)
        tree = observeOutline(doc.bookId)
        assertEquals(listOf(doc.id, c2.id, c3.id), tree[0].chapters.map { it.id })

        // 跨卷移动:第三章移到第二卷末尾。
        outline.moveChapter(c3.id, secondVolumeId, Int.MAX_VALUE)
        tree = observeOutline(doc.bookId)
        assertEquals(listOf(doc.id, c2.id), tree[0].chapters.map { it.id })
        assertEquals(listOf(c3.id), tree[1].chapters.map { it.id })

        // 卷上移:第二卷到最前。
        outline.moveVolume(doc.bookId, secondVolumeId, -1)
        tree = observeOutline(doc.bookId)
        assertEquals(secondVolumeId, tree[0].id)
    }

    // ---- M1 通过条件:删除再恢复不会错误恢复先前单独删除的章节 ----

    @Test
    fun restoreBatchDoesNotReviveEarlierIndividualDeletion() = runBlocking {
        val doc = writing.createBook(GuestSpace.ID, "配对之书", "", "")
        val chapterA = doc.id // 第一章
        val chapterB = outline.createChapter(doc.bookId, doc.volumeId, "第二章")

        // 先单独删除第一章(批次一)。
        outline.softDeleteChapter(chapterA)
        val batch1 = checkNotNull(db.outlineDao().chapterById(chapterA)!!.deletionBatchId) { "删除后应有批次 id" }

        // 再整卷删除(批次二)——级联只标记当时仍存活的第二章。
        outline.softDeleteVolume(doc.volumeId)
        val volume = db.outlineDao().volumeById(doc.volumeId)!!
        val batch2 = checkNotNull(volume.deletionBatchId) { "卷删除后应有批次 id" }
        assertEquals("更早删除的章不得被并入新批次", batch1, db.outlineDao().chapterById(chapterA)!!.deletionBatchId)
        assertEquals("存活章应随卷级联", batch2, db.outlineDao().chapterById(chapterB.id)!!.deletionBatchId)

        // 恢复批次二:第二章回来,第一章仍是墓碑。
        writing.restoreDeletionBatch(doc.bookId, batch2)
        assertNull(db.outlineDao().chapterById(chapterB.id)!!.deletedAt)
        assertNotNull("恢复父项不复活此前单独删除的子项", db.outlineDao().chapterById(chapterA)!!.deletedAt)

        // 恢复批次一:第一章回来。
        writing.restoreDeletionBatch(doc.bookId, batch1)
        assertNull(db.outlineDao().chapterById(chapterA)!!.deletedAt)
    }

    // ---- docs/01 §4:删书级联同一批次;整批恢复 ----

    @Test
    fun softDeleteBookCascadesSingleBatchAndRestoresWholeBatch() = runBlocking {
        val doc = writing.createBook(GuestSpace.ID, "级联之书", "", "")
        val extra = outline.createChapter(doc.bookId, doc.volumeId, "第二章")

        writing.softDeleteBook(doc.bookId)

        val book = db.bookDao().byId(doc.bookId)!!
        val volume = db.outlineDao().volumeById(doc.volumeId)!!
        val chapter1 = db.outlineDao().chapterById(doc.id)!!
        val chapter2 = db.outlineDao().chapterById(extra.id)!!
        val batches = setOf(book.deletionBatchId, volume.deletionBatchId, chapter1.deletionBatchId, chapter2.deletionBatchId)
        assertEquals("整书级联必须只有一个 deletionBatchId", 1, batches.size)
        assertTrue(book.deletedAt != null && volume.deletedAt != null && chapter1.deletedAt != null && chapter2.deletedAt != null)

        writing.restoreDeletionBatch(doc.bookId, book.deletionBatchId!!)
        assertNull(db.bookDao().byId(doc.bookId)!!.deletedAt)
        assertNull(db.outlineDao().volumeById(doc.volumeId)!!.deletedAt)
        assertNull(db.outlineDao().chapterById(doc.id)!!.deletedAt)
        assertNull(db.outlineDao().chapterById(extra.id)!!.deletedAt)
    }

    // ---- M1 通过条件:杀进程重开结构仍在(文件库重开等价验证;真机 force-stop 由冒烟覆盖) ----

    @Test
    fun fileDatabaseReopenKeepsStructure() = runBlocking {
        val name = "test-m1-reopen.db"
        context.deleteDatabase(name)

        val first = Room.databaseBuilder(context, WritingDatabase::class.java, name)
            .allowMainThreadQueries().build()
        first.spaceDao().insert(SpaceEntity(GuestSpace.ID, GuestSpace.KIND_GUEST, null))
        val localWriting = RoomWritingRepository(first, ::nextId, { "2026-10-31T00:00:00Z" })
        val localOutline = RoomOutlineRepository(first, ::nextId, { "2026-10-31T00:00:00Z" })
        val doc = localWriting.createBook(GuestSpace.ID, "重开仍在", "", "")
        val extra = localOutline.createChapter(doc.bookId, doc.volumeId, "第二章")
        first.close()

        val reopened = Room.databaseBuilder(context, WritingDatabase::class.java, name)
            .allowMainThreadQueries().build()
        try {
            assertNotNull(reopened.bookDao().byId(doc.bookId))
            val tree = reopened.outlineDao().observeActiveChapters(doc.bookId).first()
            assertEquals(listOf(doc.id, extra.id), tree.map { it.id })
        } finally {
            reopened.close()
            context.deleteDatabase(name)
        }
    }
}
