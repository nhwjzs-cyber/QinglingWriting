package com.qingling.writing
import androidx.test.platform.app.InstrumentationRegistry
import com.qingling.writing.core.model.*
import com.qingling.writing.core.database.entities.SpaceEntity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
class Design035UpgradeProbe {
    private val body="覆盖升级验收：原稿完整保留😀\n\n夜里两点，灯塔的光扫过海面。  "
    @Test fun verify035()=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("fixture")=="design035")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION") val version=context.packageManager.getPackageInfo(context.packageName,0).versionName
        assertEquals("0.35.0",version)
        assertTrue(context.getSharedPreferences("design035-upgrade",0).getBoolean("seededFromOriginalApk",false))
        val c=(context.applicationContext as WritingApplication).container
        val book=c.writingRepository.observeBooks(GuestSpace.ID).first().first{it.title=="升级验收·原稿"}
        val id=requireNotNull(c.writingRepository.resolveBookActiveChapter(book.id))
        val chapter=c.writingRepository.readChapter(id)
        assertEquals(body,chapter.body)
        assertTrue(chapter.editSeq>=1L)
        assertEquals(id,c.writingRepository.resolveLastActiveChapter())
    }
}
