package com.qingling.writing
import androidx.test.platform.app.InstrumentationRegistry
import com.qingling.writing.core.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
class Design036UpgradeProbe {
    @Test fun original035DataSurvivesUpgradeTo036()=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("fixture")=="design036")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.getSharedPreferences("design036-upgrade",0).getBoolean("seeded035",false))
        val c=(context.applicationContext as WritingApplication).container
        val books=c.writingRepository.observeBooks(GuestSpace.ID).first()
        val book=books.first{it.title=="增补升级验收·原稿"}
        val chapter=c.writingRepository.readChapter(c.writingRepository.resolveBookActiveChapter(book.id)!!)
        assertEquals("增补升级：中文与 emoji😀 保留。\n\n空行和行末空格  ",chapter.body)
        val empty=books.first{it.title=="增补升级验收·旧空书"}
        assertNull(c.writingRepository.resolveBookActiveChapter(empty.id))
        assertTrue(c.planning.inbox().first().any{QuickNote.decode(it.body).text=="来自0.35的原始灵感😀"})
        c.database.openHelper.readableDatabase.query("PRAGMA user_version").use{assertTrue(it.moveToFirst());assertEquals(9,it.getInt(0))}
    }
}
