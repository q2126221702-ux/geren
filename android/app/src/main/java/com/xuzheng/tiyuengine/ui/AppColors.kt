package com.xuzheng.tiyuengine.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppColors(
    val pageBackground: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val heroBackground: Color,
    val heroAccent: Color,
    val primary: Color,
    val primarySoft: Color,
    val onPrimary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textOnHero: Color,
    val textOnHeroMuted: Color,
    val border: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val infoBanner: Color,
    val infoBannerText: Color,
    val offlineBanner: Color,
    val offlineBannerText: Color,
    val selectedCard: Color,
    val progressTrack: Color,
)

private val LightAppColors = AppColors(
    pageBackground = Color(0xFFF4F6FA),
    surface = Color.White,
    surfaceMuted = Color(0xFFF6F8FB),
    heroBackground = Color(0xFF082E59),
    heroAccent = Color(0xFF15C5ED),
    primary = Color(0xFF0759BD),
    primarySoft = Color(0xFFE8EFF9),
    onPrimary = Color.White,
    textPrimary = Color(0xFF123A70),
    textSecondary = Color(0xFF64748B),
    textOnHero = Color.White,
    textOnHeroMuted = Color(0xFFC9D8E8),
    border = Color(0xFFE4E9F0),
    success = Color(0xFF138A5B),
    warning = Color(0xFFB54708),
    danger = Color(0xFFC2412D),
    infoBanner = Color(0xFFEFF6FF),
    infoBannerText = Color(0xFF526174),
    offlineBanner = Color(0xFFFFF4E5),
    offlineBannerText = Color(0xFF7A4A18),
    selectedCard = Color(0xFFE8EFF9),
    progressTrack = Color(0xFFE8EEF5),
)

private val DarkAppColors = AppColors(
    pageBackground = Color(0xFF0B1220),
    surface = Color(0xFF151D2E),
    surfaceMuted = Color(0xFF111827),
    heroBackground = Color(0xFF102A43),
    heroAccent = Color(0xFF38BDF8),
    primary = Color(0xFF60A5FA),
    primarySoft = Color(0xFF1E293B),
    onPrimary = Color(0xFF0B1220),
    textPrimary = Color(0xFFE5EEF9),
    textSecondary = Color(0xFF94A3B8),
    textOnHero = Color(0xFFF8FAFC),
    textOnHeroMuted = Color(0xFFCBD5E1),
    border = Color(0xFF243044),
    success = Color(0xFF34D399),
    warning = Color(0xFFFBBF24),
    danger = Color(0xFFF87171),
    infoBanner = Color(0xFF1E293B),
    infoBannerText = Color(0xFFCBD5E1),
    offlineBanner = Color(0xFF3A2A14),
    offlineBannerText = Color(0xFFFCD34D),
    selectedCard = Color(0xFF1E3A5F),
    progressTrack = Color(0xFF243044),
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

@Composable
fun appColors(): AppColors = if (isSystemInDarkTheme()) DarkAppColors else LightAppColors

@Composable
fun ProvideAppColors(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalAppColors provides appColors(), content = content)
}
