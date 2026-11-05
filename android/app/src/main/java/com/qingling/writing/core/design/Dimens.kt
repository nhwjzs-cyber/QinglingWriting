package com.qingling.writing.core.design

import androidx.compose.ui.unit.dp

/**
 * 间距与圆角来自 design/tokens.json:
 * spacingDp = [4,8,12,16,20,24,32,40,48];radiusDp = { button:16, bookCard:20, panel:24 }。
 * 页面只用这里的值,不随手写新的间距/圆角(docs/05 §5)。
 */
object Spacing {
    val s4 = 4.dp
    val s8 = 8.dp
    val s12 = 12.dp
    val s16 = 16.dp
    val s20 = 20.dp
    val s24 = 24.dp
    val s32 = 32.dp
    val s40 = 40.dp
    val s48 = 48.dp

    /** editor.horizontalPaddingDp = 24:正文左右边距。 */
    val editorGutter = 24.dp
}

/** tokens.radiusDp。 */
object Radii {
    val button = 16.dp
    val bookCard = 18.dp
    val panel = 26.dp
}

/** tokens.touchTargetMinDp = 48:最小触控目标(ask 红线)。 */
object TouchTarget {
    val min = 48.dp
}

/** docs/02 §2:列表行最小高 56dp(M1 复核 F5:收敛为共享常量,书架卡片与通用列表行共用)。 */
object Dimens {
    val listRowMinHeight = 56.dp

    /** UX v0.2 tokens.panel:左目录 72%/320dp,右工具 68%/300dp;不再统一 86%。 */
    object Panels {
        val leftWidthFraction = 0.72f
        val leftMaxWidth = 320.dp
        val rightWidthFraction = 0.68f
        val rightMaxWidth = 300.dp
        /** 大字体(fontScale ≥1.5)可加宽至 min(W−72dp, max(默认, W×0.80))。 */
        const val largeFontThreshold = 1.5f
        val largeFontWidthFraction = 0.80f
        /** 遮罩 alpha:浅 0.20 / 深 0.28;随面板进度连续变化。 */
        val scrimAlphaLight = 0.20f
        val scrimAlphaDark = 0.42f
    }
}
