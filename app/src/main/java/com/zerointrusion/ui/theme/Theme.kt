package com.zerointrusion.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Green,
    onPrimary = Black,
    secondary = Blue,
    onSecondary = White,
    tertiary = Yellow,
    onTertiary = Black,
    background = Dark,
    onBackground = White,
    surface = LightDark,
    onSurface = White,
    surfaceVariant = WeightDark,
    onSurfaceVariant = White,
    error = ErrorColor,
    onError = White,
)

private val LightColorScheme = lightColorScheme(
    primary = Blue,
    onPrimary = White,
    secondary = LightDark,
    onSecondary = White,
    tertiary = Yellow,
    onTertiary = Black,
    background = Black,
    onBackground = White,
    surface = Dark,
    onSurface = White,
    surfaceVariant = WeightDark,
    onSurfaceVariant = White,
    error = ErrorColor,
    onError = White,
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