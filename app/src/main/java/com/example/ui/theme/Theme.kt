package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NaturalLightColorScheme = lightColorScheme(
    primary = NaturalPrimary,
    secondary = NaturalAccentBright,
    tertiary = NaturalAccentLight,
    background = NaturalBg,
    surface = NaturalCardBg,
    onPrimary = Color.White,
    onSecondary = NaturalText,
    onBackground = NaturalText,
    onSurface = NaturalText,
    surfaceVariant = NaturalAccentHighlight,
    onSurfaceVariant = NaturalPrimary
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    // Applying the Natural Tones bright, organic theme
    MaterialTheme(
        colorScheme = NaturalLightColorScheme,
        typography = Typography,
        content = content
    )
}
