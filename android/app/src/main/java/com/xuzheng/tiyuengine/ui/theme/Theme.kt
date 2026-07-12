package com.xuzheng.tiyuengine.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0759BD),
    onPrimary = Color.White,
    secondary = Color(0xFF138A5B),
    background = Color(0xFFF4F6FA),
    surface = Color.White,
    onBackground = Color(0xFF123A70),
    onSurface = Color(0xFF123A70),
    error = Color(0xFFC2412D),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF0B1220),
    secondary = Color(0xFF34D399),
    background = Color(0xFF0B1220),
    surface = Color(0xFF151D2E),
    onBackground = Color(0xFFE5EEF9),
    onSurface = Color(0xFFE5EEF9),
    error = Color(0xFFF87171),
)

@Composable
fun TikuziyongTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
