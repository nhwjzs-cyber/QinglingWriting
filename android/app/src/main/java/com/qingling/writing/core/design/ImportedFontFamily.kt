package com.qingling.writing.core.design
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontFamily
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.model.EditorFont

@Composable fun rememberFontFamily(builtin: EditorFont,customId: String): FontFamily {
    val store=LocalAppContainer.current.importedFonts
    val family by produceState<FontFamily?>(null,customId) {value=store.load(customId)?.let {FontFamily(it)}}
    return family ?: builtin.family()
}
