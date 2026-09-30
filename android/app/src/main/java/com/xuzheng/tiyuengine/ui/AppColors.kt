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
    val actionOrange: Color,
    val actionOrangeSoft: Color,
    val violet: Color,
    val violetSoft: Color,
    val greenSoft: Color,
    val heroSky: Color,
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
    pageBackground = Color(0xFFF7FAFE),
    surface = Color.White,
    surfaceMuted = Color(0xFFF0F6FC),
    heroBackground = Color(0xFF082E59),
    heroAccent = Color(0xFF16A879),
    primary = Color(0xFF075CF5),
    primarySoft = Color(0xFFE6F0FF),
    actionOrange = Color(0xFFB6450B),
    actionOrangeSoft = Color(0xFFFFF0E4),
    violet = Color(0xFF5B47D8),
    violetSoft = Color(0xFFF0EDFF),
    greenSoft = Color(0xFFE6F8F1),
    heroSky = Color(0xFFDFF0FE),
    onPrimary = Color.White,
    textPrimary = Color(0xFF071A3D),
    textSecondary = Color(0xFF53627A),
    textOnHero = Color.White,
    textOnHeroMuted = Color(0xFFC9D8E8),
    border = Color(0xFFDCE6F3),
    success = Color(0xFF08774E),
    warning = Color(0xFFB6450B),
    danger = Color(0xFFBA3B2E),
    infoBanner = Color(0xFFEFF6FF),
    infoBannerText = Color(0xFF44536B),
    offlineBanner = Color(0xFFFFF0E4),
    offlineBannerText = Color(0xFF7A4A18),
    selectedCard = Color(0xFFE6F0FF),
    progressTrack = Color(0xFFDCE9F8),
)

private val DarkAppColors = AppColors(
    pageBackground = Color(0xFF0B1220),
    surface = Color(0xFF151D2E),
    surfaceMuted = Color(0xFF111827),
    heroBackground = Color(0xFF102A43),
    heroAccent = Color(0xFF38BDF8),
    primary = Color(0xFF60A5FA),
    primarySoft = Color(0xFF1E293B),
    actionOrange = Color(0xFFFF9A55),
    actionOrangeSoft = Color(0xFF3A281D),
    violet = Color(0xFFB2A4FF),
    violetSoft = Color(0xFF2A2545),
    greenSoft = Color(0xFF173A30),
    heroSky = Color(0xFF17314B),
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
