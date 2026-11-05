package com.qingling.writing.feature.editor

import com.qingling.writing.core.model.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.*
import org.junit.Test

class Implementation048Test {
    @Test fun openingOldOutlineIsLossless(){
        for(body in listOf("", "  自由细纲😀\n\n  后段  \n", "目标：  找到线索\r\n\r\n分场：\r\n  - [>]  雨夜  \r\n\r\n普通段落  \r\n", "分场：\n- [ ] \n", "正文".repeat(50000)))
            assertEquals(body,ChapterOutlineForm.fromBody(body).toBody())
    }
    @Test fun editingGoalKeepsOtherRawLines(){
        val form=ChapterOutlineForm.fromBody("目标：旧目标\r\n\r\n分场：\r\n  - [ ]  场景  \r\n\r\n")
        assertTrue(form.copy(goal="新目标").toBody().endsWith("分场：\r\n  - [ ]  场景  \r\n\r\n"))
    }
    @Test fun freeTextAtLimitCanBeShortenedWithoutAddingMarkers(){
        val body="文".repeat(100000);val f=ChapterOutlineForm.fromBody(body)
        assertEquals(body.dropLast(1),f.copy(goal=f.goal.dropLast(1)).toBody())
    }
    @Test fun changingOneSceneDoesNotRewriteOtherScenes(){
        val f=ChapterOutlineForm.fromBody("目标：目标\n\n分场：\n  - [ ] 第一场  \n\t第二场  ")
        val result=f.cycle(1).toBody()
        assertTrue(result.contains("  - [ ] 第一场  \n"));assertTrue(result.contains("- [>] \t第二场  "))
    }
    @Test fun changingToCurrentClearsOnlyOtherCurrent(){
        val f=ChapterOutlineForm.fromBody("分场：\n- [>] 一\n- [ ] 二\n- [x] 三")
        val result=ChapterReference.parse(f.cycle(1).toBody())
        assertEquals(listOf(SceneStatus.TODO,SceneStatus.CURRENT,SceneStatus.DONE),result.scenes.map{it.status})
    }
    @Test fun finishingOneSceneDoesNotResetAnotherCurrent(){
        val f=ChapterOutlineForm.fromBody("分场：\n- [>] 一\n- [>] 二")
        assertEquals(SceneStatus.CURRENT,ChapterReference.parse(f.cycle(0).toBody()).scenes[1].status)
    }
    @Test fun emptyNewSceneDoesNotAppearAsFakeNextScene(){
        assertTrue(ChapterReference.parse("分场：\n- [ ] ").scenes.isEmpty())
    }
    @Test fun referenceStatusToggleKeepsWhitespaceAndCrLf(){
        val source="目标：  目标  \r\n\r\n分场：\r\n  - [ ]   场景  \r\n"
        val scene=ChapterReference.parse(source).scenes.single()
        assertEquals(source.replace("[ ]","[>]"),ChapterReference.advance(source,scene))
    }
    private fun volumes()=listOf(OutlineVolume("v1","b","一",0,listOf(OutlineChapter("a","b","v1","A",0,0),OutlineChapter("b","b","v1","B",1,0))),OutlineVolume("v2","b","二",1,listOf(OutlineChapter("c","b","v2","C",0,0))))
    @Test fun exportChapterDoesNotIncludeSibling(){assertEquals(setOf("b"),ExportPreset(ExportScope.CHAPTER,"b").select(volumes()))}
    @Test fun exportVolumeDoesNotIncludeOtherVolume(){assertEquals(setOf("a","b"),ExportPreset(ExportScope.VOLUME,"v1").select(volumes()))}
    @Test fun deletedExportTargetNeverFallsBackToWholeBook(){assertTrue(ExportPreset(ExportScope.CHAPTER,"missing").select(volumes()).isEmpty());assertTrue(ExportPreset(ExportScope.VOLUME,"missing").select(volumes()).isEmpty())}
    @Test fun noExportPresetSelectsWholeBook(){assertEquals(setOf("a","b","c"),ExportPreset().select(volumes()))}
    @Test fun chromeDoesNotUndoUpScrollOnUnchangedIme(){
        val s=EditorChromeState();s.environment(true,true,false,false,false,false,false);assertEquals(EditorTopMode.THIN,s.topMode)
        s.scroll(true);repeat(5){s.environment(true,true,false,false,false,false,false)};assertEquals(EditorTopMode.FULL,s.topMode)
    }
    @Test fun hidingKeyboardAwayFromTopDoesNotExpand(){val s=EditorChromeState();s.environment(true,true,false,false,false,false,false);s.environment(false,false,false,false,false,false,false);assertEquals(EditorTopMode.THIN,s.topMode)}
    @Test fun shortChapterAndActualTopReachAreStable(){val s=EditorChromeState();s.environment(true,true,false,false,false,true,true);assertEquals(EditorTopMode.FULL,s.topMode);s.scroll(false);s.reachedTop();assertEquals(EditorTopMode.FULL,s.topMode)}
    @Test fun focusModeRejectsScrollAndTopExpansion(){val s=EditorChromeState();s.environment(false,false,false,true,false,true,false);s.scroll(true);s.reachedTop();assertEquals(EditorTopMode.THIN,s.topMode)}
    @Test fun eachTouchGestureChangesChromeAtMostOnce(){
        val changes=mutableListOf<Boolean>();val c=EditorChromeScrollConnection(24f,{true},{changes.add(it)},{false})
        c.beginGesture();c.onPreScroll(Offset(0f,-5f),NestedScrollSource.UserInput);assertTrue(changes.isEmpty())
        c.onPreScroll(Offset(0f,-25f),NestedScrollSource.UserInput);c.onPreScroll(Offset(0f,50f),NestedScrollSource.UserInput);assertEquals(listOf(false),changes)
        c.endGesture();c.beginGesture();c.onPreScroll(Offset(0f,25f),NestedScrollSource.UserInput);assertEquals(listOf(false,true),changes)
    }
    @Test fun proofKeysSurviveOffsetChangesOutsideExcerpt(){
        val text="前".repeat(40)+"今天今天去看海。"+"后".repeat(100)
        val a=Proofreader.scan(text,emptyList()).issues.first{it.message.startsWith("相邻重复词")}
        val b=Proofreader.scan("新增段落\n"+text,emptyList()).issues.first{it.message.startsWith("相邻重复词")}
        assertEquals(a.stableKey,b.stableKey);assertNotEquals(a.start,b.start)
    }
    @Test fun proofOnlyRemovesExactAdjacentRepetition(){
        val text="今天今天出发";val issue=Proofreader.scan(text,emptyList()).issues.first{it.message.startsWith("相邻重复词")}
        assertEquals("今天出发",Proofreader.removeRepeated(text,issue));assertNull(Proofreader.removeRepeated("明天明天出发",issue))
        val punctuation=Proofreader.scan("（未结束",emptyList()).issues.first();assertNull(Proofreader.removeRepeated("（未结束",punctuation))
    }
}
