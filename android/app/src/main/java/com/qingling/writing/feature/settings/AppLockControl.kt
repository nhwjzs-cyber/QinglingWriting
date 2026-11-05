package com.qingling.writing.feature.settings

import androidx.compose.runtime.staticCompositionLocalOf

data class AppLockControl(val enabled: Boolean, val requestChange: (Boolean) -> Unit, val contentAccessible: Boolean = true)
val LocalAppLockControl = staticCompositionLocalOf { AppLockControl(false, {}) }
