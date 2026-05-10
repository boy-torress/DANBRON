package com.danbron.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DanbronColorScheme = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF0A0A0F),
    secondary = Teal,
    tertiary = Red,
    background = BgPrimary,
    surface = BgSecondary,
    surfaceVariant = BgTertiary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = Border,
)

@Composable
fun DanbronTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DanbronColorScheme,
        content = content
    )
}
