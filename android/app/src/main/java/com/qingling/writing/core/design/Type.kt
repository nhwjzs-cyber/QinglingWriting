package com.qingling.writing.core.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/**
 * 字号与行高来自 design/tokens.json:
 * typographySp = { pageTitle:24, chapterTitle:22, bodyDefault:18, control:16, caption:12 }
 * editor.lineHeightMultiplier = 1.75(正文 18sp × 1.75 = 31.5sp)
 * 均跟随系统 fontScale(sp 单位天然生效)。未列出的样式保持 M3 基线,M0 未使用。
 */
val AppTypography = Typography(
    // 页面标题 24sp
    headlineLarge = TextStyle(fontSize = 23.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontSize = 23.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontSize = 23.sp, lineHeight = 32.sp, letterSpacing = 0.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
    // 正文/书籍标题 22sp
    titleLarge = TextStyle(fontSize = 23.sp, lineHeight = 30.sp, letterSpacing = 0.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
    // 列表与控件标题 16sp
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
    // 正文 18sp,行高约 1.75 倍(31.5sp)
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 31.5.sp),
    // 控件小号 14sp
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    // 控件 16sp
    labelLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    // 辅助文字 12sp
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
)
