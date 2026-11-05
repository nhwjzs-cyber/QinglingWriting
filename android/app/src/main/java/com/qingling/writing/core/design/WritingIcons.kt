package com.qingling.writing.core.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Small shared stroke icons, tinted by the surrounding neutral icon tile. */
object WritingIcons {
    private fun stroke(name: String, draw: PathBuilder.() -> Unit) = ImageVector.Builder(name,24.dp,24.dp,24f,24f)
        .apply { path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = draw) }.build()
    val Replace by lazy { stroke("Replace") {
        moveTo(4f,7f); lineTo(19f,7f); moveTo(15f,3f); lineTo(19f,7f); lineTo(15f,11f)
        moveTo(20f,17f); lineTo(5f,17f); moveTo(9f,13f); lineTo(5f,17f); lineTo(9f,21f)
    } }
    val History by lazy { stroke("History") {
        moveTo(4f,8f); curveTo(6f,3f,13f,2f,17f,5f); curveTo(23f,9f,20f,20f,12f,21f)
        curveTo(6f,22f,2f,17f,3f,13f); moveTo(3f,4f); lineTo(4f,8f); lineTo(8f,7f)
        moveTo(12f,7f); lineTo(12f,12f); lineTo(15f,14f)
    } }
    val Format by lazy { stroke("Format") {
        moveTo(4f,5f); lineTo(20f,5f); moveTo(4f,10f); lineTo(16f,10f)
        moveTo(4f,15f); lineTo(20f,15f); moveTo(4f,20f); lineTo(14f,20f)
    } }
    val Type by lazy { stroke("Type") {
        moveTo(2f,19f); lineTo(7f,5f); lineTo(12f,19f); moveTo(4f,14f); lineTo(10f,14f)
        moveTo(16f,10f); curveTo(23f,8f,21f,16f,21f,19f)
        moveTo(21f,13f); curveTo(13f,12f,13f,20f,18f,19f); lineTo(21f,17f)
    } }
    val Export by lazy { stroke("Export") {
        moveTo(5f,10f); lineTo(3f,10f); lineTo(3f,21f); lineTo(21f,21f); lineTo(21f,10f); lineTo(19f,10f)
        moveTo(12f,16f); lineTo(12f,3f); moveTo(7f,8f); lineTo(12f,3f); lineTo(17f,8f)
    } }
    val Backup by lazy { stroke("Backup") {
        moveTo(12f,2f); lineTo(21f,6f); lineTo(20f,15f); curveTo(18f,19f,15f,21f,12f,23f)
        curveTo(9f,21f,6f,19f,4f,15f); lineTo(3f,6f); close()
        moveTo(8f,12f); lineTo(11f,15f); lineTo(16f,9f)
    } }
val Bulb by lazy { stroke("Bulb") {
    moveTo(12f, 3f); curveTo(15.9f, 3f, 19f, 6.1f, 19f, 10f)
    curveTo(19f, 13.1f, 17.5f, 15.3f, 15.6f, 16.9f); lineTo(15.6f, 19f); lineTo(8.4f, 19f); lineTo(8.4f, 16.9f)
    curveTo(6.5f, 15.3f, 5f, 13.1f, 5f, 10f); curveTo(5f, 6.1f, 8.1f, 3f, 12f, 3f); close()
    moveTo(9.6f, 21.4f); lineTo(14.4f, 21.4f)
} }
val Chart by lazy { stroke("Chart") {
    moveTo(4f, 4f); lineTo(4f, 20f); lineTo(20f, 20f)
    moveTo(7.5f, 15f); lineTo(11f, 10f); lineTo(14f, 12.5f); lineTo(18f, 6.5f)
} }
val Target by lazy { stroke("Target") {
    // 外圈 r=9（κ≈0.5523 → 控制距 4.97），内圈 r=4.5（控制距 2.49）
    moveTo(12f, 3f); curveTo(16.97f, 3f, 21f, 7.03f, 21f, 12f); curveTo(21f, 16.97f, 16.97f, 21f, 12f, 21f)
    curveTo(7.03f, 21f, 3f, 16.97f, 3f, 12f); curveTo(3f, 7.03f, 7.03f, 3f, 12f, 3f); close()
    moveTo(12f, 7.5f); curveTo(14.49f, 7.5f, 16.5f, 9.51f, 16.5f, 12f); curveTo(16.5f, 14.49f, 14.49f, 16.5f, 12f, 16.5f)
    curveTo(9.51f, 16.5f, 7.5f, 14.49f, 7.5f, 12f); curveTo(7.5f, 9.51f, 9.51f, 7.5f, 12f, 7.5f); close()
} }
val Scroll by lazy { stroke("Scroll") { // 文档/设定本
    moveTo(7f, 3f); lineTo(15.5f, 3f); lineTo(20f, 7.5f); lineTo(20f, 21f); lineTo(7f, 21f); close()
    moveTo(15.5f, 3f); lineTo(15.5f, 7.5f); lineTo(20f, 7.5f)
    moveTo(10f, 12.5f); lineTo(17f, 12.5f); moveTo(10f, 16.5f); lineTo(14.5f, 16.5f)
} }
val Split by lazy { stroke("Split") {
    moveTo(3f, 12f); lineTo(10f, 12f)
    moveTo(10f, 12f); curveTo(15f, 12f, 15f, 5.5f, 21f, 5.5f)
    moveTo(10f, 12f); curveTo(15f, 12f, 15f, 18.5f, 21f, 18.5f)
} }
val Merge by lazy { stroke("Merge") {
    moveTo(3f, 5.5f); lineTo(9f, 5.5f); moveTo(3f, 18.5f); lineTo(9f, 18.5f)
    moveTo(9f, 5.5f); curveTo(15f, 5.5f, 14f, 12f, 21f, 12f)
    moveTo(9f, 18.5f); curveTo(15f, 18.5f, 14f, 12f, 21f, 12f)
} }


}
