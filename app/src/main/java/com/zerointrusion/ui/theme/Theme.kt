package com.zerointrusion.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Tide100,
    onPrimary = Ink900,
    secondary = Sand200,
    onSecondary = Ink900,
    tertiary = Moss100,
    onTertiary = Ink900,
    background = Ink900,
    onBackground = MistWhite,
    surface = Ink700,
    onSurface = MistWhite,
    surfaceVariant = Slate500,
    onSurfaceVariant = MistWhite,
    error = Coral100,
    onError = Ink900,
)

private val LightColorScheme = lightColorScheme(
    primary = Tide600,
    onPrimary = PaperWhite,
    secondary = Sand200,
    onSecondary = Ink900,
    tertiary = Moss500,
    onTertiary = PaperWhite,
    background = PaperWhite,
    onBackground = Ink900,
    surface = MistWhite,
    onSurface = Ink900,
    surfaceVariant = Sand100,
    onSurfaceVariant = Ink700,
    error = Coral500,
    onError = PaperWhite,
)

@Composable
fun ZeroIntrusionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme && !dynamicColor) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}