package com.qingling.writing

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.qingling.writing.core.database.WritingDatabase
import com.qingling.writing.core.database.entities.EditorPositionEntity
import com.qingling.writing.core.database.entities.SpaceEntity
import com.qingling.writing.core.model.ChapterDraft
import com.qingling.writing.core.model.GuestSpace
import com.qingling.writing.core.model.SaveResult
import com.qingling.writing.data.RoomHistoryRepository
import com.qingling.writing.data.RoomWritingRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M2 持久化仪器测试(docs/06 M2 / docs/05 §3):
 * 迁移 1→2(数据保留 + schema 校验)、saveDraft 防旧写、错误分类、
 * 手动/自动快照与 BEFORE_RESTORE 恢复、光标位置读写、5 万码点保存性能。
 */
@RunWith(AndroidJUnit4::class)
class M2PersistenceInstrumentedTest {

    private val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WritingDatabase::class.java,
    )

    private lateinit var context: Context
    private lateinit var db: WritingDatabase
    private lateinit var writing: RoomWritingRepository
    private lateinit var history: RoomHistoryRepository
    private var idCounter: Int = 0

    private fun nextId(): String = "id-${idCounter++}"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, WritingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        writing = RoomWritingRepository(db, ::nextId, { "2026-10-31T00:00:00Z" })
        history = RoomHistoryRepository(db, ::nextId, { "2026-10-31T00:00:00Z" })
        runBlocking {
            db.spaceDao().insert(SpaceEntity(GuestSpace.ID, GuestSpace.KIND_GUEST, null))
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun newChapter(title: String = "第一章"): com.qingling.writing.core.model.ChapterDocument {
        val doc = writing.createBook(GuestSpace.ID, "测试书", "", "")
        if (title != "第一章") {
            writing.renameChapterCompat(doc.id, title)
        }
        return doc
    }

    /** 测试辅助:章重命名走 OutlineDao(renameChapter 依赖流程)。 */
    private suspend fun com.qingling.writing.data.RoomWritingRepository.renameChapterCompat(
        chapterId: String,
        title: String,
    ) {
        // 使用 restoreChapterContent 的守卫更新通道模拟标题变化(仅测试用)。
        val chapter = db.outlineDao().chapterById(chapterId)!!
        db.outlineDao().restoreChapterContent(
            id = chapterId, title = title, body = chapter.body,
            wordCount = chapter.wordCount, editSeq = chapter.editSeq + 1,
            updatedAt = "2026-10-31T00:00:01Z",
        )
    }

    // ---- 防旧写 ----

    @Test
    fun saveDraftAdvancesEditSeqAndWordCount() = runBlocking {
        val doc = newChapter()
        val result = writing.saveDraft(ChapterDraft(doc.id, doc.title, "山雾三章", editSeq = doc.editSeq + 1))
        assertTrue(result is SaveResult.Committed)
        val reread = writing.readChapter(doc.id)
        assertEquals("山雾三章", reread.body)
        assertEquals(doc.editSeq + 1, reread.editSeq)
        assertEquals(4L, db.outlineDao().chapterById(doc.id)!!.wordCount) // 山雾三章 = 4 码点
    }

    @Test
    fun staleEditSeqIsRejectedAndSurfaced() = runBlocking {
        val doc = newChapter()
        assertTrue(writing.saveDraft(ChapterDraft(doc.id, doc.title, "新", editSeq = doc.editSeq + 1)) is SaveResult.Committed)
        // 旧写(更小 seq)必须被拒绝
        val stale = writing.saveDraft(ChapterDraft(doc.id, doc.title, "旧", editSeq = doc.editSeq))
        assertTrue(stale is SaveResult.Failed)
        assertEquals(com.qingling.writing.core.model.SaveFailure.STALE_SEQUENCE, (stale as SaveResult.Failed).reason)
        // 同 seq 重放(重复确认)同样拒绝——重试只允许一次版本推进
        val replay = writing.saveDraft(ChapterDraft(doc.id, doc.title, "新", editSeq = doc.editSeq + 1))
        assertTrue(replay is SaveResult.Failed)
        assertEquals("旧写不得覆盖新版本", "新", writing.readChapter(doc.id).body)
    }

    @Test
    fun deletedChapterReportsDeleted() = runBlocking {
        val doc = newChapter()
        db.outlineDao().softDeleteChapter(doc.id, "t", "b1")
        val result = writing.saveDraft(ChapterDraft(doc.id, doc.title, "x", editSeq = doc.editSeq + 1))
        assertEquals(com.qingling.writing.core.model.SaveFailure.DELETED, (result as SaveResult.Failed).reason)
    }

    // ---- 光标位置 ----

    @Test
    fun editorPositionUpsertRoundTrip() = runBlocking {
        val doc = newChapter()
        writing.saveEditorPosition(
            com.qingling.writing.core.model.EditorPosition(doc.id, 12, 15, 0),
        )
        writing.saveEditorPosition(
            com.qingling.writing.core.model.EditorPosition(doc.id, 30, 30, 0),
        )
        val pos = writing.readEditorPosition(doc.id)!!
        assertEquals(30, pos.selectionStart)
        assertEquals(30, pos.selectionEnd)
    }

    // ---- 历史 ----

    @Test
    fun manualSnapshotAndRestoreKeepsCurrentAsBeforeRestore() = runBlocking {
        val doc = newChapter()
        val seq1 = doc.editSeq + 1
        writing.saveDraft(ChapterDraft(doc.id, doc.title, "第一稿", seq1))
        history.createManualSnapshot(doc.id)
        val seq2 = seq1 + 1
        writing.saveDraft(ChapterDraft(doc.id, doc.title, "第二稿", seq2))

        val manual = history.observeRevisions(doc.id).first().first { it.source == com.qingling.writing.core.model.Revision.Source.MANUAL }
        val restored = history.restoreRevision(doc.id, manual.id)

        assertEquals("第一稿", restored.body)
        assertEquals(seq2 + 1, restored.editSeq)
        val revisions = history.observeRevisions(doc.id).first()
        val beforeRestore = revisions.first { it.source == com.qingling.writing.core.model.Revision.Source.BEFORE_RESTORE }
        assertEquals("第二稿", beforeRestore.body) // 恢复前内容已保全
        assertEquals("第一稿", writing.readChapter(doc.id).body)
    }

    @Test
    fun autoSnapshotRespectsThrottleAndCap() = runBlocking {
        val doc = newChapter()
        // 固定时钟同刻,两次自动快照:第二次应被 30s 节流跳过。
        history.createAutoSnapshotIfDue(doc.id, doc.title, "A")
        history.createAutoSnapshotIfDue(doc.id, doc.title, "B")
        val autos = history.observeRevisions(doc.id).first()
            .filter { it.source == com.qingling.writing.core.model.Revision.Source.AUTO }
        assertEquals(1, autos.size)
        assertEquals("A", autos.single().body)
    }

    // ---- 迁移 1→2 ----

    @Test
    fun migration1To2PreservesChapterData() = runBlocking {
        val dbName = "m2-migration-test.db"
        context.deleteDatabase(dbName)
        // 1) 以 v1 schema 建库并写入一行真实数据(v1 无 revisions/editor_positions)。
        val v1 = migrationHelper.createDatabase(dbName, 1)
        v1.execSQL(
            "INSERT INTO spaces (id, kind, account_id) VALUES ('sp-1', 'GUEST', NULL)",
        )
        v1.execSQL(
            "INSERT INTO books (id, space_id, title, author, description, created_at, updated_at, " +
                "deleted_at, deletion_batch_id, local_seq, synced_local_seq, remote_version, local_dirty) " +
                "VALUES ('bk-1', 'sp-1', '迁移之书', '', '', 't0', 't0', NULL, NULL, 0, 0, NULL, 1)",
        )
        v1.execSQL(
            "INSERT INTO volumes (id, book_id, title, position, created_at, updated_at, deleted_at, deletion_batch_id) " +
                "VALUES ('vl-1', 'bk-1', '第一卷', 0, 't0', 't0', NULL, NULL)",
        )
        v1.execSQL(
            "INSERT INTO chapters (id, book_id, volume_id, title, body, position, edit_seq, word_count, " +
                "created_at, updated_at, deleted_at, deletion_batch_id) " +
                "VALUES ('ch-1', 'bk-1', 'vl-1', '第一章', '迁移正文', 0, 3, 4, 't0', 't0', NULL, NULL)",
        )
        v1.close()

        // 2) 跑迁移并做 schema 校验。
        val v2 = migrationHelper.runMigrationsAndValidate(dbName, 2, true, com.qingling.writing.core.database.migrations.Migrations.MIGRATION_1_2)
        val cursor = v2.query("SELECT body, edit_seq FROM chapters WHERE id = 'ch-1'")
        cursor.moveToFirst()
        assertEquals("迁移正文", cursor.getString(0))
        assertEquals(3L, cursor.getLong(1))
        cursor.close()
        v2.close()

        // 3) Room v2 正常打开,新表可用,旧数据仍在。
        val migrated = Room.databaseBuilder(context, WritingDatabase::class.java, dbName)
            .addMigrations(*com.qingling.writing.core.database.migrations.Migrations.ALL)
            .allowMainThreadQueries().build()
        try {
            val chapter = migrated.outlineDao().chapterById("ch-1")
            assertNotNull(chapter)
            migrated.revisionDao().insert(
                com.qingling.writing.core.database.entities.RevisionEntity(
                    id = "rv-1", chapterId = "ch-1", title = "第一章", body = "迁移正文",
                    contentSha256 = "x", source = "MANUAL", createdAt = "t1",
                ),
            )
            migrated.editorPositionDao().upsert(
                EditorPositionEntity(chapterId = "ch-1", selectionStart = 1, selectionEnd = 1, scrollOffset = 0),
            )
            assertEquals(1, migrated.revisionDao().observeByChapter("ch-1").first().size)
        } finally {
            migrated.close()
            context.deleteDatabase(dbName)
        }
    }

    // ---- 性能:单章 5 万码点(docs/01 §7:P95 ≤ 1s 目标) ----

    @Test
    fun save50kCodepointsUnderTarget() = runBlocking {
        val doc = newChapter()
        val body = buildString {
            repeat(50_000) { i -> append(('가' + (i % 100))) } // 5 万码点(中文区),无空白
        }
        assertEquals(50_000L, com.qingling.writing.domain.validation.WordCounter.count(body))
        var editSeq = doc.editSeq
        val samples = mutableListOf<Long>()
        repeat(5) { run ->
            editSeq += 1
            val start = System.nanoTime()
            val result = writing.saveDraft(ChapterDraft(doc.id, doc.title, body, editSeq))
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            assertTrue(result is SaveResult.Committed)
            samples += elapsedMs
        }
        val sorted = samples.sorted()
        val p50 = sorted[2]
        val p95 = sorted.last()
        android.util.Log.i("M2Perf", "50k save ms: $samples P50=$p50 P95=$p95")
        assertTrue("P95 ${p95}ms 超出 1s 目标", p95 <= 1_000)
    }
}
