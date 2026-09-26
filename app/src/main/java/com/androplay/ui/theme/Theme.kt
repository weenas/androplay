package com.androplay.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.em

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF6750A4),
    secondary = Color(0xFF625B71),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
)

/**
 * Text's default style has a fixed 24 sp line height, so any text set larger (titles, song
 * names) overlapped itself when it wrapped. Relative to the font size it always fits.
 */
private val AppTypography = Typography().let { base ->
    base.copy(bodyLarge = base.bodyLarge.copy(lineHeight = 1.4.em))
}

@Composable
fun AndroPlayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AppTypography,
        content = content
    )
}
