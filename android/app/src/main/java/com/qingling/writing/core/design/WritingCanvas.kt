package com.qingling.writing.core.design

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.model.*

@Composable
fun EditorAppearance.backgroundColor(): Color = when (canvas) {
    EditorCanvas.THEME -> MaterialTheme.colorScheme.background
    EditorCanvas.INK -> Color(0xFF121212)
    EditorCanvas.PAPER -> Color(0xFFF3ECDC)
    EditorCanvas.FOREST -> Color(0xFF15201B)
    EditorCanvas.CUSTOM -> Color(0xFF000000L or backgroundHex.toLong(16))
}

@Composable
fun EditorAppearance.textColor(): Color = when (canvas) {
    EditorCanvas.THEME -> if (MaterialTheme.colorScheme.background == DarkBackground) DarkTextBody else MaterialTheme.colorScheme.onBackground
    EditorCanvas.INK -> Color(0xFFE6E6E6)
    EditorCanvas.PAPER -> Color(0xFF302E28)
    EditorCanvas.FOREST -> Color(0xFFDCE8DF)
    EditorCanvas.CUSTOM -> Color(0xFF000000L or textHex.toLong(16))
}

fun EditorFont.family(): FontFamily = when (this) {
    EditorFont.SYSTEM -> FontFamily.Default
    EditorFont.SERIF -> FontFamily.Serif
    EditorFont.MONOSPACE -> FontFamily.Monospace
}

@Composable
fun WritingCanvas(appearance: EditorAppearance, modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit) {
    val store = LocalAppContainer.current.backgroundImages
    val bitmap by produceState<Bitmap?>(null, appearance.imagePath) {
        value = store.read(appearance.imagePath)
    }
    Box(modifier.background(appearance.backgroundColor())) {
        bitmap?.let {
            Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(appearance.backgroundColor().copy(alpha = appearance.imageDimPercent / 100f)))
        }
        content()
    }
}
