package com.michael.tradelab.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark mode is independently designed, not an inversion.
private val DarkColors = darkColorScheme(
    primary = Brand400,
    onPrimary = Neutral950,
    primaryContainer = Brand900,
    onPrimaryContainer = Brand100,
    secondary = Brand100,
    onSecondary = Neutral900,
    background = Neutral950,
    onBackground = Neutral50,
    surface = Neutral950,
    onSurface = Neutral50,
    surfaceVariant = Neutral900,
    onSurfaceVariant = Color(0xFF9AA7AE),
    surfaceContainer = Neutral900,
    surfaceContainerHigh = Neutral800,
    outline = Neutral600,
    outlineVariant = Neutral800,
    error = ErrorOnDark,
    onError = Neutral950,
)

private val LightColors = lightColorScheme(
    primary = Brand600,
    onPrimary = Neutral50,
    primaryContainer = Brand50,
    onPrimaryContainer = Brand900,
    secondary = Brand900,
    onSecondary = Neutral50,
    background = Neutral50,
    onBackground = Neutral900,
    surface = Neutral50,
    onSurface = Neutral900,
    surfaceVariant = Neutral100,
    onSurfaceVariant = Neutral600,
    surfaceContainer = Neutral100,
    surfaceContainerHigh = Neutral200,
    outline = Neutral600,
    outlineVariant = Neutral200,
    error = SemanticError,
    onError = Neutral50,
)

/** Semantic gain/loss colors tuned per theme; never brand-tinted. */
@Composable
fun gainColor(dark: Boolean = isSystemInDarkTheme()): Color = if (dark) SuccessOnDark else SemanticSuccess

@Composable
fun lossColor(dark: Boolean = isSystemInDarkTheme()): Color = if (dark) ErrorOnDark else SemanticError

@Composable
fun TradeLabTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
