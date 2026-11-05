package com.qingling.writing.core.model

enum class EditorFont { SYSTEM, SERIF, MONOSPACE }
enum class EditorCanvas { THEME, INK, PAPER, FOREST, CUSTOM }

/** Display preferences only: never used to rewrite or export chapter text. */
data class EditorAppearance(
    val fontSize: Int = 17,
    val lineHeightPercent: Int = 190,
    val gutter: Int = 34,
    val font: EditorFont = EditorFont.SYSTEM,
    val canvas: EditorCanvas = EditorCanvas.THEME,
    val backgroundHex: String = "121212",
    val textHex: String = "E6E6E6",
    val imagePath: String = "",
    val imageDimPercent: Int = 75,
) {
    fun normalized() = copy(
        fontSize = fontSize.coerceIn(14, 32),
        lineHeightPercent = lineHeightPercent.coerceIn(120, 220),
        gutter = gutter.coerceIn(12, 40),
        backgroundHex = normalizeHex(backgroundHex) ?: "121212",
        textHex = normalizeHex(textHex) ?: "E6E6E6",
        imageDimPercent = imageDimPercent.coerceIn(0, 90),
    )
}

fun normalizeHex(value: String): String? = value.trim().removePrefix("#")
    .takeIf { it.length == 6 && it.all { c -> c in '0'..'9' || c.lowercaseChar() in 'a'..'f' } }
    ?.uppercase(java.util.Locale.ROOT)
