package com.cleanguard.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = GuardGreen,
    onPrimary = Color.White,
    primaryContainer = GuardGreenContainer,
    onPrimaryContainer = Color(0xFF002019),
    secondary = GuardBlue,
    onSecondary = Color.White,
    tertiary = GuardAmber,
    error = GuardRed,
    background = GuardSurface,
    onBackground = GuardInk,
    surface = Color.White,
    onSurface = GuardInk,
    surfaceVariant = Color(0xFFE4ECE9),
    onSurfaceVariant = Color(0xFF414946),
    outline = Color(0xFF707976),
)

private val DarkColors = darkColorScheme(
    primary = GuardGreenDark,
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF005143),
    onPrimaryContainer = GuardGreenContainer,
    secondary = Color(0xFFA2C9F2),
    onSecondary = Color(0xFF043354),
    tertiary = Color(0xFFFFBA47),
    error = Color(0xFFFFB3B5),
    background = GuardSurfaceDark,
    onBackground = Color(0xFFDFE4E1),
    surface = Color(0xFF171C1B),
    onSurface = Color(0xFFDFE4E1),
    surfaceVariant = Color(0xFF414946),
    onSurfaceVariant = Color(0xFFC0C9C5),
    outline = Color(0xFF8A938F),
)

@Composable
fun CleanGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = CleanGuardTypography,
        content = content,
    )
}

