package com.qingling.writing.feature.editor

import org.junit.Assert.*
import org.junit.Test

class EditorChromeVisibilityTest {
    @Test fun readingExposesBothBarsAndDiscoverableHandles() {
        val state = editorChromeVisibility(false, true, false, false)
        assertTrue(state.top); assertTrue(state.bottom); assertTrue(state.handles)
    }
    @Test fun typingKeepsTitleAndHidesFooterWithoutReplacingTheEditor() {
        val state = editorChromeVisibility(true, true, false, false)
        assertTrue(state.top); assertFalse(state.bottom); assertFalse(state.handles)
    }
    @Test fun saveFailureRemainsVisibleDuringTyping() {
        val state = editorChromeVisibility(true, true, false, true)
        assertTrue(state.top); assertTrue(state.bottom); assertFalse(state.handles)
    }
    @Test fun openedPanelDoesNotAdvertiseHiddenHandles() {
        val state = editorChromeVisibility(false, true, true, false)
        assertTrue(state.top); assertTrue(state.bottom); assertFalse(state.handles)
    }
    @Test fun missingOrLoadingContentKeepsNavigationAvailable() {
        val state = editorChromeVisibility(true, false, false, false)
        assertTrue(state.top); assertTrue(state.bottom); assertFalse(state.handles)
    }
}
