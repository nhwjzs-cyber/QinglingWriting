package com.qingling.writing.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors

/** Shared paper surfaces and rounded component shapes. */
private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightSurfaceRaised,
    onPrimaryContainer = LightTextPrimary,
    secondary = LightPrimary,
    onSecondary = LightOnPrimary,
    secondaryContainer = LightSurfaceRaised,
    onSecondaryContainer = LightTextPrimary,
    tertiary = LightPrimary,
    onTertiary = LightOnPrimary,
    tertiaryContainer = LightSurfaceRaised,
    onTertiaryContainer = LightTextPrimary,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceRaised,
    onSurfaceVariant = LightTextSecondary,
    surfaceDim = LightBackground,
    surfaceBright = LightSurface,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightSurfaceRaised,
    surfaceContainer = LightSurfaceRaised,
    surfaceContainerHigh = LightSurfaceRaised,
    surfaceContainerHighest = LightSurfaceRaised,
    error = LightError,
    onError = LightOnPrimary,
    errorContainer = LightError,
    onErrorContainer = LightOnPrimary,
    outline = LightDivider,
    outlineVariant = LightDivider,
    inverseSurface = LightTextPrimary,
    inverseOnSurface = LightBackground,
    inversePrimary = LightPrimary,
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkSurfaceRaised,
    onPrimaryContainer = DarkTextPrimary,
    secondary = DarkPrimary,
    onSecondary = DarkOnPrimary,
    secondaryContainer = DarkSurfaceRaised,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = DarkPrimary,
    onTertiary = DarkOnPrimary,
    tertiaryContainer = DarkSurfaceRaised,
    onTertiaryContainer = DarkTextPrimary,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceRaised,
    onSurfaceVariant = DarkTextSecondary,
    surfaceDim = DarkBackground,
    surfaceBright = DarkSurface,
    surfaceContainerLowest = DarkSurface,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurfaceRaised,
    surfaceContainerHigh = DarkSurfaceRaised,
    surfaceContainerHighest = DarkSurfaceHighest,
    error = DarkError,
    onError = DarkOnPrimary,
    errorContainer = DarkError,
    onErrorContainer = DarkOnPrimary,
    outline = DarkDivider,
    outlineVariant = DarkDivider,
    inverseSurface = DarkTextPrimary,
    inverseOnSurface = DarkBackground,
    inversePrimary = DarkPrimary,
)

@Composable
fun QinglingWritingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = androidx.compose.material3.Shapes(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
        ),
        content = { CompositionLocalProvider(LocalTextSelectionColors provides TextSelectionColors(
            handleColor = colorScheme.primary, backgroundColor = colorScheme.primary.copy(alpha = .25f)), content = content) },
    )
}
