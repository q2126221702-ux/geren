package com.xuzheng.tiyuengine.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF075CF5),
    onPrimary = Color.White,
    secondary = Color(0xFF08774E),
    tertiary = Color(0xFFB6450B),
    background = Color(0xFFF7FAFE),
    surface = Color.White,
    surfaceVariant = Color(0xFFF0F6FC),
    onBackground = Color(0xFF071A3D),
    onSurface = Color(0xFF071A3D),
    error = Color(0xFFBA3B2E),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF0B1220),
    secondary = Color(0xFF34D399),
    tertiary = Color(0xFFFF9A55),
    background = Color(0xFF0B1220),
    surface = Color(0xFF151D2E),
    surfaceVariant = Color(0xFF111827),
    onBackground = Color(0xFFE5EEF9),
    onSurface = Color(0xFFE5EEF9),
    error = Color(0xFFF87171),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun TikuziyongTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content,
    )
}
