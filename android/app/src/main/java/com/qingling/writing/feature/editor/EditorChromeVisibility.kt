package com.qingling.writing.feature.editor

/** Editing chrome is independent of the text buffer: changing it must never recreate a session. */
internal data class EditorChromeVisibility(val top: Boolean, val bottom: Boolean, val handles: Boolean)
internal fun editorChromeVisibility(imeVisible: Boolean, ready: Boolean,
    panelOpen: Boolean, saveFailed: Boolean): EditorChromeVisibility {
    val normal = !imeVisible || !ready || panelOpen
    return EditorChromeVisibility(true, normal || saveFailed, ready && !imeVisible && !panelOpen)
}
