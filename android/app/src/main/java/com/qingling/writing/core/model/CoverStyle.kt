package com.qingling.writing.core.model
import androidx.compose.runtime.staticCompositionLocalOf
data class CoverStyle(val color:Int,val motif:Int)
val LocalCoverStyles=staticCompositionLocalOf<Map<String,CoverStyle>> {emptyMap()}
